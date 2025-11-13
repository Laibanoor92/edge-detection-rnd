package com.example.minimalnativeapp

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SimpleRenderer : GLSurfaceView.Renderer {

    private var programId = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var samplerHandle = 0

    private var vertexBufferId = 0
    private var textureId = 0

    private var viewportWidth = 0
    private var viewportHeight = 0

    private var textureWidth = 0
    private var textureHeight = 0

    private var pixelBuffer: ByteBuffer? = null
    private var pendingWidth = 0
    private var pendingHeight = 0
    private val needsUpload = AtomicBoolean(false)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        programId = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord")
        samplerHandle = GLES20.glGetUniformLocation(programId, "uTexture")

        vertexBufferId = createQuadBuffer()
        textureId = createTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (needsUpload.compareAndSet(true, false)) {
            uploadTexture()
        }

        if (textureId == 0 || programId == 0) return

        GLES20.glUseProgram(programId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(samplerHandle, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        val stride = 4 * FLOAT_BYTE_SIZE

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            stride,
            0
        )

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(
            texCoordHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            stride,
            2 * FLOAT_BYTE_SIZE
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun updateTexture(data: ByteArray, width: Int, height: Int) {
        if (data.isEmpty() || width <= 0 || height <= 0) return

        val requiredCapacity = width * height * 4
        if (data.size != requiredCapacity) {
            Log.w(TAG, "Unexpected frame size: ${data.size} != $requiredCapacity")
            return
        }
        if (pixelBuffer == null || pixelBuffer?.capacity() != requiredCapacity) {
            pixelBuffer = ByteBuffer.allocateDirect(requiredCapacity).order(ByteOrder.nativeOrder())
        }

        pixelBuffer?.apply {
            position(0)
            put(data)
            position(0)
            limit(requiredCapacity)
        }

        pendingWidth = width
        pendingHeight = height
        needsUpload.set(true)
    }

    private fun uploadTexture() {
        val buffer = pixelBuffer ?: return
        if (textureId == 0) return

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        buffer.position(0)

        if (pendingWidth != textureWidth || pendingHeight != textureHeight) {
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                pendingWidth,
                pendingHeight,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer
            )
            textureWidth = pendingWidth
            textureHeight = pendingHeight
        } else {
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                pendingWidth,
                pendingHeight,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer
            )
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun createQuadBuffer(): Int {
        val vertexData = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )

        val floatBuffer = ByteBuffer
            .allocateDirect(vertexData.size * FLOAT_BYTE_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData)
                position(0)
            }

        val bufferIds = IntArray(1)
        GLES20.glGenBuffers(1, bufferIds, 0)
        val vbo = bufferIds[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            vertexData.size * FLOAT_BYTE_SIZE,
            floatBuffer,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return vbo
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return id
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("Program link failed: $error")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Shader compilation failed: $error")
        }
        return shader
    }

    companion object {
        private const val FLOAT_BYTE_SIZE = 4
        private const val TAG = "SimpleRenderer"

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}
