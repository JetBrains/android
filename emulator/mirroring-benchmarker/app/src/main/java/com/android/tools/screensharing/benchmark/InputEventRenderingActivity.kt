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
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.descendants
import androidx.core.view.updateMargins
import com.android.tools.screensharing.benchmark.databinding.ActivityFullscreenBinding
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "DMBench.Render"
private const val NOISE_BITMAP_SIZE = 10
private val NUMERIC_KEYCODES = KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9

/**
 * Scales a point from the source space to the destination space.
 */
private fun Point.scale(src: Size, dst: Size): Point {
  val scaledX = x * dst.width / src.width.toDouble()
  val scaledY = y * dst.height / src.height.toDouble()
  return Point(scaledX.roundToInt(), scaledY.roundToInt())
}

/**
 * An activity that displays and encodes touches and some other inputs in its video output.
 * The intended use is to analyze the performance of the end-to-end device mirroring in
 * Android Studio by tracking how long it takes an input sent from Studio to make it all the
 * way to the device and for the resulting video from the device to make it back to Studio.
 */
class InputEventRenderingActivity : AppCompatActivity() {
  private val benchmarkUiVisible = AtomicBoolean()
  private val textVisible = AtomicBoolean()
  private val visibilityHandler = Handler(Looper.getMainLooper())
  private val makeTextVisible = Runnable { setTextVisible(true) }

  private var stringBuilder = StringBuilder()
  private var lastToast: Toast? = null
  private lateinit var binding: ActivityFullscreenBinding
  private lateinit var objectTrackingView: FrameLayout
  private lateinit var rick: ImageView
  private lateinit var x: EncodedIntegerView
  private lateinit var y: EncodedIntegerView
  private lateinit var coordinates: TextView
  private lateinit var noiseBitmapView: ImageView
  private lateinit var frameLatency: EncodedIntegerView
  private lateinit var frameLatencyText: TextView

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
    coordinates = binding.coordinates
    objectTrackingView = binding.objectTracking
    rick = binding.rick
    frameLatency = binding.frameLatency
    frameLatencyText = binding.frameLatencyText
  }

  override fun onTouchEvent(event: MotionEvent): Boolean = processEvent(event)
  override fun onGenericMotionEvent(event: MotionEvent): Boolean = processEvent(event)
  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = processKey(keyCode)

  private fun setTextVisible(visibility: Boolean) {
    if (textVisible.compareAndSet(!visibility, visibility)) {
      val targetVisibility = if (visibility) View.VISIBLE else View.INVISIBLE
      positionCoordinates()
      coordinates.visibility = targetVisibility
      frameLatencyText.visibility = targetVisibility
      x.setTextVisible(visibility)
      y.setTextVisible(visibility)
      frameLatency.setTextVisible(visibility)
    }
  }

  @SuppressLint("SetTextI18n")
  private fun processEvent(event: MotionEvent): Boolean {
    makeBenchmarkUiVisible()
    setTextVisible(false)
    with (visibilityHandler) {
      removeCallbacks(makeTextVisible)
      postDelayed(makeTextVisible, resources.getInteger(R.integer.delay_before_showing_text_ms).toLong())
    }
    Point(event.rawX.toInt(), event.rawY.toInt()).let {
      x.displayAsColors(it.x)
      y.displayAsColors(it.y)
      val activitySize = Size(binding.root.width, binding.root.height)
      val objectTrackingViewSize = Size(objectTrackingView.width, objectTrackingView.height)
      val scaled = it.scale(activitySize, objectTrackingViewSize)
      coordinates.text = "(${it.x},${it.y})"
      moveRickTo(scaled)
    }
    makeSomeNoise()
    (SystemClock.uptimeMillis() - event.eventTime).toInt().let {
      frameLatency.displayAsColors(it)
      frameLatencyText.text = "$it ms"
    }
    return true
  }

  private fun processKey(keyCode: Int): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> {
        stringBuilder.clear()
        showTouchableArea()
      }
      KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> manuallyDecrementBitsPerChannel()
      in NUMERIC_KEYCODES -> stringBuilder.append(NUMERIC_KEYCODES.indexOf(keyCode))
      KeyEvent.KEYCODE_COMMA -> stringBuilder.append(',')
      KeyEvent.KEYCODE_ENTER -> if (setBitConfigFromStringBuilder()) showTouchableArea()
      else -> return false
    }
    return true
  }

  /** Decrements the number of bits per channel (modulo 8). */
  private fun manuallyDecrementBitsPerChannel() {
    setMaxBitsFromDeviceDisplaySpecs()
    // Use the least value
    val oldBitsPerChannel = min(min(x.bitsPerChannel, y.bitsPerChannel), frameLatency.bitsPerChannel)
    val bitsPerChannel = (oldBitsPerChannel - 1) and 7
    setBitsPerChannel(bitsPerChannel)
    lastToast?.cancel()
    lastToast = makeText(this, "Bits per channel set to $bitsPerChannel", Toast.LENGTH_SHORT).also(Toast::show)
  }

  private fun setBitsPerChannel(bitsPerChannel: Int) {
    x.bitsPerChannel = bitsPerChannel
    y.bitsPerChannel = bitsPerChannel
    frameLatency.bitsPerChannel = bitsPerChannel
  }

  /** Sets the maximum number of bits based on the largest display dimension that might need to be encoded. */
  private fun setMaxBitsFromDeviceDisplaySpecs() {
    setMaxBits(ceil(log2(max(binding.root.width, binding.root.height).toDouble())).roundToInt())
  }

  private fun setMaxBits(maxBits: Int) {
    x.maxBits = maxBits
    y.maxBits = maxBits
  }

  private fun setMaxLatencyBits(maxLatencyBits: Int) {
    frameLatency.maxBits = maxLatencyBits
  }

  private fun setBitConfigFromStringBuilder(): Boolean {
    var succeeded = false
    try {
      Log.d(TAG, "Setting bit config from string: $stringBuilder")
      val numbers = stringBuilder.split(',').map(String::toInt)
      if (numbers.size == 3 && numbers[0] > 0 && numbers[1] > 0 && numbers[2] >= 0) {
        val (maxBits, maxLatencyBits, bitsPerChannel) = numbers
        setMaxBits(maxBits)
        setMaxLatencyBits(maxLatencyBits)
        setBitsPerChannel(bitsPerChannel)
        Log.d(TAG, "maxBits: $maxBits, maxLatencyBits: $maxLatencyBits, bitsPerChannel: $bitsPerChannel")
        succeeded = true
      }
    } catch (e: NumberFormatException) {
      Log.e(TAG, "Failed to parse number", e)
    }
    stringBuilder.clear()
    return succeeded
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
      binding.root.descendants.forEach {
        if (it !== coordinates && it !== frameLatencyText) it.visibility = View.VISIBLE
      }
    }
  }

  /** Moves the draggable target to the given [Point] within the [objectTrackingView]. */
  private fun moveRickTo(p: Point) {
    val layoutParams: MarginLayoutParams = rick.layoutParams as MarginLayoutParams
    layoutParams.updateMargins(left = p.x - rick.width / 2, top = p.y - rick.height / 2)
    rick.visibility = View.VISIBLE
    rick.layoutParams = layoutParams
    rick.invalidate()
  }

  /** Position the coordinates display next to the draggable target. */
  private fun positionCoordinates() {
    coordinates.measure(objectTrackingView.measuredWidth, objectTrackingView.measuredHeight)
    val layoutParams: MarginLayoutParams = coordinates.layoutParams as MarginLayoutParams
    // If Rick is too far to the right, put the coordinates to the left.
    val coordinatesOnLeft = coordinates.measuredWidth + rick.right > objectTrackingView.right
    val leftMargin = if (coordinatesOnLeft) rick.left - coordinates.measuredWidth else rick.right
    val topMargin = rick.top.coerceIn(0, objectTrackingView.height - coordinates.measuredHeight)
    layoutParams.updateMargins(left = leftMargin, top = topMargin)
    coordinates.layoutParams = layoutParams
    coordinates.invalidate()
  }

  /** Creates noise in the [noiseBitmapView] to make the encoder's job a little harder. */
  private fun makeSomeNoise() {
    val bitmap = getOrInitializeNoiseBitmap()
    (0 until bitmap.width).forEach { x ->
      (0 until bitmap.height).forEach { y -> bitmap.setPixel(x, y, randomColor()) }
    }
    noiseBitmapView.invalidate()
  }

  private fun getOrInitializeNoiseBitmap(): Bitmap {
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
}
