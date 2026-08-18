package pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import locators.OlxLocators;
import utils.ConfigReader;

/**
 * Page object for OLX Pakistan: home page, category listing pages and the sort
 * control they share.
 *
 * <p>Methods here express <em>business actions</em> and return the observed
 * state. Assertions live in the step definitions, so a failure is reported
 * against the Gherkin step that caused it.
 */
public class OlxPage extends BasePage {

    private final OlxLocators elements;

    /**
     * @param page page under test
     */
    public OlxPage(Page page) {
        super(page);
        this.elements = new OlxLocators(page);
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /**
     * Opens the OLX Pakistan home page and clears any first-visit overlay.
     */
    public void openHomePage() {
        navigateTo(ConfigReader.baseUrl());
        dismissInterstitials();
    }

    /**
     * Clicks the Mobiles tile in the top categories strip and waits for the
     * category page to settle.
     */
    public void clickMobilesCategory() {
        Locator tile = waitForElementVisible(elements.mobilesCategory(), ConfigReader.timeout());
        tile.scrollIntoViewIfNeeded();

        log.action("Click", "Mobiles category tile");
        tile.click(new Locator.ClickOptions().setTimeout(ConfigReader.timeout()));

        // OLX category URLs carry a "_c<id>" suffix, e.g. /mobiles_c1411.
        try {
            wait.waitForUrl(url -> url.matches(".*_c\\d+.*"), ConfigReader.navigationTimeout());
        } catch (RuntimeException e) {
            log.debug("Category URL pattern not observed; verifying page content directly.");
        }

        waitForPageLoad();
        dismissInterstitials();
    }

    // ------------------------------------------------------------------
    // State the step definitions assert on
    // ------------------------------------------------------------------

    /**
     * Reports whether the Mobiles category page rendered.
     *
     * <p>Two signals are required: the listings grid is populated, and the header
     * search control is present, which together rule out an error or empty shell.
     *
     * @return {@code true} when the page loaded successfully
     */
    public boolean verifyMobilesPageLoaded() {
        boolean listingsPresent = getListingCount() > 0;
        boolean searchPresent = isVisible(elements.searchTextbox(), ConfigReader.assertionTimeout());

        log.validation("Mobiles page loaded (listings=" + listingsPresent
                + ", search=" + searchPresent + ")");
        return listingsPresent && searchPresent;
    }

    /**
     * Reports whether the document title contains the supplied fragment.
     *
     * @param titleFragment expected substring
     * @return {@code true} when the title contains it
     */
    public boolean verifyPageTitleContains(String titleFragment) {
        String actual = getPageTitle();
        log.validation("Page title is '" + actual + "'");
        return actual.toLowerCase().contains(titleFragment.toLowerCase());
    }

    /**
     * Reports whether the country selector holds the expected country.
     *
     * <p>OLX renders this control as a text input rather than a native select, so
     * the "selected" country is its value.
     *
     * @param expectedCountry expected country, for example "Pakistan"
     * @return {@code true} when the value matches
     */
    public boolean verifyCountrySelected(String expectedCountry) {
        String actual = getSelectedCountry();
        log.validation("Country dropdown holds '" + actual + "'");
        return expectedCountry.equalsIgnoreCase(actual);
    }

    /**
     * @return the country currently held by the location selector
     */
    public String getSelectedCountry() {
        return getInputValue(elements.selectedCountry());
    }

    /**
     * Reports whether the search textbox placeholder matches exactly.
     *
     * @param expectedPlaceholder expected placeholder text
     * @return {@code true} when it matches
     */
    public boolean verifySearchPlaceholder(String expectedPlaceholder) {
        String actual = getSearchPlaceholder();
        log.validation("Search placeholder is '" + actual + "'");
        return expectedPlaceholder.equals(actual);
    }

    /**
     * @return the placeholder text of the search textbox
     */
    public String getSearchPlaceholder() {
        return getAttribute(elements.searchTextbox(), "placeholder");
    }

    // ------------------------------------------------------------------
    // Sorting
    // ------------------------------------------------------------------

    /**
     * Opens the Sort By dropdown and selects an option.
     *
     * @param option visible option label, for example "Newly listed"
     */
    public void selectSortByOption(String option) {
        selectDropdownOption(
                elements.sortByDropdown(),
                elements.sortOptionsList(),
                elements.sortOption(option),
                option,
                "Sort By dropdown");

        // OLX reflects the choice in the query string, e.g. ?sorting=desc-creation.
        try {
            wait.waitForUrl(url -> url.contains("sorting="), ConfigReader.navigationTimeout());
        } catch (RuntimeException e) {
            log.debug("Sorting query parameter not observed; verifying the control directly.");
        }
    }

    /**
     * @return the label shown on the Sort By control, for example "Sort by: Newly listed"
     */
    public String getSelectedSortOption() {
        return getText(elements.sortByDropdown());
    }

    /**
     * Reports whether the Sort By control reflects the chosen option.
     *
     * @param expectedOption expected option label
     * @return {@code true} when the control shows it
     */
    public boolean verifySortOptionSelected(String expectedOption) {
        String actual = getSelectedSortOption();
        log.validation("Sort control shows '" + actual.replaceAll("\\s+", " ") + "'");
        return actual.toLowerCase().contains(expectedOption.toLowerCase());
    }

    // ------------------------------------------------------------------
    // Listings
    // ------------------------------------------------------------------

    /**
     * Waits for the listing grid to finish refreshing after a sort change.
     *
     * <p>The spinner is transient and easy to miss between polls, so the
     * definitive signal is rendered advert cards rather than the disappearance of
     * a loading indicator.
     */
    public void waitForListingsRefresh() {
        log.info("Waiting for the listings to refresh");

        wait.waitForDomContentLoaded();

        Locator listings = wait.resolveCollection(elements.listingsContainer(),
                ConfigReader.timeout());
        wait.waitForCountAtLeast(listings, 1, ConfigReader.timeout());

        wait.waitForNetworkIdle(8_000);
        log.info("Listings refreshed: {} card(s) rendered", listings.count());
    }

    /**
     * Reports whether the listing grid is populated.
     *
     * @return {@code true} when at least one advert card is displayed
     */
    public boolean verifyListingsDisplayed() {
        int count = getListingCount();
        log.validation(count + " mobile listing(s) displayed");
        return count > 0;
    }

    /**
     * @return the number of advert cards currently rendered
     */
    public int getListingCount() {
        try {
            return wait.resolveCollection(elements.listingsContainer(), ConfigReader.timeout())
                    .count();
        } catch (AssertionError | RuntimeException e) {
            log.debug("No listings resolved: {}", e.getMessage());
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Closes cookie banners and promo overlays when they appear.
     *
     * <p>Deliberately non-fatal: none of these overlays is guaranteed to render,
     * so a missing one must never fail a scenario.
     */
    public void dismissInterstitials() {
        for (Locator candidate : concat(elements.cookieAcceptButton(), elements.modalCloseButton())) {
            try {
                Locator button = candidate.first();
                if (button.isVisible()) {
                    button.click(new Locator.ClickOptions().setTimeout(5_000));
                    log.debug("Dismissed an overlay");
                    return;
                }
            } catch (RuntimeException ignored) {
                // The overlay is optional; try the next candidate.
            }
        }
    }

    private static java.util.List<Locator> concat(java.util.List<Locator> first,
                                                  java.util.List<Locator> second) {
        java.util.List<Locator> combined = new java.util.ArrayList<>(first);
        combined.addAll(second);
        return combined;
    }
}
