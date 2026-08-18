# PlaywrightJavaBdd

Playwright Java + Cucumber BDD + JUnit 5 automation framework, built on the Page
Object Model with a factory-created browser per scenario.

## Stack

| Concern            | Tool                                     |
| ------------------ | ---------------------------------------- |
| Language           | Java 17                                  |
| Build              | Maven                                    |
| Browser automation | Playwright Java                          |
| BDD runner         | Cucumber 7 on the JUnit 5 Platform       |
| Assertions         | JUnit 5 (`org.junit.jupiter.api.Assertions`) |
| DI / test context  | Cucumber PicoContainer                   |
| Logging            | SLF4J + Logback                          |

## Setup

```bash
mvn clean install -DskipTests
```

```bash
mvn exec:java@install-browsers
```

The second command downloads the Chromium, Firefox and WebKit binaries.
Playwright also downloads them on first use, so it is optional — but running it
up front keeps the first test run from timing out on a large download.

## Run

```bash
mvn test
```

| Command                                                | What it does                     |
| ------------------------------------------------------ | -------------------------------- |
| `mvn test`                                              | Full suite, Chromium, headless   |
| `mvn test -Dbrowser=firefox`                            | Firefox                          |
| `mvn test -Dbrowser=webkit`                             | WebKit                           |
| `mvn test -Dheadless=false`                             | Visible browser                  |
| `mvn test -Dcucumber.filter.tags="@smoke"`              | Tagged scenarios only            |
| `mvn test -Dcucumber.execution.parallel.enabled=true`   | Parallel scenarios               |
| `mvn test -DslowMo=250`                                 | Slow the run down for debugging  |

Every key in `config.properties` can be overridden with `-D`, because
`ConfigReader` checks system properties before the file.

## Layout

```
PlaywrightJavaBdd
├── pom.xml
├── src/test/java
│   ├── factory/PlaywrightFactory.java     # browser/context/page lifecycle
│   ├── pages/BasePage.java                # reusable Playwright primitives
│   ├── pages/OlxPage.java                 # business actions
│   ├── locators/OlxLocators.java          # candidate-list locators
│   ├── context/TestContext.java           # per-scenario shared state
│   ├── hooks/Hooks.java                   # setup, artifacts, teardown
│   ├── stepdefinitions/OlxSteps.java      # Gherkin bindings + assertions
│   ├── runners/TestRunner.java            # JUnit 5 Platform suite
│   └── utils/                             # ConfigReader, LoggerUtility, WaitUtils
├── src/test/resources
│   ├── features/olx.feature
│   ├── config/config.properties
│   ├── junit-platform.properties          # parallelism, strict mode
│   └── logback.xml
├── reports                                # HTML / JSON / JUnit XML
└── test-results                           # screenshots, traces, videos, logs
```

## Reports and failure artifacts

- HTML report: `reports/cucumber-report.html`
- JSON / JUnit XML: `reports/cucumber-report.json`, `reports/cucumber-junit.xml`
- Screenshots: `test-results/screenshots/` (also attached to the HTML report)
- Traces: `test-results/traces/` — open with
  `npx playwright show-trace test-results/traces/<file>.zip`
- Videos: `test-results/videos/`
- Execution log: `test-results/logs/execution.log`

Screenshots, traces and videos each honour an independent capture policy —
`ON`, `OFF` or `RETAIN_ON_FAILURE` — set in `config.properties`. Under the
default `RETAIN_ON_FAILURE`, passing scenarios leave nothing behind and only
failures keep evidence.

## Design notes

**Locator fallback.** Every element in `OlxLocators` is an ordered `List<Locator>`
of candidates, most semantic first (`getByRole`, `getByPlaceholder`,
`getByLabel`, image `alt` text), falling back to structural CSS.
`WaitUtils.firstVisible()` picks whichever candidate the current markup variant
renders. This matters on OLX because its CSS class names are build hashes
(`_520955ba`, `b5720141`) that change on every deploy and are therefore never
used as selectors. No XPath anywhere.

**Collection resolution.** `WaitUtils.resolveCollection()` prefers a candidate
with a genuinely *visible* node over one that merely matches nodes. OLX attaches
a zero-size wrapper `<a>` around each advert before the card renders, so a plain
count check locks onto the wrapper and then fails the visibility assertion that
follows.

**Isolation.** `PlaywrightFactory` builds one Playwright / Browser /
BrowserContext / Page chain per scenario, held in a `TestContext` that
PicoContainer scopes per scenario. There is no static or thread-local state, so
parallel execution is safe.

**Assertions live in step definitions.** Page objects perform actions and report
what they observed; `OlxSteps` decides whether that constitutes a pass. A
failure therefore names the Gherkin expectation, not a selector.

## Two things the site dictates

The page under test is a live production site, and two of its details shape the
code:

1. **The country control is not a `<select>`.** OLX renders it as a text input
   whose *value* is the selected location, so "Pakistan is selected" is asserted
   with an input-value check. On the home page that value is empty; it only reads
   `Pakistan` once you are on a category page.
2. **The sort control is not a `<select>` either.** It is a `<button>` plus a
   `role="listbox"`, which is why `BasePage` carries a custom-dropdown overload
   of `selectDropdownOption()` alongside the native-`<select>` one.

## Adding a page

1. Add locators to `src/test/java/locators/<Name>Locators.java` as candidate lists.
2. Create `src/test/java/pages/<Name>Page.java` extending `BasePage`.
3. Expose it from `TestContext.setPage(...)`.
4. Write the feature, then step definitions that delegate to the page object and
   assert on what it returns.
