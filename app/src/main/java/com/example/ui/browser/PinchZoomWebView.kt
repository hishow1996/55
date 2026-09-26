package com.example.ui.browser

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.webkit.WebView
import kotlin.math.pow

/**
 * WebView with explicit two-finger pinch zoom.
 *
 * Native WebView zoom controls are disabled so double-tap and the browser's
 * legacy zoom gesture cannot unexpectedly change the page scale. Zooming is
 * driven only by ScaleGestureDetector and WebView.zoomBy().
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
                pinchActive = detector.currentSpan >= 24f
                return pinchActive
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!pinchActive) return false

                // Slightly amplify the native scale delta so a normal pinch
                // reaches the expected zoom level with less finger travel.
                // The exponent keeps small movements smooth while avoiding
                // aggressive jumps on larger pinch gestures.
                val amplified = detector.scaleFactor.toDouble().coerceIn(0.5, 2.0).pow(1.20)
                if (amplified.isFinite() && amplified > 0.0 && amplified != 1.0) {
                    zoomBy(amplified.toFloat())
                    pageZoom = (pageZoom * amplified.toFloat()).coerceIn(0.5f, 5f)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                pinchActive = false
            }
        }
    )

    init {
        // We implement pinch zoom ourselves. This prevents WebView's legacy
        // built-in zoom/double-tap behavior from competing with the detector.
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
    }

    /**
     * Reset only the zoom state owned by this wrapper.
     *
     * Desktop mode changes WebView's initial scale independently through
     * WebSettings. Keeping the old gesture scale here would make a later
     * one-finger pan start unexpectedly after switching modes.
     */
    fun resetGestureZoomState() {
        pinchActive = false
        pageZoom = 1f
        panStarted = false
        lastPanX = 0f
        lastPanY = 0f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastPanX = event.x
                lastPanY = event.y
                panStarted = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pinchActive && pageZoom > 1.01f && event.pointerCount == 1) {
                    val dx = event.x - lastPanX
                    val dy = event.y - lastPanY
                    if (!panStarted && (dx * dx + dy * dy) > 16f) {
                        panStarted = true
                    }
                    if (panStarted) {
                        scrollBy((-dx).toInt(), (-dy).toInt())
                        lastPanX = event.x
                        lastPanY = event.y
                        return true
                    }
                }
                if (event.pointerCount >= 2) {
                    lastPanX = event.x
                    lastPanY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                panStarted = false
            }
        }

        return super.onTouchEvent(event)
    }
}
