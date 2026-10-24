package me.magnum.melonds.ui.settings.fragments

import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import me.magnum.melonds.R
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider

class WideMelonPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    private lateinit var modePreference: ListPreference
    private lateinit var customWidthPreference: SeekBarPreference
    private lateinit var bottomScreenModePreference: ListPreference
    private lateinit var overlaySizePreference: SeekBarPreference
    private lateinit var overlayOpacityPreference: SeekBarPreference
    private lateinit var fallbackPreference: ListPreference

    override fun getTitle() = getString(R.string.category_widemelon)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_widemelon, rootKey)
        modePreference = findPreference("widescreen_mode")!!
        customWidthPreference = findPreference("widescreen_custom_width")!!
        bottomScreenModePreference = findPreference("widescreen_bottom_screen_mode")!!
        overlaySizePreference = findPreference("widescreen_overlay_size")!!
        overlayOpacityPreference = findPreference("widescreen_overlay_opacity")!!
        fallbackPreference = findPreference("widescreen_fallback")!!

        modePreference.setOnPreferenceChangeListener { _, newValue ->
            updateVisibility(mode = newValue as String)
            true
        }
        bottomScreenModePreference.setOnPreferenceChangeListener { _, newValue ->
            updateVisibility(bottomScreenMode = newValue as String)
            true
        }
        customWidthPreference.setOnPreferenceChangeListener { _, newValue ->
            // the seekbar can't snap to even values by itself, the native side wants an even width
            val width = (newValue as Int) and 1.inv()
            customWidthPreference.summary = getString(R.string.widescreen_custom_width_value, width)
            if (width != newValue) {
                customWidthPreference.value = width
                false
            } else {
                true
            }
        }
        overlaySizePreference.setOnPreferenceChangeListener { _, newValue ->
            overlaySizePreference.summary = getString(R.string.widescreen_percentage, newValue as Int)
            true
        }
        overlayOpacityPreference.setOnPreferenceChangeListener { _, newValue ->
            overlayOpacityPreference.summary = getString(R.string.widescreen_percentage, newValue as Int)
            true
        }

        customWidthPreference.summary = getString(R.string.widescreen_custom_width_value, customWidthPreference.value)
        overlaySizePreference.summary = getString(R.string.widescreen_percentage, overlaySizePreference.value)
        overlayOpacityPreference.summary = getString(R.string.widescreen_percentage, overlayOpacityPreference.value)
        updateVisibility()
        setupNotice()
    }

    private fun setupNotice() {
        val notice = findPreference<Preference>("widescreen_notice") ?: return
        val preferences = preferenceManager.sharedPreferences ?: return
        if (preferences.getBoolean(KEY_NOTICE_DISMISSED, false)) {
            notice.isVisible = false
            return
        }

        notice.summary = listOf(
            getString(R.string.widescreen_compatibility_note),
            getString(R.string.widescreen_photosensitivity_note),
            getString(R.string.widescreen_notice_dismiss_hint),
        ).joinToString("\n\n")
        notice.setOnPreferenceClickListener {
            preferences.edit { putBoolean(KEY_NOTICE_DISMISSED, true) }
            notice.isVisible = false
            true
        }
    }

    private fun updateVisibility(
        mode: String = modePreference.value,
        bottomScreenMode: String = bottomScreenModePreference.value,
    ) {
        val enabled = mode != "off"
        customWidthPreference.isVisible = mode == "custom"
        bottomScreenModePreference.isVisible = enabled
        overlaySizePreference.isVisible = enabled && bottomScreenMode == "overlay"
        overlayOpacityPreference.isVisible = enabled && bottomScreenMode == "overlay"
        fallbackPreference.isVisible = enabled
    }

    private companion object {
        const val KEY_NOTICE_DISMISSED = "widescreen_notice_dismissed"
    }
}
