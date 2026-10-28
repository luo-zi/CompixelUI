package dev.compixel.forge.render

import dev.compixel.render.gl.SharedGlResources

/** Skia's GPU context and compositor program of one GL context, shared by the pages that draw on it in turn. */
internal val sharedGlResources = SharedGlResources()
