package pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import utils.ConfigReader;
import utils.LoggerUtility;
import utils.WaitUtils;

import java.util.List;

/**
 * Base class for every page object.
 *
 * <p>Owns the Playwright {@link Page}, exposes the reusable interaction
 * primitives, and centralises logging so concrete page objects stay declarative
 * and read as business actions.
 *
 * <p>Each primitive is overloaded twice: once for a single {@link Locator} and
 * once for an ordered list of candidate locators. The list form applies the
 * fallback strategy, resolving to whichever candidate the current markup
 * variant renders.
 */
public abstract class BasePage {

    protected final Page page;

    protected final WaitUtils wait;

    protected final LoggerUtility log;

    /**
     * @param page page under test
     */
    protected BasePage(Page page) {
        this.page = page;
        this.wait = new WaitUtils(page);
        this.log = LoggerUtility.forClass(getClass());
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /**
     * Navigates to an absolute URL, or to a path relative to the configured base
     * URL.
     *
     * @param urlOrPath absolute URL or leading-slash path
     */
    public void navigateTo(String urlOrPath) {
        String target = urlOrPath.matches("(?i)^https?://.*")
                ? urlOrPath
                : ConfigReader.baseUrl() + (urlOrPath.startsWith("/") ? urlOrPath : "/" + urlOrPath);

        log.navigation(target);
        page.navigate(target, new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(ConfigReader.navigationTimeout()));
        waitForPageLoad();
    }

    /**
     * Reloads the current page.
     */
    public void reload() {
        page.reload(new Page.ReloadOptions().setTimeout(ConfigReader.navigationTimeout()));
        waitForPageLoad();
    }

    /**
     * Waits for the DOM to be parsed and the network to settle.
     */
    public void waitForPageLoad() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        wait.waitForNetworkIdle(8_000);
    }

    /**
     * @return the current page URL
     */
    public String getCurrentUrl() {
        return page.url();
    }

    /**
     * @return the document title
     */
    public String getPageTitle() {
        return page.title();
    }

    // ------------------------------------------------------------------
    // Interactions
    // ------------------------------------------------------------------

    /**
     * Clicks an element. Playwright auto-waits for actionability, so no explicit
     * wait is needed beforehand.
     *
     * @param locator     target
     * @param description human-readable name used in logs
     */
    public void click(Locator locator, String description) {
        log.action("Click", description);
        locator.first().click(new Locator.ClickOptions().setTimeout(ConfigReader.timeout()));
    }

    /**
     * Clicks the first candidate that becomes visible.
     *
     * @param candidates  ordered candidates
     * @param description human-readable name used in logs
     */
    public void click(List<Locator> candidates, String description) {
        log.action("Click", description);
        Locator resolved = wait.firstVisible(candidates, ConfigReader.timeout());
        try {
            resolved.click(new Locator.ClickOptions().setTimeout(ConfigReader.timeout()));
        } catch (RuntimeException e) {
            // A sticky header or an animation can intercept the pointer event.
            log.debug("Standard click on {} failed; retrying forced.", description);
            resolved.scrollIntoViewIfNeeded();
            resolved.click(new Locator.ClickOptions()
                    .setForce(true)
                    .setTimeout(ConfigReader.timeout()));
        }
    }

    /**
     * Clears a field and types a value.
     *
     * @param locator     target
     * @param value       text to enter
     * @param description human-readable name used in logs
     */
    public void fill(Locator locator, String value, String description) {
        log.action("Fill '" + value + "' into", description);
        Locator field = locator.first();
        field.click(new Locator.ClickOptions().setTimeout(ConfigReader.timeout()));
        field.fill(value);
    }

    /**
     * Clears the first visible candidate and types a value.
     *
     * <p>Retries once against a freshly resolved locator: search widgets commonly
     * swap the input element the moment it receives focus, which detaches the node
     * resolved milliseconds earlier.
     *
     * @param candidates  ordered candidates
     * @param value       text to enter
     * @param description human-readable name used in logs
     */
    public void fill(List<Locator> candidates, String value, String description) {
        log.action("Fill '" + value + "' into", description);
        try {
            Locator field = wait.firstVisible(candidates, ConfigReader.timeout());
            field.fill(value);
        } catch (RuntimeException | AssertionError e) {
            log.debug("First fill attempt on {} failed; re-resolving.", description);
            Locator field = wait.firstVisible(candidates, ConfigReader.timeout());
            field.fill(value);
        }
    }

    /**
     * Presses a key on an element.
     *
     * @param candidates  ordered candidates
     * @param key         key name, for example "Enter"
     * @param description human-readable name used in logs
     */
    public void press(List<Locator> candidates, String key, String description) {
        log.action("Press '" + key + "' on", description);
        wait.firstVisible(candidates, ConfigReader.timeout()).press(key);
    }

    /**
     * Selects an option from a native {@code <select>} element by its visible
     * label.
     *
     * @param locator     the select element
     * @param optionLabel visible option text
     * @param description human-readable name used in logs
     */
    public void selectDropdownOption(Locator locator, String optionLabel, String description) {
        log.action("Select '" + optionLabel + "' from", description);
        locator.first().selectOption(new SelectOption().setLabel(optionLabel));
    }

    /**
     * Selects an option from a custom dropdown: clicks the trigger, waits for the
     * option list, then clicks the option.
     *
     * <p>OLX builds its sort control from a {@code <button>} and a
     * {@code role="listbox"} list rather than a native {@code <select>}, so this
     * overload drives the widget the way a user does.
     *
     * @param trigger       ordered candidates for the control that opens the list
     * @param optionList    ordered candidates for the option list
     * @param option        ordered candidates for the option to pick
     * @param optionLabel   visible option text, used in logs
     * @param description   human-readable name used in logs
     */
    public void selectDropdownOption(List<Locator> trigger,
                                     List<Locator> optionList,
                                     List<Locator> option,
                                     String optionLabel,
                                     String description) {
        click(trigger, description);
        wait.firstVisible(optionList, ConfigReader.timeout());
        log.action("Select '" + optionLabel + "' from", description);
        wait.firstVisible(option, ConfigReader.timeout())
                .click(new Locator.ClickOptions().setTimeout(ConfigReader.timeout()));
    }

    // ------------------------------------------------------------------
    // State queries
    // ------------------------------------------------------------------

    /**
     * @param locator target
     * @return the trimmed inner text
     */
    public String getText(Locator locator) {
        return locator.first().innerText().trim();
    }

    /**
     * @param candidates ordered candidates
     * @return the trimmed inner text of the first visible candidate
     */
    public String getText(List<Locator> candidates) {
        return wait.firstVisible(candidates, ConfigReader.timeout()).innerText().trim();
    }

    /**
     * @param candidates ordered candidates
     * @param attribute  attribute name
     * @return the attribute value, or {@code null} when absent
     */
    public String getAttribute(List<Locator> candidates, String attribute) {
        return wait.firstVisible(candidates, ConfigReader.timeout()).getAttribute(attribute);
    }

    /**
     * @param candidates ordered candidates
     * @return the value of the first visible input
     */
    public String getInputValue(List<Locator> candidates) {
        return wait.firstVisible(candidates, ConfigReader.timeout()).inputValue().trim();
    }

    /**
     * Reports whether an element is visible. Never throws.
     *
     * @param locator target
     * @param timeout milliseconds to wait
     * @return {@code true} when visible within the timeout
     */
    public boolean isVisible(Locator locator, double timeout) {
        try {
            waitForElementVisible(locator, timeout);
            return true;
        } catch (RuntimeException | AssertionError e) {
            return false;
        }
    }

    /**
     * Reports whether any candidate is visible. Never throws.
     *
     * @param candidates ordered candidates
     * @param timeout    milliseconds to wait
     * @return {@code true} when one is visible within the timeout
     */
    public boolean isVisible(List<Locator> candidates, double timeout) {
        return wait.isAnyVisible(candidates, timeout);
    }

    /**
     * @param candidates ordered candidates
     * @return the number of nodes the resolved collection matches
     */
    public int getCount(List<Locator> candidates) {
        return wait.resolveCollection(candidates, ConfigReader.timeout()).count();
    }

    // ------------------------------------------------------------------
    // Waits
    // ------------------------------------------------------------------

    /**
     * Waits until an element is visible.
     *
     * @param locator target
     * @param timeout milliseconds to wait
     * @return the resolved locator
     */
    public Locator waitForElementVisible(Locator locator, double timeout) {
        return wait.waitForVisible(locator, timeout);
    }

    /**
     * Waits until one of the candidates is visible.
     *
     * @param candidates ordered candidates
     * @param timeout    milliseconds to wait
     * @return the first visible candidate
     */
    public Locator waitForElementVisible(List<Locator> candidates, double timeout) {
        return wait.firstVisible(candidates, timeout);
    }

    // ------------------------------------------------------------------
    // Artifacts
    // ------------------------------------------------------------------

    /**
     * Captures a screenshot of the current page.
     *
     * @param fullPage whether to capture the whole scrollable page
     * @return the PNG bytes
     */
    public byte[] captureScreenshot(boolean fullPage) {
        return page.screenshot(new Page.ScreenshotOptions().setFullPage(fullPage));
    }
}
