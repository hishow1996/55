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
 * Native zoom UI is disabled, but WebView zoom support itself remains enabled
 * because WebView.zoomBy() requires zoom support on some Android System WebView
 * versions. Zooming is driven only by ScaleGestureDetector.
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
                if (pinchActive) parent?.requestDisallowInterceptTouchEvent(true)
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
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    )

    init {
        // We implement pinch zoom ourselves. This prevents WebView's legacy
        // built-in zoom/double-tap behavior from competing with the detector.
        // zoomBy() can be a no-op when supportZoom is disabled on older
        // Android System WebView builds. Keep the engine capable of zooming,
        // while hiding all legacy zoom controls/gestures from the user.
        settings.setSupportZoom(true)
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
        // Feed the detector first, then always let WebView receive the same
        // stream. This keeps links/text/video controls functional while the
        // custom pinch handler consumes only the scale/pan part it owns.
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
                    // Keep multi-touch owned by the custom detector so native
                    // WebView zoom cannot run in parallel.
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                } else if (!pinchActive && pageZoom > 1.01f && event.pointerCount == 1) {
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
                // Do not clear pinchActive here: ScaleGestureDetector may still
                // have one final scale callback before it ends the gesture.
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
