package me.magnum.melonds.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import me.magnum.melonds.ui.theme.MelonTheme
import me.magnum.melonds.ui.welcome.WelcomeActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val version = "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})"

        setContent {
            MelonTheme {
                AboutScreen(
                    version = version,
                    onOpenLink = { openLink(it) },
                    onShowWelcome = { startActivity(WelcomeActivity.getIntent(this)) },
                    onBack = { finish() },
                )
            }
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser on the device, nothing sensible to do
        }
    }
}
