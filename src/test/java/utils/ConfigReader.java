package utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/**
 * Loads and exposes framework configuration.
 *
 * <p>Values are read from {@code src/test/resources/config/config.properties}. A
 * matching JVM system property always wins, so any value can be overridden on
 * the command line without editing the file:
 *
 * <pre>
 *   mvn test -Dbrowser=firefox -Dheadless=false
 * </pre>
 *
 * <p>Implemented as an eagerly initialised singleton: the properties file is
 * parsed once per JVM and shared by every thread in a parallel run.
 */
public final class ConfigReader {

    private static final String CONFIG_FILE = "config/config.properties";

    private static final Properties PROPERTIES = load();

    private ConfigReader() {
        // Utility class.
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input =
                     ConfigReader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {

            if (input == null) {
                throw new IllegalStateException(
                        "Configuration file not found on the classpath: " + CONFIG_FILE);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read configuration file: " + CONFIG_FILE, e);
        }
        return properties;
    }

    /**
     * Returns a required property. System properties take precedence.
     *
     * @param key property name
     * @return the trimmed value
     * @throws IllegalStateException when the key is absent or blank
     */
    public static String get(String key) {
        String value = System.getProperty(key, PROPERTIES.getProperty(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration value for key: " + key);
        }
        return value.trim();
    }

    /**
     * Returns a property, or the supplied default when it is absent.
     *
     * @param key          property name
     * @param defaultValue value to use when the key is absent
     * @return the resolved value
     */
    public static String get(String key, String defaultValue) {
        String value = System.getProperty(key, PROPERTIES.getProperty(key));
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    /**
     * Returns a property parsed as an int.
     *
     * @param key          property name
     * @param defaultValue value to use when the key is absent
     * @return the resolved value
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Configuration value for '" + key + "' is not a number: " + value, e);
        }
    }

    /**
     * Returns a property parsed as a boolean.
     *
     * @param key          property name
     * @param defaultValue value to use when the key is absent
     * @return the resolved value
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    // ------------------------------------------------------------------
    // Typed accessors for the values used across the framework
    // ------------------------------------------------------------------

    public static String baseUrl() {
        String url = get("baseUrl");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String environment() {
        return get("environment", "qa");
    }

    public static String browser() {
        return get("browser", "chromium").toLowerCase(Locale.ROOT);
    }

    public static boolean headless() {
        return getBoolean("headless", true);
    }

    public static double slowMo() {
        return getInt("slowMo", 0);
    }

    public static int viewportWidth() {
        return getInt("viewportWidth", 1920);
    }

    public static int viewportHeight() {
        return getInt("viewportHeight", 1080);
    }

    public static String locale() {
        return get("locale", "en-PK");
    }

    public static String timezoneId() {
        return get("timezoneId", "Asia/Karachi");
    }

    public static boolean ignoreHttpsErrors() {
        return getBoolean("ignoreHttpsErrors", true);
    }

    public static int timeout() {
        return getInt("timeout", 30_000);
    }

    public static int navigationTimeout() {
        return getInt("navigationTimeout", 60_000);
    }

    public static int assertionTimeout() {
        return getInt("assertionTimeout", 15_000);
    }

    public static ArtifactMode screenshotMode() {
        return ArtifactMode.from(get("screenshot", "RETAIN_ON_FAILURE"));
    }

    public static ArtifactMode traceMode() {
        return ArtifactMode.from(get("trace", "RETAIN_ON_FAILURE"));
    }

    public static ArtifactMode videoMode() {
        return ArtifactMode.from(get("video", "RETAIN_ON_FAILURE"));
    }

    public static String reportsDir() {
        return get("reportsDir", "reports");
    }

    public static String artifactsDir() {
        return get("artifactsDir", "test-results");
    }

    /**
     * Capture policy for screenshots, traces and videos.
     */
    public enum ArtifactMode {

        /** Always capture and keep. */
        ON,

        /** Never capture. */
        OFF,

        /** Capture always, but keep only when the scenario fails. */
        RETAIN_ON_FAILURE;

        /**
         * Parses a configured value, case insensitively.
         *
         * @param value raw configured value
         * @return the matching mode
         */
        public static ArtifactMode from(String value) {
            String normalised = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                return valueOf(normalised);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Invalid artifact mode: '" + value + "'. Expected ON, OFF or RETAIN_ON_FAILURE.", e);
            }
        }

        /**
         * @return {@code true} when the artifact should be captured at all
         */
        public boolean isEnabled() {
            return this != OFF;
        }

        /**
         * @param failed whether the scenario failed
         * @return {@code true} when the captured artifact should be kept
         */
        public boolean shouldKeep(boolean failed) {
            return this == ON || (this == RETAIN_ON_FAILURE && failed);
        }
    }
}
