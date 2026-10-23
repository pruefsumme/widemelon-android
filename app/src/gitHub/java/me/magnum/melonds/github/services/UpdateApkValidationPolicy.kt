package me.magnum.melonds.github.services

import me.magnum.melonds.domain.model.Version

data class UpdateApkMetadata(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val signerDigests: Set<String>,
)

enum class UpdateApkRejectionReason {
    SIZE_MISMATCH,
    PACKAGE_MISMATCH,
    VERSION_CODE_NOT_NEWER,
    VERSION_NAME_MISMATCH,
    SIGNATURE_MISMATCH,
}

object UpdateApkValidationPolicy {
    const val EXPECTED_PACKAGE = "io.github.pruefsumme.widemelon"

    fun validate(
        candidate: UpdateApkMetadata,
        installed: UpdateApkMetadata,
        expectedVersion: Version,
        requireSemanticVersionMatch: Boolean,
        expectedSize: Long,
        actualSize: Long,
    ): UpdateApkRejectionReason? {
        if (expectedSize > 0L && expectedSize != actualSize) {
            return UpdateApkRejectionReason.SIZE_MISMATCH
        }
        if (candidate.packageName != EXPECTED_PACKAGE || installed.packageName != EXPECTED_PACKAGE) {
            return UpdateApkRejectionReason.PACKAGE_MISMATCH
        }
        if (candidate.versionCode <= installed.versionCode) {
            return UpdateApkRejectionReason.VERSION_CODE_NOT_NEWER
        }
        if (requireSemanticVersionMatch) {
            val candidateVersion = Version.parseOrNull(candidate.versionName)
            if (candidateVersion == null || !candidateVersion.isSameArtifactVersion(expectedVersion)) {
                return UpdateApkRejectionReason.VERSION_NAME_MISMATCH
            }
        }
        if (candidate.signerDigests.intersect(installed.signerDigests).isEmpty()) {
            return UpdateApkRejectionReason.SIGNATURE_MISMATCH
        }
        return null
    }
}
