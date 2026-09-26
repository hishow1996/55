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

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinchActive = detector.pointerCount >= 2
                return pinchActive
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!pinchActive || detector.pointerCount < 2) return false

                // Slightly amplify the native scale delta so a normal pinch
                // reaches the expected zoom level with less finger travel.
                // The exponent keeps small movements smooth while avoiding
                // aggressive jumps on larger pinch gestures.
                val amplified = detector.scaleFactor.toDouble().coerceIn(0.5, 2.0).pow(1.20)
                if (amplified.isFinite() && amplified > 0.0 && amplified != 1.0) {
                    zoomBy(amplified.toFloat())
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        // Let WebView continue receiving the complete gesture stream so normal
        // one-finger scrolling, links, text selection and page interaction
        // remain unchanged.
        return super.onTouchEvent(event)
    }
}
