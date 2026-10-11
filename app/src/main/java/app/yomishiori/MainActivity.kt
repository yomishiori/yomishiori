package app.yomishiori

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.yomishiori.ui.navigation.YomishioriNavHost
import app.yomishiori.ui.theme.YomishioriTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            YomishioriTheme { YomishioriNavHost() }
        }
    }
}
