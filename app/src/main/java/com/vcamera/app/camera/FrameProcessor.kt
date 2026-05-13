package com.vcamera.app.camera

import android.graphics.SurfaceTexture
import android.opengl.*
import android.util.Log
import com.vcamera.app.model.CameraTransform
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 2.0 frame processor.
 * Handles zoom, rotate, flip, and translate transformations on camera frames.
 */
class FrameProcessor {

    private val TAG = "FrameProcessor"

    // OpenGL handles
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program: Int = 0
    private var textureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null

    // Shader attribute/uniform locations
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var stMatrixHandle: Int = 0
    private var textureHandle: Int = 0

    // Vertex buffer (full-screen quad)
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private val vertexCoords = floatArrayOf(
        -1f, -1f,
         1f, -1f,
        -1f,  1f,
         1f,  1f
    )

    private val texCoords = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )

    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)

    var transform: CameraTransform = CameraTransform.DEFAULT

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertexCoords); position(0) }

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(texCoords); position(0) }
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = (uSTMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }

    fun init(outputSurface: android.view.Surface, width: Int, height: Int) {
        setupEGL(outputSurface)
        program = createProgram()
        textureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(width, height)
        }

        GLES20.glViewport(0, 0, width, height)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(stMatrix, 0)

        Log.d(TAG, "FrameProcessor initialized: ${width}x${height}")
    }

    fun getSurfaceTexture(): SurfaceTexture? = surfaceTexture

    fun drawFrame() {
        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(stMatrix)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // Apply transform
        buildMVPMatrix()

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun buildMVPMatrix() {
        Matrix.setIdentityM(mvpMatrix, 0)

        // Apply zoom (inverse — zoom=0.5 means 2x magnification)
        val scale = 1f / transform.zoom
        Matrix.scaleM(mvpMatrix, 0, scale, scale, 1f)

        // Apply rotation
        Matrix.rotateM(mvpMatrix, 0, transform.rotation, 0f, 0f, 1f)

        // Apply flip
        val flipX = if (transform.flipHorizontal) -1f else 1f
        val flipY = if (transform.flipVertical) -1f else 1f
        Matrix.scaleM(mvpMatrix, 0, flipX, flipY, 1f)

        // Apply translation
        Matrix.translateM(mvpMatrix, 0, transform.translateX, transform.translateY, 0f)
    }

    private fun setupEGL(surface: android.view.Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(eglDisplay, null, 0, null, 0)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private fun createProgram(): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        return GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)

            positionHandle = GLES20.glGetAttribLocation(prog, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(prog, "aTexCoord")
            mvpMatrixHandle = GLES20.glGetUniformLocation(prog, "uMVPMatrix")
            stMatrixHandle = GLES20.glGetUniformLocation(prog, "uSTMatrix")
            textureHandle = GLES20.glGetUniformLocation(prog, "sTexture")
        }
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, code)
            GLES20.glCompileShader(it)
        }
    }

    private fun createExternalTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        return tex[0]
    }

    fun release() {
        surfaceTexture?.release()
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }
}
