package me.magnum.melonds.github

const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"

const val PREF_KEY_GITHUB_CHECK_FOR_UPDATES = "github_check_for_updates"
const val PREF_KEY_GITHUB_UPDATE_CHANNEL = "github_update_channel"

const val GITHUB_REPOSITORY = "pruefsumme/widemelon-android"
const val GITHUB_RELEASE_URL_PREFIX = "https://github.com/$GITHUB_REPOSITORY/releases/"

enum class GitHubUpdateChannel(val preferenceValue: String) {
    STABLE("stable"),
    STABLE_AND_PRERELEASE("stable_and_prerelease");

    companion object {
        fun fromPreference(value: String?): GitHubUpdateChannel {
            return entries.firstOrNull { it.preferenceValue == value } ?: STABLE_AND_PRERELEASE
        }
    }
}
