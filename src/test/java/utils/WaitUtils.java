package utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Explicit waiting helpers layered on top of Playwright's auto-waiting.
 *
 * <p>Playwright already waits for actionability before every action, so these
 * helpers exist only for what the built-in waits do not cover: choosing between
 * several candidate locators, waiting for a collection to populate, and polling
 * an arbitrary condition.
 */
public class WaitUtils {

    private static final int POLL_INTERVAL_MS = 250;

    private final Page page;

    private final LoggerUtility log = LoggerUtility.forClass(WaitUtils.class);

    /**
     * @param page page these helpers operate on
     */
    public WaitUtils(Page page) {
        this.page = page;
    }

    /**
     * Waits until the locator is visible.
     *
     * @param locator target
     * @param timeout milliseconds to wait
     * @return the same locator, for chaining
     */
    public Locator waitForVisible(Locator locator, double timeout) {
        locator.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(timeout));
        return locator.first();
    }

    /**
     * Waits until the locator is hidden or detached.
     *
     * @param locator target
     * @param timeout milliseconds to wait
     */
    public void waitForHidden(Locator locator, double timeout) {
        locator.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(timeout));
    }

    /**
     * Returns the first candidate locator that becomes visible.
     *
     * <p>This is the backbone of the locator fallback strategy. Production sites
     * ship several markup variants, so a page object supplies an ordered list of
     * candidates and this method picks whichever the current variant renders.
     *
     * @param candidates ordered candidates, most preferred first
     * @param timeout    milliseconds to wait
     * @return the first visible candidate, narrowed to its first match
     * @throws IllegalArgumentException when no candidates are supplied
     * @throws AssertionError           when none becomes visible in time
     */
    public Locator firstVisible(List<Locator> candidates, double timeout) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("firstVisible() requires at least one candidate locator");
        }

        long deadline = System.currentTimeMillis() + (long) timeout;

        while (System.currentTimeMillis() < deadline) {
            for (Locator candidate : candidates) {
                try {
                    Locator first = candidate.first();
                    if (first.isVisible()) {
                        return first;
                    }
                } catch (RuntimeException ignored) {
                    // A candidate may be invalid for the current variant; try the next one.
                }
            }
            page.waitForTimeout(POLL_INTERVAL_MS);
        }

        throw new AssertionError("None of the " + candidates.size()
                + " candidate locators became visible within " + (long) timeout + "ms");
    }

    /**
     * Reports whether any candidate becomes visible, without throwing.
     *
     * @param candidates ordered candidates
     * @param timeout    milliseconds to wait
     * @return {@code true} when one of them is visible
     */
    public boolean isAnyVisible(List<Locator> candidates, double timeout) {
        try {
            firstVisible(candidates, timeout);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }

    /**
     * Resolves a candidate list to the one representing a collection.
     *
     * <p>A candidate that renders at least one <em>visible</em> node wins over one
     * that merely matches nodes. Card grids commonly attach a zero-size wrapper
     * element before the card itself renders, so a plain count check can lock onto
     * the wrapper and then fail a downstream visibility assertion. Only when
     * nothing becomes visible does this fall back to the first candidate that
     * matches anything.
     *
     * @param candidates ordered candidates
     * @param timeout    milliseconds to wait
     * @return the resolved collection locator
     */
    public Locator resolveCollection(List<Locator> candidates, double timeout) {
        long deadline = System.currentTimeMillis() + (long) timeout;

        while (System.currentTimeMillis() < deadline) {
            for (Locator candidate : candidates) {
                try {
                    if (candidate.first().isVisible()) {
                        return candidate;
                    }
                } catch (RuntimeException ignored) {
                    // Try the next candidate.
                }
            }
            page.waitForTimeout(POLL_INTERVAL_MS);
        }

        log.debug("No candidate produced a visible node; falling back to a match-count resolution.");
        for (Locator candidate : candidates) {
            if (candidate.count() > 0) {
                return candidate;
            }
        }

        throw new AssertionError("None of the " + candidates.size()
                + " candidate locators matched any node within " + (long) timeout + "ms");
    }

    /**
     * Waits until the locator matches at least the expected number of nodes.
     *
     * @param locator  target collection
     * @param expected minimum count
     * @param timeout  milliseconds to wait
     * @return the observed count
     */
    public int waitForCountAtLeast(Locator locator, int expected, double timeout) {
        return until(() -> {
            int count = locator.count();
            return count >= expected ? count : null;
        }, timeout, "Expected at least " + expected + " element(s)");
    }

    /**
     * Waits for the DOM to be parsed. Preferred over network idle on commercial
     * sites, which keep long-lived analytics connections open.
     */
    public void waitForDomContentLoaded() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    /**
     * Best-effort wait for network quiescence. Never fails the test, because
     * third-party trackers can keep a page from ever reaching network idle.
     *
     * @param timeout milliseconds to wait
     */
    public void waitForNetworkIdle(double timeout) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(timeout));
        } catch (RuntimeException e) {
            log.debug("Network idle not reached within {}ms; continuing.", (long) timeout);
        }
    }

    /**
     * Waits for the URL to match a glob pattern.
     *
     * @param urlPattern glob pattern accepted by Playwright
     * @param timeout    milliseconds to wait
     */
    public void waitForUrl(String urlPattern, double timeout) {
        page.waitForURL(urlPattern, new Page.WaitForURLOptions().setTimeout(timeout));
    }

    /**
     * Waits for the URL to satisfy a predicate.
     *
     * <p>Preferred over the glob overload whenever the interesting part of the URL
     * is a suffix or a query parameter. Playwright globs must match the whole URL
     * including the scheme, so a pattern such as {@code **sorting=**} silently
     * never matches and burns the full timeout before failing.
     *
     * @param predicate test applied to the current URL
     * @param timeout   milliseconds to wait
     */
    public void waitForUrl(Predicate<String> predicate, double timeout) {
        page.waitForURL(predicate, new Page.WaitForURLOptions().setTimeout(timeout));
    }

    /**
     * Polls a supplier until it returns a non-null value.
     *
     * @param condition supplier returning {@code null} while unsatisfied
     * @param timeout   milliseconds to wait
     * @param message   text used in the failure message
     * @param <T>       result type
     * @return the first non-null value produced
     */
    public <T> T until(Supplier<T> condition, double timeout, String message) {
        long deadline = System.currentTimeMillis() + (long) timeout;
        RuntimeException lastError = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                T result = condition.get();
                if (result != null) {
                    return result;
                }
            } catch (RuntimeException e) {
                lastError = e;
            }
            page.waitForTimeout(POLL_INTERVAL_MS);
        }

        String failure = message + " within " + (long) timeout + "ms";
        if (lastError != null) {
            log.debug("{} (last error: {})", failure, lastError.getMessage());
        }
        throw new AssertionError(failure);
    }

    /**
     * Fixed pause. Use sparingly, and only where no event-based wait exists.
     *
     * @param milliseconds how long to pause
     */
    public void pause(double milliseconds) {
        page.waitForTimeout(milliseconds);
    }
}
