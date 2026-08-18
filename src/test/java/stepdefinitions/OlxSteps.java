package stepdefinitions;

import context.TestContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pages.OlxPage;
import utils.LoggerUtility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step definitions for {@code features/olx.feature}.
 *
 * <p>Steps stay thin: they translate Gherkin into page-object calls and assert
 * on what those calls report. All waiting, locator fallback and interaction
 * logic lives in {@link OlxPage}, so a failure here always points at a business
 * expectation rather than at a selector.
 */
public class OlxSteps {

    private static final LoggerUtility LOG = LoggerUtility.forClass(OlxSteps.class);

    private final TestContext context;

    /**
     * @param context per-scenario context, injected by PicoContainer
     */
    public OlxSteps(TestContext context) {
        this.context = context;
    }

    private OlxPage olx() {
        return context.getOlxPage();
    }

    // ------------------------------------------------------------------
    // Given
    // ------------------------------------------------------------------

    @Given("I open OLX Pakistan website")
    public void iOpenOlxPakistanWebsite() {
        olx().openHomePage();

        String currentUrl = olx().getCurrentUrl();
        assertTrue(currentUrl.contains("olx.com.pk"),
                "Expected to be on the OLX Pakistan domain but the URL was: " + currentUrl);
    }

    // ------------------------------------------------------------------
    // When
    // ------------------------------------------------------------------

    @When("I click on {string} from the top categories section")
    public void iClickOnCategoryFromTopCategories(String categoryName) {
        assertEquals("Mobiles", categoryName,
                "Only the Mobiles category tile is implemented by this step.");

        olx().clickMobilesCategory();
        context.set("category", categoryName);
    }

    @When("I select {string} from the Sort By dropdown")
    public void iSelectFromTheSortByDropdown(String sortOption) {
        olx().selectSortByOption(sortOption);
        context.set("sortOption", sortOption);
    }

    // ------------------------------------------------------------------
    // Then
    // ------------------------------------------------------------------

    @Then("the Mobiles page should load successfully")
    public void theMobilesPageShouldLoadSuccessfully() {
        assertTrue(olx().verifyMobilesPageLoaded(),
                "The Mobiles category page did not load: expected the listings grid and the "
                        + "header search control to be present. Current URL: " + olx().getCurrentUrl());
    }

    @Then("page title should contain {string}")
    public void pageTitleShouldContain(String expectedFragment) {
        assertTrue(olx().verifyPageTitleContains(expectedFragment),
                "Expected the page title to contain '" + expectedFragment
                        + "' but it was: '" + olx().getPageTitle() + "'");
    }

    @Then("Country dropdown should have {string} selected")
    public void countryDropdownShouldHaveSelected(String expectedCountry) {
        assertEquals(expectedCountry, olx().getSelectedCountry(),
                "The country selector did not hold the expected country.");
    }

    @Then("Search textbox placeholder should be {string}")
    public void searchTextboxPlaceholderShouldBe(String expectedPlaceholder) {
        assertEquals(expectedPlaceholder, olx().getSearchPlaceholder(),
                "The search textbox placeholder did not match.");
    }

    @Then("listings should be refreshed")
    public void listingsShouldBeRefreshed() {
        olx().waitForListingsRefresh();

        String selectedSort = context.get("sortOption", String.class);
        if (selectedSort != null) {
            assertTrue(olx().verifySortOptionSelected(selectedSort),
                    "Expected the Sort By control to show '" + selectedSort
                            + "' but it showed: '" + olx().getSelectedSortOption() + "'");
        }
    }

    @Then("mobile listings should be displayed successfully")
    public void mobileListingsShouldBeDisplayedSuccessfully() {
        assertTrue(olx().verifyListingsDisplayed(),
                "Expected at least one mobile listing to be displayed but the grid was empty.");

        LOG.validation("Mobiles category verified end to end");
    }
}
