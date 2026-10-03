package com.example.cad.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.cad.native.CadNativeEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CadSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var renderThread: Thread? = null
    @Volatile private var isRunning = false
    private val surfaceHolder: SurfaceHolder = holder.apply { addCallback(this@CadSurfaceView) }

    private val transformMatrix = Matrix()

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f
        typeface = Typeface.MONOSPACE
    }
    private val arcBounds = RectF()

    private var nativeBuffer: ByteBuffer? = null
    private var primitiveCount = 0
    private var modelMetadata: CadNativeEngine.ModelMetadata? = null

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                transformMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                transformMatrix.postTranslate(-distanceX, -distanceY)
                return true
            }
        })
    }

    fun bindModel() {
        if (!CadNativeEngine.isAvailable()) return
        nativeBuffer = CadNativeEngine.getDirectGeometryBuffer()?.order(ByteOrder.nativeOrder())
        primitiveCount = CadNativeEngine.getPrimitiveCount()
        modelMetadata = CadNativeEngine.fetchMetadata()
        resetViewToFit()
    }

    private fun resetViewToFit() {
        val meta = modelMetadata ?: return
        if (width <= 0 || height <= 0 || meta.width <= 0f || meta.height <= 0f) return

        val scaleX = (width * 0.9f) / meta.width
        val scaleY = (height * 0.9f) / meta.height
        val fitScale = minOf(scaleX, scaleY)

        transformMatrix.reset()
        transformMatrix.postScale(fitScale, -fitScale)
        transformMatrix.postTranslate(width * 0.5f, height * 0.5f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isRunning = true
        renderThread = Thread(this, "CAD_RenderThread").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        resetViewToFit()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRunning = false
        var retry = true
        while (retry) {
            try {
                renderThread?.join()
                retry = false
            } catch (_: InterruptedException) {}
        }
        renderThread = null
    }

    override fun run() {
        while (isRunning) {
            val canvas = surfaceHolder.lockHardwareCanvas() ?: continue
            try {
                renderScene(canvas)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }
    }

    private fun renderScene(canvas: Canvas) {
        canvas.drawColor(0xFF212830.toInt())

        val buffer = nativeBuffer ?: return
        if (primitiveCount == 0) return

        canvas.save()
        canvas.concat(transformMatrix)

        buffer.position(0)
        for (i in 0 until primitiveCount) {
            val type = buffer.get().toInt()
            val layerId = buffer.get()
            val colorRgb565 = buffer.short.toInt() and 0xFFFF
            val x1 = buffer.float
            val y1 = buffer.float
            val x2 = buffer.float
            val y2 = buffer.float
            val radius = buffer.float
            val startAngle = buffer.float
            val sweepAngle = buffer.float
            val rotation = buffer.float

            val r = ((colorRgb565 shr 11) and 0x1F) * 255 / 31
            val g = ((colorRgb565 shr 5) and 0x3F) * 255 / 63
            val b = (colorRgb565 and 0x1F) * 255 / 31
            
            // Contrast control: Invert dark strokes to solid white for maximum legibility on dark canvas
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            if (luminance < 75f || (r < 75 && g < 75 && b < 75)) {
                linePaint.color = Color.WHITE
            } else {
                linePaint.color = Color.rgb(r, g, b)
            }

            when (type) {
                1, 4, 6 -> {
                    canvas.drawLine(x1, y1, x2, y2, linePaint)
                }
                2 -> {
                    // Strict ARC Rendering: Only draw if sweepAngle > 0.05f with useCenter = false
                    if (sweepAngle > 0.05f) {
                        arcBounds.set(x1 - radius, y1 - radius, x1 + radius, y1 + radius)
                        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, linePaint)
                    }
                }
                3 -> {
                    canvas.drawCircle(x1, y1, radius, linePaint)
                }
                5 -> {
                    canvas.save()
                    canvas.translate(x1, y1)
                    canvas.rotate(rotation)
                    canvas.scale(1f, -1f)
                    textPaint.textSize = if (radius > 1f) radius else 12f
                    canvas.drawText("CAD_TEXT", 0f, 0f, textPaint)
                    canvas.restore()
                }
            }
        }
        canvas.restore()
    }
}
