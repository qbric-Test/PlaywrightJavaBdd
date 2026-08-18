package hooks;

import com.microsoft.playwright.Page;
import context.TestContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import utils.ConfigReader;
import utils.LoggerUtility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Scenario lifecycle: browser setup before, artifact capture and teardown after.
 */
public class Hooks {

    private static final LoggerUtility LOG = LoggerUtility.forClass(Hooks.class);

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT);

    private final TestContext context;

    /**
     * @param context per-scenario context, injected by PicoContainer
     */
    public Hooks(TestContext context) {
        this.context = context;
    }

    /**
     * Initialises Playwright, launches the browser, creates the context and page,
     * and starts tracing.
     *
     * @param scenario the scenario about to run
     */
    @Before(order = 0)
    public void setUp(Scenario scenario) {
        LOG.scenarioStart(scenario.getName());

        createArtifactDirectories();

        context.setScenario(scenario);
        Page page = context.getPlaywrightFactory().initBrowser();
        context.setPage(page);
    }

    /**
     * Captures artifacts and releases the browser.
     *
     * <p>On failure a screenshot is written under {@code test-results/screenshots}
     * and attached to the Cucumber report, and the trace is written under
     * {@code test-results/traces}. The video is finalised only when the context
     * closes, so it is located first and moved afterwards.
     *
     * @param scenario the finished scenario
     */
    @After(order = 0)
    public void tearDown(Scenario scenario) {
        boolean failed = scenario.isFailed();
        String stem = slugify(scenario.getName()) + "_" + LocalDateTime.now().format(STAMP);

        captureScreenshot(scenario, failed, stem);
        captureTrace(failed, stem);

        // Resolve the recording path before the context closes; the handle is
        // unusable afterwards.
        Path recordedVideo = resolveVideoPath();

        context.getPlaywrightFactory().closeResources();

        handleVideo(recordedVideo, failed, stem);

        LOG.scenarioEnd(scenario.getName(), scenario.getStatus().name());
    }

    // ------------------------------------------------------------------
    // Artifact capture
    // ------------------------------------------------------------------

    private void captureScreenshot(Scenario scenario, boolean failed, String stem) {
        if (!ConfigReader.screenshotMode().shouldKeep(failed)) {
            return;
        }

        Page page = context.getPage();
        if (page == null || page.isClosed()) {
            return;
        }

        try {
            Path target = Paths.get(ConfigReader.artifactsDir(), "screenshots", stem + ".png");
            Files.createDirectories(target.getParent());

            byte[] image = page.screenshot(new Page.ScreenshotOptions()
                    .setPath(target)
                    .setFullPage(true)
                    .setTimeout(20_000));

            scenario.attach(image, "image/png", scenario.getName());
            scenario.attach("URL at capture: " + page.url(), "text/plain", "page-url");

            LOG.artifact("Screenshot", target.toAbsolutePath().toString());
        } catch (RuntimeException | IOException e) {
            // A full-page capture can time out on very long pages; fall back to the
            // viewport rather than losing the evidence entirely.
            LOG.warn("Full-page screenshot failed ({}); retrying viewport only.", e.getMessage());
            captureViewportScreenshot(scenario, stem);
        }
    }

    private void captureViewportScreenshot(Scenario scenario, String stem) {
        try {
            Path target = Paths.get(ConfigReader.artifactsDir(), "screenshots", stem + ".png");
            Files.createDirectories(target.getParent());

            byte[] image = context.getPage().screenshot(new Page.ScreenshotOptions()
                    .setPath(target)
                    .setFullPage(false)
                    .setTimeout(10_000));

            scenario.attach(image, "image/png", scenario.getName());
            LOG.artifact("Screenshot", target.toAbsolutePath().toString());
        } catch (RuntimeException | IOException e) {
            LOG.warn("Could not capture a screenshot: {}", e.getMessage());
        }
    }

    private void captureTrace(boolean failed, String stem) {
        if (!ConfigReader.traceMode().isEnabled()) {
            return;
        }

        if (!ConfigReader.traceMode().shouldKeep(failed)) {
            context.getPlaywrightFactory().stopTracing(null);
            return;
        }

        try {
            Path target = Paths.get(ConfigReader.artifactsDir(), "traces", stem + ".zip");
            Files.createDirectories(target.getParent());
            context.getPlaywrightFactory().stopTracing(target);
        } catch (IOException e) {
            LOG.warn("Could not create the traces directory: {}", e.getMessage());
        }
    }

    private Path resolveVideoPath() {
        if (!ConfigReader.videoMode().isEnabled()) {
            return null;
        }

        Page page = context.getPage();
        if (page == null || page.isClosed() || page.video() == null) {
            return null;
        }

        try {
            return page.video().path();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Renames a kept recording to match its screenshot and trace, or deletes it
     * when the scenario passed under RETAIN_ON_FAILURE.
     */
    private void handleVideo(Path recordedVideo, boolean failed, String stem) {
        if (recordedVideo == null || !Files.exists(recordedVideo)) {
            return;
        }

        try {
            if (!ConfigReader.videoMode().shouldKeep(failed)) {
                Files.deleteIfExists(recordedVideo);
                return;
            }

            Path target = Paths.get(ConfigReader.artifactsDir(), "videos", stem + ".webm");
            Files.createDirectories(target.getParent());
            Files.move(recordedVideo, target, StandardCopyOption.REPLACE_EXISTING);

            LOG.artifact("Video", target.toAbsolutePath().toString());
        } catch (IOException e) {
            LOG.warn("Could not finalise the recorded video: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void createArtifactDirectories() {
        try {
            Files.createDirectories(Paths.get(ConfigReader.reportsDir()));
            Files.createDirectories(Paths.get(ConfigReader.artifactsDir(), "screenshots"));
            Files.createDirectories(Paths.get(ConfigReader.artifactsDir(), "traces"));
            Files.createDirectories(Paths.get(ConfigReader.artifactsDir(), "videos"));
        } catch (IOException e) {
            LOG.warn("Could not create the output directories: {}", e.getMessage());
        }
    }

    /**
     * Converts a scenario name into a file-system-safe slug.
     */
    private static String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }
}
