package com.example.minimalnativeapp.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class CameraGLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val rendererImpl = SimpleRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(rendererImpl)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateTexture(data: ByteArray, width: Int, height: Int) {
        queueEvent {
            rendererImpl.updateTexture(data, width, height)
        }
        requestRender()
    }

    fun setDisplayModeProcessed(processed: Boolean) {
        queueEvent {
            rendererImpl.setProcessedMode(processed)
        }
        requestRender()
    }

    fun updateRawFrame(data: ByteArray, width: Int, height: Int) {
        queueEvent {
            rendererImpl.updateRawTexture(data, width, height)
        }
        requestRender()
    }
}
