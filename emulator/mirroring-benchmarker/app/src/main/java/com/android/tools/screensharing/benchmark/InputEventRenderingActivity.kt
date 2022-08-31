/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.screensharing.benchmark

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.descendants
import androidx.core.view.updateMargins
import com.android.tools.screensharing.benchmark.databinding.ActivityFullscreenBinding
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * An activity that displays and encodes touches and some other inputs in its video output.
 * The intended use is to analyze the performance of the end-to-end device mirroring in
 * Android Studio by tracking how long it takes an input sent from Studio to make it all the
 * way to the device and for the resulting video from the device to make it back to Studio.
 */
class InputEventRenderingActivity : AppCompatActivity() {
  private val benchmarkUiVisible: AtomicBoolean = AtomicBoolean()

  private lateinit var binding: ActivityFullscreenBinding
  private lateinit var objectTrackingView: FrameLayout
  private lateinit var rick: ImageView
  private lateinit var x: TextView
  private lateinit var y: TextView
  private lateinit var noiseBitmapView: ImageView
  private lateinit var frameLatency: TextView

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    hideSystemBars()
    binding = ActivityFullscreenBinding.inflate(layoutInflater)
    setContentView(binding.root)

    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    noiseBitmapView = binding.noiseBitmap
    x = binding.x
    y = binding.y
    objectTrackingView = binding.objectTracking
    rick = binding.rick
    frameLatency = binding.frameLatency
  }

  override fun onTouchEvent(event: MotionEvent): Boolean = processEvent(event)
  override fun onGenericMotionEvent(event: MotionEvent): Boolean = processEvent(event)
  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = processKey(keyCode)

  private fun processEvent(event: MotionEvent) : Boolean {
    makeBenchmarkUiVisible()
    Point(event.rawX.toInt(), event.rawY.toInt()).let {
      colorEncodeAndDisplay(it)
      val activitySize = Size(binding.root.width, binding.root.height)
      val objectTrackingViewSize = Size(objectTrackingView.width, objectTrackingView.height)
      moveRickTo(it.scale(activitySize, objectTrackingViewSize))
    }
    makeSomeNoise()
    displayFrameLatency((SystemClock.uptimeMillis() - event.eventTime).toInt())
    return true
  }

  private fun processKey(keyCode: Int) : Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP -> showTouchableArea()
      else -> return false
    }
    return true
  }

  private fun showTouchableArea() {
    if (benchmarkUiVisible.compareAndSet(true, false)) {
      Log.d(TAG, "Showing touchable area for benchmarking.")
      binding.root.descendants.forEach { it.visibility = View.INVISIBLE }
    }
  }

  private fun makeBenchmarkUiVisible() {
    if (benchmarkUiVisible.compareAndSet(false, true)) {
      Log.d(TAG, "Showing benchmarking UI to begin benchmarking.")
      binding.root.descendants.forEach { it.visibility = View.VISIBLE }
    }
  }

  /**
   * Encodes a [Point] as two colors on the display.
   *
   * Also displays the value encoded as well as the color to which it has been encoded
   * for diagnostic purposes.
   */
  @SuppressLint("SetTextI18n")
  private fun colorEncodeAndDisplay(p: Point) {
    @ColorInt val xColor: Int = p.x.toColor(BITS_PER_CHANNEL)
    @ColorInt val yColor = p.y.toColor(BITS_PER_CHANNEL)
    x.setBackgroundColor(xColor)
    y.setBackgroundColor(yColor)
    x.text = "${p.x} ${xColor.toHexColorString()}"
    x.setTextColor(xColor.contrastingColor())
    y.text = "${p.y} ${yColor.toHexColorString()}"
    y.setTextColor(yColor.contrastingColor())
  }

  /** Moves the draggable target to the given [Point] within the [objectTrackingView]. */
  private fun moveRickTo(p: Point) {
    val layoutParams: MarginLayoutParams = rick.layoutParams as MarginLayoutParams
    layoutParams.updateMargins(left = p.x - rick.width / 2, top = p.y - rick.height / 2)
    rick.visibility = View.VISIBLE
    rick.layoutParams = layoutParams
    rick.invalidate()
  }

  /** Creates noise in the [noiseBitmapView] to make the encoder's job a little harder. */
  private fun makeSomeNoise() {
    val bitmap = getOrInitializeNoiseBitmap()
    (0 until bitmap.width).forEach { x ->
      (0 until bitmap.height).forEach { y -> bitmap.setPixel(x, y, randomColor()) }
    }
    noiseBitmapView.invalidate()
  }

  /** Color-encodes and displays the latency associated with producing this frame. */
  @SuppressLint("SetTextI18n")
  private fun displayFrameLatency(latency: Int) {
    val latencyColors = LATENCY_COLORS[latency] ?: OUT_OF_BOUNDS_LATENCY_COLORS
    frameLatency.setBackgroundColor(latencyColors.first)
    frameLatency.setTextColor(latencyColors.second)
    frameLatency.text = "$latency ${latencyColors.first.toHexColorString()}"
  }

  private fun getOrInitializeNoiseBitmap() : Bitmap {
    val drawable = noiseBitmapView.drawable
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      return drawable.bitmap
    }

    var numXPixels = NOISE_BITMAP_SIZE
    var numYPixels = NOISE_BITMAP_SIZE
    if (noiseBitmapView.width > noiseBitmapView.height) {
      numYPixels =
        (NOISE_BITMAP_SIZE * noiseBitmapView.height / noiseBitmapView.width.toDouble()).roundToInt()
    }
    if (noiseBitmapView.height > noiseBitmapView.width) {
      numXPixels =
        (NOISE_BITMAP_SIZE * noiseBitmapView.width / noiseBitmapView.height.toDouble()).roundToInt()
    }
    val bitmap = Bitmap.createBitmap(numXPixels, numYPixels, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(0xfffffff)
    noiseBitmapView.setImageBitmap(bitmap)
    return bitmap
  }

  private fun hideSystemBars() {
    ViewCompat.getWindowInsetsController(window.decorView)?.let {
      // Configure the behavior of the hidden system bars
      it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      // Hide both the status bar and the navigation bar
      it.hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  companion object {
    private const val TAG = "DMBench.Render"
    private const val MAX_COLOR_BITS = 8
    private const val BITS_PER_CHANNEL = 4
    private const val NOISE_BITMAP_SIZE = 10

    // Precompute all the latency colors so it doesn't affect our latency measurement.
    private val LATENCY_COLORS: Map<Int, Pair<Int, Int>> = (0 until (1 shl 6)).associateWith {
      val bgColor = it.toColor(2)
      bgColor to bgColor.contrastingColor()
    }

    private val OUT_OF_BOUNDS_LATENCY_COLORS: Pair<Int, Int> = Color.WHITE to Color.BLACK

    /**
     * Encodes a positive integer into a color using at most [bitsPerChannel] bits per channel.
     *
     * Because the alpha channel is not used, the integer must always fit in 24 bits, i.e. be less
     * than or equal to 2^15-1.
     *
     * Will throw an [IllegalArgumentException] if the integer is negative, if [bitsPerChannel] is
     * more than 8, or if the integer cannot be encoded in the number of available bits.
     */
    @ColorInt
    private fun Int.toColor(bitsPerChannel: Int): Int {
      require(this >= 0) { "Cannot encode a negative integer" }
      require(bitsPerChannel <= MAX_COLOR_BITS) {
        "Cannot use more than $MAX_COLOR_BITS bits per channel for color."
      }
      require(1 shl (3 * bitsPerChannel) > this) {
        "Cannot encode $this in ${bitsPerChannel * 3} bits."
      }
      val bitmask = (1 shl bitsPerChannel) - 1
      val emptyBits = 8 - bitsPerChannel
      val r = ((this shr (bitsPerChannel * 2)) and bitmask) shl emptyBits
      val g = ((this shr bitsPerChannel) and bitmask) shl emptyBits
      val b = (this and bitmask) shl emptyBits
      return Color.rgb(r, g, b)
    }

    /** Returns a contrasting color for the [ColorInt]. */
    @ColorInt
    private fun @receiver:ColorInt Int.contrastingColor(): Int {
      val r = Color.red(this)
      val g = Color.green(this)
      val b = Color.blue(this)
      return if (r * 0.299 + g * 0.587 + b * 0.114 > 128) Color.BLACK else Color.WHITE
    }

    /** Pretty-prints a [ColorInt] as a hex string (e.g. #123ABC) */
    private fun @receiver:ColorInt Int.toHexColorString(): String {
      return String.format("#%06X", (0xFFFFFF and this))
    }

    @ColorInt
    private fun randomColor(): Int =
      Color.rgb(Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

    /**
     * Scales a point from the source space to the destination space.
     */
    private fun Point.scale(src: Size, dst: Size): Point {
      val scaledX = x * dst.width / src.width.toDouble()
      val scaledY = y * dst.height / src.height.toDouble()
      return Point(scaledX.roundToInt(), scaledY.roundToInt())
    }
  }
}
