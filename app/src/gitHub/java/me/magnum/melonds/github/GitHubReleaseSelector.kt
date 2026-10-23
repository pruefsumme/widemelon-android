package me.magnum.melonds.github

import me.magnum.melonds.domain.model.Version
import me.magnum.melonds.github.dtos.AssetDto
import me.magnum.melonds.github.dtos.ReleaseDto
import kotlin.time.Instant

data class GitHubReleaseCandidate(
    val release: ReleaseDto,
    val asset: AssetDto,
    val version: Version,
    val publishedAt: Instant,
)

object GitHubReleaseSelector {
    fun selectProduction(
        releases: List<ReleaseDto>,
        currentVersion: Version,
        channel: GitHubUpdateChannel,
        skippedVersion: Version?,
    ): GitHubReleaseCandidate? {
        return releases.asSequence()
            .filter(::belongsToConfiguredRepository)
            .filterNot { it.draft }
            .filter { channel == GitHubUpdateChannel.STABLE_AND_PRERELEASE || !it.prerelease }
            .mapNotNull { release ->
                val version = Version.parseOrNull(release.tagName) ?: return@mapNotNull null
                if (version.type == Version.ReleaseType.NIGHTLY || version <= currentVersion) {
                    return@mapNotNull null
                }
                if (skippedVersion != null && version == skippedVersion) {
                    return@mapNotNull null
                }
                val asset = release.assets.firstOrNull(::isProductionApk) ?: return@mapNotNull null
                val published = parsePublishedAt(release) ?: return@mapNotNull null
                GitHubReleaseCandidate(release, asset, version, published)
            }
            .maxWithOrNull(compareBy<GitHubReleaseCandidate> { it.version }.thenBy { it.publishedAt })
    }

    fun selectNightly(releases: List<ReleaseDto>): GitHubReleaseCandidate? {
        return releases.asSequence()
            .filter(::belongsToConfiguredRepository)
            .filterNot { it.draft }
            .mapNotNull { release ->
                val asset = release.assets.firstOrNull(::isNightlyApk) ?: return@mapNotNull null
                val published = parsePublishedAt(release) ?: return@mapNotNull null
                GitHubReleaseCandidate(release, asset, Version.Nightly, published)
            }
            .maxByOrNull { it.publishedAt }
    }

    fun isProductionApk(asset: AssetDto): Boolean {
        val lowerName = asset.name.lowercase()
        return isRepositoryDownload(asset) &&
            asset.contentType == APK_CONTENT_TYPE &&
            lowerName.endsWith(".apk") &&
            "widemelon" in lowerName &&
            "nightly" !in lowerName
    }

    fun isNightlyApk(asset: AssetDto): Boolean {
        val lowerName = asset.name.lowercase()
        return isRepositoryDownload(asset) &&
            asset.contentType == APK_CONTENT_TYPE &&
            lowerName.endsWith(".apk") &&
            "widemelon" in lowerName &&
            "nightly" in lowerName
    }

    private fun belongsToConfiguredRepository(release: ReleaseDto): Boolean {
        return release.htmlUrl.startsWith("${GITHUB_RELEASE_URL_PREFIX}tag/")
    }

    private fun isRepositoryDownload(asset: AssetDto): Boolean {
        return asset.id > 0 &&
            asset.size > 0 &&
            asset.url.startsWith("${GITHUB_RELEASE_URL_PREFIX}download/")
    }

    private fun parsePublishedAt(release: ReleaseDto): Instant? {
        return (release.publishedAt ?: release.createdAt)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
}
