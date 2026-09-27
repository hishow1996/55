package com.example.ui.browser

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.webkit.WebView
import kotlin.math.pow

/**
 * WebView with explicit two-finger pinch zoom and one-finger panning after zoom.
 * Built-in zoom controls remain hidden; the gesture is owned by this view.
 */
class PinchZoomWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private var pinchActive = false
    private var pageZoom = 1f
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var panStarted = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinchActive = detector.currentSpan >= 16f
                if (pinchActive) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return pinchActive
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!pinchActive) return false

                val amplified = detector.scaleFactor
                    .toDouble()
                    .coerceIn(0.5, 2.0)
                    .pow(1.20)

                if (amplified.isFinite() && amplified > 0.0 && amplified != 1.0) {
                    val currentZoom = pageZoom
                    val targetZoom = (currentZoom * amplified.toFloat()).coerceIn(0.5f, 5f)
                    val effectiveDelta = targetZoom / currentZoom
                    if (effectiveDelta.isFinite() && effectiveDelta > 0f && effectiveDelta != 1f) {
                        zoomBy(effectiveDelta)
                        pageZoom = targetZoom
                    }
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                pinchActive = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    )

    init {
        // Keep WebView's zoom engine enabled because zoomBy() is a no-op on
        // some Android System WebView versions when supportZoom is disabled.
        // The visible legacy controls remain disabled.
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
    }

    fun resetGestureZoomState() {
        if (pageZoom != 1f) {
            val restoreFactor = 1f / pageZoom
            if (restoreFactor.isFinite() && restoreFactor > 0f) {
                zoomBy(restoreFactor)
            }
        }
        pinchActive = false
        pageZoom = 1f
        panStarted = false
        lastPanX = 0f
        lastPanY = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Feed the detector first, then explicitly own multi-touch so the
        // native WebView gesture cannot fight with our custom pinch handling.
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastPanX = event.x
                lastPanY = event.y
                panStarted = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    panStarted = false
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                if (!pinchActive && pageZoom > 1.01f && event.pointerCount == 1) {
                    val dx = event.x - lastPanX
                    val dy = event.y - lastPanY
                    if (!panStarted && (dx * dx + dy * dy) > 9f) {
                        panStarted = true
                    }
                    if (panStarted) {
                        scrollBy((-dx).toInt(), (-dy).toInt())
                        lastPanX = event.x
                        lastPanY = event.y
                        return true
                    }
                }

                if (event.pointerCount == 1) {
                    lastPanX = event.x
                    lastPanY = event.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                panStarted = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                panStarted = false
                pinchActive = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.onTouchEvent(event)
    }
}
