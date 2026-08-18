package locators;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Central locator repository for OLX Pakistan.
 *
 * <p>Every element is exposed as an <em>ordered list</em> of candidate locators,
 * most semantic first, and the page object resolves whichever candidate the
 * current markup variant actually renders. This matters on OLX in particular:
 * its CSS class names are build hashes ({@code _520955ba}, {@code b5720141})
 * that change on every deploy, so they are never used as primary selectors.
 *
 * <p>Preference order is role and accessible name, then placeholder and label,
 * then stable attributes such as image {@code alt} text and {@code href}
 * patterns, and only then structural CSS. No XPath is used.
 */
public class OlxLocators {

    private final Page page;

    /**
     * @param page page these locators are bound to
     */
    public OlxLocators(Page page) {
        this.page = page;
    }

    // ------------------------------------------------------------------
    // Home page: top categories
    // ------------------------------------------------------------------

    /**
     * A tile in the top categories strip.
     *
     * <p>OLX renders each category name twice: once as a plain text link in a
     * collapsed list and once as the icon tile in the top categories section.
     * Only the tile is visible, so the variant carrying the category image is
     * listed first.
     *
     * @param categoryName visible category label, for example "Mobiles"
     * @return ordered candidates
     */
    public List<Locator> topCategory(String categoryName) {
        Pattern exact = Pattern.compile("^\\s*" + Pattern.quote(categoryName) + "\\s*$",
                Pattern.CASE_INSENSITIVE);

        return List.of(
                page.locator("a").filter(new Locator.FilterOptions()
                        .setHas(page.locator("img[alt=\"" + categoryName + "\"]"))),
                page.getByRole(AriaRole.LINK,
                        new Page.GetByRoleOptions().setName(categoryName).setExact(true)),
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exact)),
                page.locator("a").filter(new Locator.FilterOptions().setHasText(exact)));
    }

    /**
     * The Mobiles category tile.
     *
     * @return ordered candidates
     */
    public List<Locator> mobilesCategory() {
        return topCategory("Mobiles");
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    /**
     * The country / location selector.
     *
     * <p>OLX renders this as a text input whose <em>value</em> is the selected
     * location, not as a native {@code <select>}, so the selection is verified
     * with a value assertion rather than a selected-option lookup.
     *
     * @return ordered candidates
     */
    public List<Locator> countryDropdown() {
        return List.of(
                page.getByPlaceholder("Location", new Page.GetByPlaceholderOptions().setExact(true)),
                page.locator("input[placeholder=\"Location\"]"),
                page.locator("header input[placeholder*=\"Location\" i]"));
    }

    /**
     * The country currently held by the location selector. Same control as
     * {@link #countryDropdown()}; named separately so step definitions read
     * naturally.
     *
     * @return ordered candidates
     */
    public List<Locator> selectedCountry() {
        return countryDropdown();
    }

    /**
     * The main search textbox next to the country selector.
     *
     * @return ordered candidates
     */
    public List<Locator> searchTextbox() {
        return List.of(
                page.getByPlaceholder("Find Cars, Mobile Phones and more..."),
                page.locator("input[placeholder^=\"Find Cars\"]"),
                page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("find cars", Pattern.CASE_INSENSITIVE))));
    }

    // ------------------------------------------------------------------
    // Sorting
    // ------------------------------------------------------------------

    /**
     * The "Sort by" dropdown trigger.
     *
     * <p>Rendered as a button holding two spans ("Sort by: " and the current
     * value) plus a chevron image whose {@code alt} text is stable across
     * deploys, which makes it the most reliable anchor available.
     *
     * @return ordered candidates
     */
    public List<Locator> sortByDropdown() {
        return List.of(
                page.locator("button:has(img[alt=\"Sort options dropdown\"])"),
                page.locator("button").filter(new Locator.FilterOptions()
                        .setHasText(Pattern.compile("sort by", Pattern.CASE_INSENSITIVE))),
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("sort by", Pattern.CASE_INSENSITIVE))));
    }

    /**
     * The option list revealed by the sort dropdown.
     *
     * @return ordered candidates
     */
    public List<Locator> sortOptionsList() {
        return List.of(
                page.getByRole(AriaRole.LISTBOX),
                page.locator("ul[role=\"listbox\"]"));
    }

    /**
     * A single option inside the open sort dropdown.
     *
     * @param optionName visible option label, for example "Newly listed"
     * @return ordered candidates
     */
    public List<Locator> sortOption(String optionName) {
        Pattern exact = Pattern.compile("^\\s*" + Pattern.quote(optionName) + "\\s*$",
                Pattern.CASE_INSENSITIVE);

        return List.of(
                page.getByRole(AriaRole.OPTION,
                        new Page.GetByRoleOptions().setName(optionName).setExact(true)),
                page.locator("li[role=\"option\"]")
                        .filter(new Locator.FilterOptions().setHasText(exact)),
                page.getByRole(AriaRole.LISTBOX).getByText(exact));
    }

    /**
     * The "Newly listed" sort option.
     *
     * @return ordered candidates
     */
    public List<Locator> newlyListedOption() {
        return sortOption("Newly listed");
    }

    /**
     * The option currently marked {@code aria-selected="true"}.
     *
     * @return ordered candidates
     */
    public List<Locator> selectedSortOption() {
        return List.of(
                page.locator("li[role=\"option\"][aria-selected=\"true\"]"),
                page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setSelected(true)));
    }

    // ------------------------------------------------------------------
    // Listings
    // ------------------------------------------------------------------

    /**
     * The advert cards on a category page.
     *
     * <p>The {@code li[aria-label="Listing"]} variant is listed first because the
     * item anchors attach before the card renders and have zero size until their
     * image loads.
     *
     * @return ordered candidates
     */
    public List<Locator> listingsContainer() {
        return List.of(
                page.locator("li[aria-label=\"Listing\"]"),
                page.locator("article"),
                page.locator("a[href*=\"-iid-\"]"));
    }

    /**
     * Links to individual adverts. OLX item URLs always carry an
     * {@code -iid-<id>} suffix, which makes this a reliable structural anchor.
     *
     * @return ordered candidates
     */
    public List<Locator> listingLinks() {
        return List.of(
                page.locator("a[href*=\"-iid-\"]"),
                page.locator("a[href*=\"/item/\"]"));
    }

    /**
     * The main page heading.
     *
     * @return ordered candidates
     */
    public List<Locator> pageHeading() {
        return List.of(
                page.getByRole(AriaRole.HEADING).first(),
                page.locator("h1").first());
    }

    /**
     * The loading spinner shown while a sorted result set is re-fetched.
     *
     * @return ordered candidates
     */
    public List<Locator> loadingSpinner() {
        return List.of(
                page.locator("[class*=\"loader\" i]"),
                page.locator("[class*=\"spinner\" i]"),
                page.getByRole(AriaRole.PROGRESSBAR));
    }

    // ------------------------------------------------------------------
    // Interstitials
    // ------------------------------------------------------------------

    /**
     * Cookie and consent banners.
     *
     * @return ordered candidates
     */
    public List<Locator> cookieAcceptButton() {
        return List.of(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(
                        Pattern.compile("accept all|accept cookies|i agree|got it",
                                Pattern.CASE_INSENSITIVE))),
                page.locator("#onetrust-accept-btn-handler"));
    }

    /**
     * Close controls for login and promo overlays.
     *
     * @return ordered candidates
     */
    public List<Locator> modalCloseButton() {
        return List.of(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("^close$", Pattern.CASE_INSENSITIVE))),
                page.locator("button[aria-label=\"Close\"]"));
    }
}
