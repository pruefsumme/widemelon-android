package me.magnum.melonds.domain.model.render

data class PresentFrameWrapper(
    var isValidFrame: Boolean = false,
    var textureId: Int = 0,
    /** Width of the top screen in native pixels, 256 unless widescreen is active */
    var viewWidth: Int = 256,
)
