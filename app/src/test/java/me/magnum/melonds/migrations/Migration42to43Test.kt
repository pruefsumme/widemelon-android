package me.magnum.melonds.migrations

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Migration42to43Test {

    @Test
    fun softwareRendererMovesToOpenGl() {
        val preferences = FakeSharedPreferences(mutableMapOf("video_renderer" to "software"))
        Migration42to43(preferences).migrate()
        assertEquals("opengl", preferences.getString("video_renderer", null))
    }

    @Test
    fun otherRenderersAreKept() {
        listOf("opengl", "vulkan", "compute").forEach { renderer ->
            val preferences = FakeSharedPreferences(mutableMapOf("video_renderer" to renderer))
            Migration42to43(preferences).migrate()
            assertEquals(renderer, preferences.getString("video_renderer", null))
        }
    }

    @Test
    fun unsetRendererStaysUnset() {
        val preferences = FakeSharedPreferences(mutableMapOf())
        Migration42to43(preferences).migrate()
        assertNull(preferences.getString("video_renderer", null))
    }

    private class FakeSharedPreferences(private val values: MutableMap<String, Any?>) : SharedPreferences {
        override fun getAll(): Map<String, *> = values
        override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { values.clear() }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                values.putAll(pending)
            }
        }
    }
}
