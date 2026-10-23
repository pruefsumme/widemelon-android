package me.magnum.melonds.ui.welcome

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import me.magnum.melonds.ui.theme.MelonTheme

/**
 * Short explanation of the WideMelon features, shown once on first launch (and again from the About screen).
 */
class WelcomeActivity : AppCompatActivity() {

    companion object {
        private const val KEY_WELCOME_SEEN = "widemelon_welcome_seen"

        fun shouldShow(context: Context): Boolean {
            return !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_WELCOME_SEEN, false)
        }

        fun getIntent(context: Context) = Intent(context, WelcomeActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        // A presentation display at launch is a good hint that this is a dual screen handheld
        val displayManager = getSystemService(DisplayManager::class.java)
        val hasSecondScreen = displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.isNotEmpty() == true

        setContent {
            MelonTheme {
                WelcomeScreen(
                    hasSecondScreen = hasSecondScreen,
                    onDone = { finish() },
                )
            }
        }
    }

    override fun finish() {
        markSeen()
        super.finish()
    }

    private fun markSeen() {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putBoolean(KEY_WELCOME_SEEN, true)
        }
    }
}
