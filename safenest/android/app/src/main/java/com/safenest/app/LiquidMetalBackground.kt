package com.safenest.app

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Decorative only: never owns input, VPN state, or protection lifecycle. */
@Composable
internal fun LiquidMetalBackground(animate: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember(context) { LiquidMetalView(context) }
    AndroidView(factory = { view }, modifier = modifier, update = { it.motionEnabled = animate })
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.start()
                Lifecycle.Event.ON_STOP -> view.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.start()
        onDispose { lifecycle.removeObserver(observer); view.stop() }
    }
}

private class LiquidMetalView(context: Context) : GLSurfaceView(context) {
    var motionEnabled = true
    private val handler = Handler(Looper.getMainLooper())
    private val metal = MetalRenderer(context)
    private val power = context.getSystemService(PowerManager::class.java)
    private var running = false
    private var last = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            val animate = motionEnabled && !reduced && !power.isPowerSaveMode
            val now = android.os.SystemClock.uptimeMillis()
            if (animate) {
                metal.seconds += if (last == 0L) 0f else ((now - last) / 1000f).coerceAtMost(0.1f)
                requestRender()
            }
            last = if (animate) now else 0L
            handler.postDelayed(this, if (animate) 34L else 1000L)
        }
    }
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(metal)
        renderMode = RENDERMODE_WHEN_DIRTY
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val scale = minOf(1f, 1200f / maxOf(w, h))
            holder.setFixedSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        }
    }
    fun start() {
        if (running) return
        running = true
        onResume()
        requestRender()
        handler.post(tick)
    }
    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        last = 0L
        onPause()
    }
}

private class MetalRenderer(private val context: Context) : GLSurfaceView.Renderer {
    @Volatile var seconds = 0f
    private val fragment = context.assets.open("liquid-metal.frag").bufferedReader().use { it.readText() }
    private val vertices = ByteBuffer.allocateDirect(12 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, -1f,1f, 1f,-1f, 1f,1f)); position(0) }
    private var program = 0
    private var width = 1
    private var height = 1
    private var resolution = -1
    private var time = -1
    private var texture = 0
    private var artSize = -1
    private var artWidth = 1
    private var artHeight = 1
    private var position = -1
    private fun shader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val compileMessage = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error(compileMessage)
        }
        return shader
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = 0
        GLES20.glClearColor(.76f, .38f, .53f, 1f)
        val shaders = mutableListOf<Int>()
        var candidate = 0
        try {
            shaders += shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 position; void main(){gl_Position=vec4(position,0.0,1.0);}")
            shaders += shader(GLES20.GL_FRAGMENT_SHADER, fragment)
            candidate = GLES20.glCreateProgram()
            shaders.forEach { GLES20.glAttachShader(candidate, it) }
            GLES20.glLinkProgram(candidate)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(candidate, GLES20.GL_LINK_STATUS, linked, 0)
            check(linked[0] != 0) { GLES20.glGetProgramInfoLog(candidate) }
            program = candidate
            position = GLES20.glGetAttribLocation(program, "position")
            resolution = GLES20.glGetUniformLocation(program, "u_resolution")
            time = GLES20.glGetUniformLocation(program, "u_time")
            artSize = GLES20.glGetUniformLocation(program, "u_artSize")
            val artwork = context.assets.open("liquid-artwork.webp").use { BitmapFactory.decodeStream(it) }
                ?: error("Liquid artwork unavailable")
            try {
                val textures = IntArray(1)
                GLES20.glGenTextures(1, textures, 0)
                texture = textures[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, artwork, 0)
                artWidth = artwork.width; artHeight = artwork.height
                GLES20.glUseProgram(program)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "u_art"), 0)
            } finally { artwork.recycle() }
        } catch (failure: Exception) {
            if (candidate != 0) GLES20.glDeleteProgram(candidate)
            program = 0
            Log.w("SafeNestMetal", "Using baby pink fallback", failure)
        } finally { shaders.forEach { GLES20.glDeleteShader(it) } }
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        GLES20.glViewport(0, 0, w, h)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glUniform2f(resolution, width.toFloat(), height.toFloat())
        GLES20.glUniform2f(artSize, artWidth.toFloat(), artHeight.toFloat())
        GLES20.glUniform1f(time, seconds)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
    }
}
