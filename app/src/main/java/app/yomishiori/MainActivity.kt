package app.yomishiori

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.yomishiori.epub.TutorialBook
import app.yomishiori.ui.navigation.YomishioriNavHost
import app.yomishiori.ui.theme.YomishioriTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            YomishioriTheme { YomishioriNavHost() }
        }
        if (savedInstanceState == null) lifecycleScope.launch { showTutorialOnFirstLaunch() }
    }

    private suspend fun showTutorialOnFirstLaunch() {
        if (container.preferences.current().tutorialDone) return
        container.preferences.update { it.copy(tutorialDone = true) }
        if (container.libraryRepository.getAllEntries().isNotEmpty()) return
        try {
            TutorialBook.open(this)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Couldn't open the tutorial.", Toast.LENGTH_LONG).show()
        }
    }
}
