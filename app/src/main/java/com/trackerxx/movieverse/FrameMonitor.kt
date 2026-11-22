package com.trackerxx.movieverse

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView

/**
 * TEMPORARY diagnostic: a small box at the bottom-left shows, once a second, how many frames were drawn, how many were slow,
 * and where the time of the slow frames went. Scroll the list and read the box (or send a screenshot).
 *   anim   = Compose recomposition (runs in the animation callback)
 *   layout = measure + layout
 *   draw   = recording the draw commands
 *   sync   = uploading bitmaps (posters) to the GPU
 *   cmd/swap/gpu = GPU side
 */
object FrameMonitor {
    private var tv: TextView? = null
    private var frames = 0
    private var jank = 0
    private var worst = 0L
    private var gpuSum = 0L
    private val sum = LongArray(8)
    private var t0 = 0L

    fun attach(a: Activity) {
        if (Build.VERSION.SDK_INT < 24) return
        val box = TextView(a)
        box.textSize = 10f
        box.typeface = Typeface.MONOSPACE
        box.setTextColor(Color.WHITE)
        box.setBackgroundColor(0xB0000000.toInt())
        box.setPadding(10, 6, 10, 6)
        box.text = "scroll the list to measure"
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
        lp.bottomMargin = 160
        (a.window.decorView as ViewGroup).addView(box, lp)
        tv = box
        t0 = SystemClock.uptimeMillis()
        a.window.addOnFrameMetricsAvailableListener(Window.OnFrameMetricsAvailableListener { _, fm, _ ->
            val total = fm.getMetric(FrameMetrics.TOTAL_DURATION)
            val limit = if (Build.VERSION.SDK_INT >= 31) fm.getMetric(FrameMetrics.DEADLINE) else 16_700_000L
            frames++
            if (total > limit) {
                jank++
                if (total > worst) worst = total
                sum[0] += fm.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
                sum[1] += fm.getMetric(FrameMetrics.ANIMATION_DURATION)
                sum[2] += fm.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                sum[3] += fm.getMetric(FrameMetrics.DRAW_DURATION)
                sum[4] += fm.getMetric(FrameMetrics.SYNC_DURATION)
                sum[5] += fm.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
                sum[6] += fm.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
                sum[7] += total
                if (Build.VERSION.SDK_INT >= 31) gpuSum += fm.getMetric(FrameMetrics.GPU_DURATION)
            }
            val now = SystemClock.uptimeMillis()
            if (now - t0 >= 1000L && frames >= 5) {
                val n = if (jank == 0) 1 else jank
                fun ms(v: Long) = String.format("%.1f", v / 1_000_000.0 / n)
                val line2 = if (jank == 0) "no slow frames" else
                    "slow-frame avg ms: in " + ms(sum[0]) + " anim " + ms(sum[1]) + " layout " + ms(sum[2]) + " draw " + ms(sum[3]) +
                        "\nsync " + ms(sum[4]) + " cmd " + ms(sum[5]) + " swap " + ms(sum[6]) + " gpu " + ms(gpuSum) + " total " + ms(sum[7])
                tv?.text = "frames " + frames + "  slow " + jank + "  worst " + String.format("%.0f", worst / 1_000_000.0) + "ms\n" + line2
                frames = 0; jank = 0; worst = 0L; gpuSum = 0L
                for (i in sum.indices) sum[i] = 0L
                t0 = now
            }
        }, Handler(Looper.getMainLooper()))
    }
}
