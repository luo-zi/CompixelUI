package dev.compixel.render.gl

import org.jetbrains.skia.DirectContext
import org.lwjgl.opengl.GL33C.glDeleteProgram

/**
 * Skia's GPU context and the compositor program of one GL context, for the pages that draw on it one after another.
 * Creating both takes a noticeable part of a page's first frame, and every page composites the same way, so a host
 * creates one of these per GL context, gives it to each [OpenGlFrameRenderer] that draws there, and [close]s it when
 * the context goes away rather than with the page. Its pages draw in turn, never at the same time.
 *
 * Every member runs on the render thread that owns the GL context, from [attach] to [detach], and from [close].
 */
class SharedGlResources : AutoCloseable {
    private var identity: Any? = null
    private var skia: DirectContext? = null
    private var compositor = 0
    private var renderers = 0
    private var closed = false

    /** [renderer] draws on the GL context that [identity] names, and detaches when it closes. */
    internal fun attach(identity: Any) {
        check(!closed) { "Shared GL resources are closed" }
        check(this.identity == null || this.identity === identity) {
            "Shared GL resources belong to another GL context; create one per context"
        }
        this.identity = identity
        renderers++
    }

    internal fun detach() {
        renderers--
        releaseIfUnused()
    }

    /** Skia's GPU context, made with the GL context current on its first use. */
    internal fun directContext(): DirectContext {
        skia?.let {
            return it
        }
        // Skiko refuses OpenGL on macOS unless this is set, but this backend draws into the game's own context.
        System.setProperty("skiko.macos.opengl.enabled", "true")
        return DirectContext.makeGL().also {
            it.resourceCacheLimit = CACHE_LIMIT
            skia = it
        }
    }

    /** The program that composites a frame, compiled by [create] on its first use and reused afterwards. */
    internal fun compositorProgram(create: () -> Int): Int {
        if (compositor == 0) compositor = create()
        return compositor
    }

    override fun close() {
        closed = true
        releaseIfUnused()
    }

    // The last renderer keeps drawing until it closes, so the context and program wait for it.
    private fun releaseIfUnused() {
        if (!closed || renderers > 0) return
        skia?.close()
        skia = null
        if (compositor != 0) glDeleteProgram(compositor)
        compositor = 0
    }

    private companion object {
        const val CACHE_LIMIT = 64L * 1024 * 1024
    }
}
