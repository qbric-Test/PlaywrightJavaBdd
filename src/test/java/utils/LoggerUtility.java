package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin SLF4J wrapper that gives the framework a consistent vocabulary for the
 * events worth logging: browser lifecycle, navigation, user actions, validation
 * and cleanup.
 *
 * <p>Using named methods rather than raw {@code log.info(...)} calls keeps the
 * execution log scannable — every line is prefixed with the kind of event it
 * represents, so a failure can be traced without reading the test code.
 */
public final class LoggerUtility {

    private final Logger logger;

    private LoggerUtility(Class<?> type) {
        this.logger = LoggerFactory.getLogger(type);
    }

    /**
     * Creates a logger scoped to the supplied class.
     *
     * @param type class the messages belong to
     * @return a new logger instance
     */
    public static LoggerUtility forClass(Class<?> type) {
        return new LoggerUtility(type);
    }

    // ------------------------------------------------------------------
    // Lifecycle events
    // ------------------------------------------------------------------

    /**
     * Logs a browser launch.
     *
     * @param browser  engine name
     * @param headless whether the run is headless
     */
    public void browserLaunched(String browser, boolean headless) {
        logger.info("BROWSER   | Launched {} (headless={})", browser, headless);
    }

    /**
     * Logs a navigation.
     *
     * @param url destination
     */
    public void navigation(String url) {
        logger.info("NAVIGATE  | {}", url);
    }

    /**
     * Logs a user action such as a click or a fill.
     *
     * @param action      what was done
     * @param description the element it was done to
     */
    public void action(String action, String description) {
        logger.info("ACTION    | {} -> {}", action, description);
    }

    /**
     * Logs a validation that passed.
     *
     * @param message what was verified
     */
    public void validation(String message) {
        logger.info("VERIFY    | {}", message);
    }

    /**
     * Logs the start of a scenario.
     *
     * @param scenarioName scenario name
     */
    public void scenarioStart(String scenarioName) {
        logger.info("SCENARIO  | START  | {}", scenarioName);
    }

    /**
     * Logs the end of a scenario.
     *
     * @param scenarioName scenario name
     * @param status       final status
     */
    public void scenarioEnd(String scenarioName, String status) {
        logger.info("SCENARIO  | END    | {} | status={}", scenarioName, status);
    }

    /**
     * Logs a cleanup operation.
     *
     * @param message what was cleaned up
     */
    public void cleanup(String message) {
        logger.info("CLEANUP   | {}", message);
    }

    /**
     * Logs where an artifact was written.
     *
     * @param type artifact kind, for example "Screenshot"
     * @param path absolute path on disk
     */
    public void artifact(String type, String path) {
        logger.info("ARTIFACT  | {} -> {}", type, path);
    }

    // ------------------------------------------------------------------
    // Generic levels
    // ------------------------------------------------------------------

    public void info(String message, Object... args) {
        logger.info(message, args);
    }

    public void debug(String message, Object... args) {
        logger.debug(message, args);
    }

    public void warn(String message, Object... args) {
        logger.warn(message, args);
    }

    /**
     * Logs a failure with its stack trace.
     *
     * @param message context for the failure
     * @param error   the throwable
     */
    public void failure(String message, Throwable error) {
        logger.error("FAILURE   | {}", message, error);
    }

    public void error(String message, Object... args) {
        logger.error("FAILURE   | " + message, args);
    }
}
