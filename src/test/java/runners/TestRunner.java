package runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

/**
 * JUnit 5 Platform suite that runs the Cucumber features.
 *
 * <p>Executed by Surefire, which is configured in {@code pom.xml} to include only
 * this class. Parallel execution lives in
 * {@code src/test/resources/junit-platform.properties} so it can be toggled
 * without recompiling.
 *
 * <p>Tags come from the {@code cucumber.filter.tags} system property, which keeps
 * the runner free of hard-coded tag expressions:
 *
 * <pre>
 *   mvn test -Dcucumber.filter.tags="@smoke"
 *   mvn test -Dcucumber.filter.tags="@olx and not @ignore"
 * </pre>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "stepdefinitions,hooks")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty,"
                + "html:reports/cucumber-report.html,"
                + "json:reports/cucumber-report.json,"
                + "junit:reports/cucumber-junit.xml,"
                + "timeline:reports/timeline,"
                + "summary")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class TestRunner {
    // The annotations above are the entire configuration; no body is required.
}
