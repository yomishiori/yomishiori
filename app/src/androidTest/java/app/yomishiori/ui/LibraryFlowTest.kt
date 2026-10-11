package app.yomishiori.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yomishiori.MainActivity
import app.yomishiori.resetAppData
import app.yomishiori.testContainer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class LibraryFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                resetAppData()
                runBlocking { testContainer.preferences.update { it.copy(library = it.library.copy(statusFilter = emptySet(), typeFilter = emptySet())) } }
                base.evaluate()
            }
        }
    }).around(compose)

    @Test fun addSearchAndOpenEntry() {
        compose.onNodeWithText("Your library is empty").assertIsDisplayed()
        compose.onNodeWithText("Add a novel").performClick()
        compose.onNodeWithText("Add novel manually").performClick()

        compose.onNodeWithTag("title_field").performTextInput("Shuumatsu Nani Shitemasu ka")
        compose.onNodeWithTag("url_field").performTextInput("example.com/shuukura/ch1")
        compose.onNodeWithTag("save_entry").performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Sources")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("example.com").assertIsDisplayed()
        val entry = runBlocking { testContainer.libraryRepository.getAllEntries().single() }
        assertEquals("Shuumatsu Nani Shitemasu ka", entry.title)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Shuumatsu Nani Shitemasu ka").assertIsDisplayed()

        val found = runCatching { compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Search")).fetchSemanticsNodes().isNotEmpty() } }
        check(found.isSuccess) { compose.onRoot().printToString() }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithTag("library_search").performTextInput("ＳＨＵＵＭＡＴＳＵ")
        compose.onNodeWithText("Shuumatsu Nani Shitemasu ka").assertIsDisplayed()
        compose.onNodeWithTag("library_search").performTextInput("zzz")
        compose.onNodeWithText("Nothing matches").assertIsDisplayed()
    }
}
