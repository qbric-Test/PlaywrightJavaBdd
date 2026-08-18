package context;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import factory.PlaywrightFactory;
import io.cucumber.java.Scenario;
import pages.OlxPage;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-scenario state shared between hooks, step definitions and page objects.
 *
 * <p>PicoContainer creates one instance per scenario and injects the same
 * instance into every class that declares it as a constructor parameter. That is
 * what lets {@code Hooks} build the browser while {@code OlxSteps} uses it,
 * without any static or thread-local state — which in turn is what makes
 * parallel execution safe.
 */
public class TestContext {

    private final PlaywrightFactory playwrightFactory = new PlaywrightFactory();

    private final Map<String, Object> scenarioData = new HashMap<>();

    private Page page;

    private Scenario scenario;

    private OlxPage olxPage;

    // ------------------------------------------------------------------
    // Playwright object graph
    // ------------------------------------------------------------------

    /**
     * @return the factory that owns this scenario's browser
     */
    public PlaywrightFactory getPlaywrightFactory() {
        return playwrightFactory;
    }

    /**
     * @return the Playwright instance
     */
    public Playwright getPlaywright() {
        return playwrightFactory.getPlaywright();
    }

    /**
     * @return the browser
     */
    public Browser getBrowser() {
        return playwrightFactory.getBrowser();
    }

    /**
     * @return the browser context
     */
    public BrowserContext getBrowserContext() {
        return playwrightFactory.getBrowserContext();
    }

    /**
     * @return the active page
     */
    public Page getPage() {
        return page;
    }

    /**
     * Stores the page and builds the page objects bound to it.
     *
     * @param page the page created by the factory
     */
    public void setPage(Page page) {
        this.page = page;
        this.olxPage = new OlxPage(page);
    }

    // ------------------------------------------------------------------
    // Scenario
    // ------------------------------------------------------------------

    /**
     * @return the running scenario
     */
    public Scenario getScenario() {
        return scenario;
    }

    /**
     * @param scenario the running scenario, supplied by the Before hook
     */
    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    // ------------------------------------------------------------------
    // Page objects
    // ------------------------------------------------------------------

    /**
     * @return the OLX page object for this scenario
     * @throws IllegalStateException when the browser has not been started yet
     */
    public OlxPage getOlxPage() {
        if (olxPage == null) {
            throw new IllegalStateException(
                    "Page objects are not available: the Before hook has not created a page yet.");
        }
        return olxPage;
    }

    // ------------------------------------------------------------------
    // Free-form data sharing between steps
    // ------------------------------------------------------------------

    /**
     * Stores a value for a later step in the same scenario.
     *
     * @param key   lookup key
     * @param value value to keep
     */
    public void set(String key, Object value) {
        scenarioData.put(key, value);
    }

    /**
     * Reads a value stored earlier in the same scenario.
     *
     * @param key  lookup key
     * @param type expected type
     * @param <T>  expected type
     * @return the stored value, or {@code null} when absent
     */
    public <T> T get(String key, Class<T> type) {
        return type.cast(scenarioData.get(key));
    }

    /**
     * Reads a value a previous step is required to have set.
     *
     * @param key  lookup key
     * @param type expected type
     * @param <T>  expected type
     * @return the stored value
     * @throws IllegalStateException when the key is absent
     */
    public <T> T require(String key, Class<T> type) {
        Object value = scenarioData.get(key);
        if (value == null) {
            throw new IllegalStateException("Scenario context is missing the required key: " + key);
        }
        return type.cast(value);
    }
}
