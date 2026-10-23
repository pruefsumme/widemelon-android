package me.magnum.melonds.ui.emulator.render

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import androidx.core.os.bundleOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import me.magnum.melonds.MelonDSAndroidInterface
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.RuntimeBackground
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.emulator.EmulatorSurfaceView
import me.magnum.melonds.ui.emulator.model.VulkanPresentationConfig

class OpenGlFrameRenderCoordinator : FrameRenderCoordinator {

    private val glContext: GlContext
    private val frameRenderThread = FrameRenderThread()
    private val presentFrameWrapper = PresentFrameWrapper()
    private val surfacesLock = Any()
    private val managedSurfaces = mutableListOf<EmulatorSurfaceView>()
    private val surfacesPendingRemoval = mutableListOf<EmulatorSurfaceView>()
    @Volatile private var stopped = false

    init {
        glContext = GlContext(MelonDSAndroidInterface.getEmulatorGlContext())
        frameRenderThread.start()
    }

    override fun addSurface(surface: EmulatorSurfaceView) {
        if (stopped) {
            return
        }
        synchronized(surfacesLock) {
            managedSurfaces.add(surface)
        }
    }

    override fun removeSurface(surface: EmulatorSurfaceView) {
        if (stopped) {
            return
        }
        synchronized(surfacesLock) {
            managedSurfaces.remove(surface)
            surfacesPendingRemoval.add(surface)
            frameRenderThread.requestSurfaceDestruction()
        }
    }

    override fun renderFrame(frameDeadlineNanos: Long?) {
        if (stopped) {
            return
        }
        frameRenderThread.requestFrameRender(frameDeadlineNanos)
    }

    override fun prewarmShaders(atlasWidth: Int, atlasHeight: Int): Long {
        if (stopped) {
            return 0L
        }
        return frameRenderThread.prewarmShaders(atlasWidth, atlasHeight)
    }

    override fun updateSurfacePresentation(
        surface: EmulatorSurfaceView,
        config: VulkanPresentationConfig?,
        background: RuntimeBackground,
    ) {
        // OpenGL presentation does not use the native Vulkan presenter.
    }

    override fun stop() {
        if (stopped) {
            return
        }
        stopped = true
        frameRenderThread.requestStop()
        frameRenderThread.quitSafely()
        frameRenderThread.join()
    }

    private inner class FrameRenderThread : HandlerThread("FrameRenderThread") {

        private var handler: Handler? = null
        @Volatile private var running = true
        private var cleanedUp = false
        private val renderStatistics = RenderStatistics()

        private val frameRenderCallback = object : FrameRenderCallback {
            override fun renderFrame(isValidFrame: Boolean, frameTextureId: Int, viewWidth: Int) {
                val renderStart = System.nanoTime()

                presentFrameWrapper.apply {
                    this.isValidFrame = isValidFrame
                    this.textureId = frameTextureId
                    this.viewWidth = viewWidth
                }

                managedSurfaces.forEach {
                    it.doFrame(glContext, presentFrameWrapper)
                }

                val renderDuration = System.nanoTime() - renderStart
                renderStatistics.trackRenderEvent(renderDuration)
            }
        }

        override fun onLooperPrepared() {
            handler = object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        MSG_RENDER_FRAME -> renderFrame(msg.data.getLong(MSG_RENDER_FRAME_FRAME_DEADLINE_NS))
                        MSG_DESTROY_SURFACES -> destroySurfaces()
                        MSG_PREWARM_SHADERS -> prewarmShaders(msg.arg1, msg.arg2, msg.obj as PrewarmRequest)
                        MSG_STOP -> stopThread()
                    }
                }
            }
        }

        fun requestFrameRender(frameDeadlineNanos: Long?) {
            if (!running) {
                return
            }
            handler?.removeMessages(MSG_RENDER_FRAME)
            handler?.obtainMessage(MSG_RENDER_FRAME)?.let {
                it.data = bundleOf(MSG_RENDER_FRAME_FRAME_DEADLINE_NS to (frameDeadlineNanos ?: 0L))
                handler?.sendMessage(it)
            }
        }

        fun prewarmShaders(atlasWidth: Int, atlasHeight: Int): Long {
            val handler = this.handler ?: return 0L
            if (!running) {
                return 0L
            }
            val request = PrewarmRequest(CountDownLatch(1))
            val message = Message.obtain(handler, MSG_PREWARM_SHADERS, atlasWidth, atlasHeight, request)
            if (!handler.sendMessageAtFrontOfQueue(message)) {
                return 0L
            }
            if (!request.latch.await(PREWARM_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                Log.w(TAG, "Shader prewarm timed out; the first frames may stutter while it finishes")
                return 0L
            }
            return request.elapsedMillis
        }

        fun requestSurfaceDestruction() {
            if (!running) {
                return
            }
            handler?.removeMessages(MSG_DESTROY_SURFACES)
            handler?.sendEmptyMessage(MSG_DESTROY_SURFACES)
        }

        fun requestStop() {
            running = false
            handler?.sendMessageAtFrontOfQueue(Message.obtain(handler, MSG_STOP))
        }

        private fun renderFrame(frameDeadlineNanos: Long) {
            if (!running) {
                return
            }

            synchronized(surfacesLock) {
                val deadline = if (frameDeadlineNanos > 0) {
                    // Use 2 times the average render duration to be safe. A large margin is required because only the CPU time is being measured and the GPU is performing
                    // additional work, which is not being measured. As a future improvement, GPU time can be taken into account as well to obtain a more accurate deadline
                    frameDeadlineNanos - (renderStatistics.getMeanRenderDurationNs() * 2f).toLong()
                } else {
                    0L
                }
                glContext.useWorker()
                MelonEmulator.presentFrame(deadline, frameRenderCallback)
            }
        }

        private fun prewarmShaders(atlasWidth: Int, atlasHeight: Int, request: PrewarmRequest) {
            try {
                if (!running || !glContext.useWorker()) {
                    return
                }
                val started = System.nanoTime()
                val succeeded = runCatching {
                    MelonEmulator.prewarmOpenGlRetroArchFilter(atlasWidth, atlasHeight)
                }.getOrDefault(false)
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                request.elapsedMillis = if (succeeded) elapsedMs else 0L
                Log.i(TAG, "Shader prewarm ${if (succeeded) "ready" else "failed"} in ${elapsedMs}ms (atlas ${atlasWidth}x$atlasHeight)")
            } finally {
                request.latch.countDown()
            }
        }

        private fun destroySurfaces() {
            synchronized(surfacesLock) {
                surfacesPendingRemoval.forEach {
                    it.stop(glContext)
                }
                surfacesPendingRemoval.clear()
            }
        }

        private fun stopThread() {
            if (cleanedUp) {
                return
            }
            cleanedUp = true
            running = false

            synchronized(surfacesLock) {
                managedSurfaces.forEach {
                    it.stop(glContext)
                }
                surfacesPendingRemoval.forEach {
                    it.stop(glContext)
                }
                managedSurfaces.clear()
                surfacesPendingRemoval.clear()
            }

            if (glContext.useWorker()) {
                runCatching { MelonEmulator.releaseOpenGlRetroArchFilter() }
            }
            glContext.release()
            glContext.destroy()
        }
    }

    private class PrewarmRequest(val latch: CountDownLatch) {
        @Volatile var elapsedMillis: Long = 0L
    }

    private class RenderStatistics {
        private var meanRenderDurationNs = 0L
        private var collectedSamples = 0

        fun trackRenderEvent(durationNs: Long) {
            if (collectedSamples < 60) {
                collectedSamples++
            }

            meanRenderDurationNs = (meanRenderDurationNs * (collectedSamples - 1) + durationNs) / collectedSamples
        }

        fun getMeanRenderDurationNs(): Long {
            return meanRenderDurationNs
        }
    }

    private companion object {
        const val TAG = "OpenGlFrameRenderCoordinator"

        const val MSG_RENDER_FRAME = 1
        const val MSG_DESTROY_SURFACES = 2
        const val MSG_STOP = 3
        const val MSG_PREWARM_SHADERS = 4

        const val MSG_RENDER_FRAME_FRAME_DEADLINE_NS = "frame-deadline"

        const val PREWARM_TIMEOUT_MINUTES = 10L
    }
}
