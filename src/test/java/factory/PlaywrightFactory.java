package factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.ViewportSize;
import utils.ConfigReader;
import utils.LoggerUtility;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Creates and disposes the Playwright object graph.
 *
 * <p>Each instance owns exactly one {@link Playwright} / {@link Browser} /
 * {@link BrowserContext} / {@link Page} chain, and Hooks creates one instance
 * per scenario. That keeps parallel workers fully independent: no shared state,
 * and a crashed scenario cannot poison its neighbours.
 */
public class PlaywrightFactory {

    private static final LoggerUtility LOG = LoggerUtility.forClass(PlaywrightFactory.class);

    private Playwright playwright;

    private Browser browser;

    private BrowserContext browserContext;

    private Page page;

    /**
     * Initialises Playwright, launches the configured browser, and creates a
     * context and page.
     *
     * <p>Tracing is started when enabled. Video recording is switched on at
     * context creation because Playwright cannot begin recording later; unwanted
     * recordings are discarded during teardown.
     *
     * @return the created page
     */
    public Page initBrowser() {
        playwright = Playwright.create();

        String browserName = ConfigReader.browser();
        boolean headless = ConfigReader.headless();

        browser = launchBrowser(browserName, headless);
        LOG.browserLaunched(browserName, headless);

        browserContext = browser.newContext(buildContextOptions());
        browserContext.setDefaultTimeout(ConfigReader.timeout());
        browserContext.setDefaultNavigationTimeout(ConfigReader.navigationTimeout());

        if (ConfigReader.traceMode().isEnabled()) {
            browserContext.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true)
                    .setSources(true));
            LOG.debug("Tracing started");
        }

        page = browserContext.newPage();
        return page;
    }

    /**
     * Launches the browser engine named in configuration.
     *
     * @param browserName chromium, firefox or webkit
     * @param headless    whether to run without a visible window
     * @return the launched browser
     * @throws IllegalArgumentException for an unsupported engine name
     */
    private Browser launchBrowser(String browserName, boolean headless) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(ConfigReader.slowMo());

        return switch (browserName.toLowerCase(Locale.ROOT)) {
            case "chromium", "chrome" -> playwright.chromium().launch(
                    // Note: --start-maximized is deliberately absent. Combined with a
                    // fixed viewport it creates a window/screen metric mismatch that
                    // bot-detection flags, and Playwright overrides it anyway.
                    options.setArgs(List.of("--disable-blink-features=AutomationControlled")));
            case "firefox" -> playwright.firefox().launch(options);
            case "webkit", "safari" -> playwright.webkit().launch(options);
            default -> throw new IllegalArgumentException(
                    "Unsupported browser: '" + browserName + "'. Use chromium, firefox or webkit.");
        };
    }

    /**
     * Builds the context options, including video recording when enabled.
     *
     * @return the configured options
     */
    private Browser.NewContextOptions buildContextOptions() {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setViewportSize(new ViewportSize(
                        ConfigReader.viewportWidth(), ConfigReader.viewportHeight()))
                .setLocale(ConfigReader.locale())
                .setTimezoneId(ConfigReader.timezoneId())
                .setIgnoreHTTPSErrors(ConfigReader.ignoreHttpsErrors())
                .setAcceptDownloads(true);

        if (ConfigReader.videoMode().isEnabled()) {
            Path videoDir = Paths.get(ConfigReader.artifactsDir(), "videos");
            options.setRecordVideoDir(videoDir)
                    .setRecordVideoSize(ConfigReader.viewportWidth(), ConfigReader.viewportHeight());
        }

        return options;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public Playwright getPlaywright() {
        return playwright;
    }

    public Browser getBrowser() {
        return browser;
    }

    public BrowserContext getBrowserContext() {
        return browserContext;
    }

    public Page getPage() {
        return page;
    }

    // ------------------------------------------------------------------
    // Teardown
    // ------------------------------------------------------------------

    /**
     * Stops tracing, writing the trace file when it should be kept.
     *
     * @param tracePath where to write the trace, or {@code null} to discard it
     */
    public void stopTracing(Path tracePath) {
        if (browserContext == null || !ConfigReader.traceMode().isEnabled()) {
            return;
        }
        try {
            if (tracePath == null) {
                browserContext.tracing().stop();
            } else {
                browserContext.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
                LOG.artifact("Trace", tracePath.toAbsolutePath().toString());
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not stop tracing: {}", e.getMessage());
        }
    }

    /**
     * Closes the page, context, browser and Playwright, in that order.
     *
     * <p>Every step is guarded so one failure cannot leave a browser process
     * orphaned.
     */
    public void closeResources() {
        closeQuietly(() -> {
            if (page != null && !page.isClosed()) {
                page.close();
            }
        }, "page");

        closeQuietly(() -> {
            if (browserContext != null) {
                browserContext.close();
            }
        }, "browser context");

        closeQuietly(() -> {
            if (browser != null) {
                browser.close();
            }
        }, "browser");

        closeQuietly(() -> {
            if (playwright != null) {
                playwright.close();
            }
        }, "Playwright");

        LOG.cleanup("Browser resources released");
    }

    private void closeQuietly(Runnable action, String what) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOG.debug("Ignored failure while closing {}: {}", what, e.getMessage());
        }
    }
}
