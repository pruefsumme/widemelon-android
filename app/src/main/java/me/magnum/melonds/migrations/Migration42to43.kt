package me.magnum.melonds.migrations

import android.content.SharedPreferences
import androidx.core.content.edit

class Migration42to43(private val sharedPreferences: SharedPreferences) : Migration {

    override val from = 42
    override val to = 43

    override fun migrate() {
        // 1.0.0 shipped with the software renderer as the default, which can't render real widescreen. Move those installs
        // over to OpenGL. Devices without GLES 3.2 still end up on software through the settings repository
        if (sharedPreferences.getString("video_renderer", null) == "software") {
            sharedPreferences.edit {
                putString("video_renderer", "opengl")
            }
        }
    }
}
