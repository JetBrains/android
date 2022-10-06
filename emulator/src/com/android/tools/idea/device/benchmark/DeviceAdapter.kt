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
package com.android.tools.idea.device.benchmark

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.device.benchmark.Benchmarker.Adapter
import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.bottom
import com.android.tools.idea.emulator.right
import com.android.tools.idea.emulator.rotatedByQuadrants
import com.android.tools.idea.emulator.scaled
import com.android.tools.idea.emulator.scaledUnbiased
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val MAX_BECOME_READY_DURATION = 2.seconds
private val LOG = Logger.getInstance(DeviceAdapter::class.java)

private fun Int.isEven() = this % 2 == 0

/** Computes values in between [this.start] and [this.end] at regular intervals representing the centers of [numValues] equal ranges. */
private fun IntRange.sampleValues(numValues: Int) : List<Int> {
  val spacing = ((endInclusive - start) / numValues.toDouble())
  return List(numValues) { start + (spacing * (it + 0.5)).roundToInt() }
}

private fun Rectangle.fractionalX(fraction: Double) : Int = (x + width * fraction).roundToInt().coerceIn(x until x + width)

private fun Rectangle.fractionalY(fraction: Double) : Int = (y + height * fraction).roundToInt().coerceIn(y until y + height)

/** Gets the range of Y values within the [Rectangle] in which we encode frame latency. */
private fun Rectangle.latencyEncodingYRange(): IntRange = fractionalY(0.85) until bottom

/** Gets the center of the range of X values within the [Rectangle] in which we encode frame latency. */
private fun Rectangle.latencyEncodingX(): Int = fractionalX(0.9)

/** Gets the center of the range of Y values within the [Rectangle] in which we encode the first integer. */
private fun Rectangle.integer1EncodingX(): Int = fractionalX(0.25)

/** Gets the center of the range of Y values within the [Rectangle] in which we encode the second integer. */
private fun Rectangle.integer2EncodingX(): Int = fractionalX(0.75)

/** Gets the range of Y values within the [Rectangle] in which we encode integer values. */
private fun Rectangle.integerEncodingYRange(): IntRange = y..fractionalY(0.5)

private fun Color.channels(): List<Int> = listOf(red, green, blue)

private fun Color.distanceFrom(other: Color) = channels().zip(other.channels()).sumOf { abs(it.first - it.second) }

fun Color.isReddish() = red > 0xE0 && green < 0x1F && blue < 0x1F

fun Color.isGreenish() = red < 0x1F && green > 0xE0 && blue < 0x1F

fun Color.isBluish() = red < 0x1F && green < 0x1F && blue > 0xE0

private fun AbstractDisplayView.press(keyCode: Int) {
  keyInput(keyCode, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_PRESSED)
}

private fun AbstractDisplayView.type(keyCode: Int, keyChar: Char) {
  keyInput(KeyEvent.VK_UNDEFINED, keyChar, KeyEvent.KEY_TYPED)
}

private fun AbstractDisplayView.keyInput(keyCode: Int, keyChar:Char, id: Int) {
  UIUtil.invokeLaterIfNeeded {
    dispatchEvent(KeyEvent(this, id, System.currentTimeMillis(), 0, keyCode, keyChar))
  }
}

private fun AbstractDisplayView.typeNumber(n: Int) {
  n.toString().forEach { type(KeyEvent.VK_0 + it.digitToInt(), it) }
}

private fun AbstractDisplayView.click(location: Point, mouseEventType: Int = MouseEvent.MOUSE_PRESSED) {
  UIUtil.invokeLaterIfNeeded {
    dispatchEvent(
      MouseEvent(this,
                 mouseEventType,
                 System.currentTimeMillis(),
                 0,
                 location.x,
                 location.y,
                 1,
                 false,
                 MouseEvent.BUTTON1))
  }
}

/** Extracts the [Color] of the pixel at ([x], [y]) in the image. */
private fun BufferedImage.extract(x: Int, y: Int): Color = Color(getRGB(x,y))

/** Determines whether the image holds the initialization frame pattern, a gradient from red to green to blue. */
private fun BufferedImage.isInitializationFrame(): Boolean {
  val center = Point(width / 2, height / 2)

  val extremesCorrect = extract(0, center.y).isReddish() &&
                        extract(center.x, center.y).isGreenish() &&
                        extract(width - 1, center.y).isBluish()

  // Use a local function so we don't bother with this if the first part fails. This just tells us
  // if a sampling of pixels are the right (color) distance from one another.
  fun smooth() : Boolean {
    val expectedTotalDistance = 255 * 4
    val samplePoints = width / 10
    val expectedAverageDistance = expectedTotalDistance / (samplePoints + 1).toDouble()

    return (1 until width).sampleValues(samplePoints).windowed(2).all {
      extract(it[0], center.y).distanceFrom(extract(it[1], center.y)) < 2 * expectedAverageDistance
    }
  }

  return extremesCorrect && smooth()
}

/**
 * Finds a [Rectangle] of touchable area of the screen, which should be colored green, or `null` if no such
 * touchable area can be found.
 *
 * This method assumes that the marked touchable area overlaps the center of the screen. This method also
 * returns the [Rectangle] in image coordinates.
 */
private fun BufferedImage.findTouchableArea(): Rectangle? {
  val center = Point(width / 2, height / 2)

  val left = (0 until width).find { extract(it, center.y).isGreenish() } ?: return null
  val right = (0 until width).reversed().find { extract(it, center.y).isGreenish() } ?: return null
  val top = (0 until height).find { extract(center.x, it).isGreenish() } ?: return null
  val bottom = (0 until height).reversed().find { extract(center.x, it).isGreenish() } ?: return null

  val width = right - left + 1
  val height = bottom - top + 1
  return Rectangle(left, top, width, height).also { LOG.info("Found touchable area: $it") }
}

/**
 * Returns a [Sequence] of [Point]s scribbling sinusoidally back and forth across the [Rectangle].
 */
private fun Rectangle.scribble(numPoints: Int, step: Int, spikiness: Int): Sequence<Point> = sequence {
  // Each row is width points, so make sure we have enough rows.
  val numRows = if (numPoints > (width * height) / step) height else ((numPoints * step + width - 1) / width).coerceIn(1, height)
  val targetStripeHeight = height / numRows.toDouble()
  val p = Point()
  // Scaling factor to compress x coordinates, so we go from [0, 1) to [0, 2πn)
  val xScalingFactor = 2 * Math.PI * spikiness
  repeat(numRows) { rowIdx ->
    val stripeStart = y + (targetStripeHeight * rowIdx).roundToInt().coerceIn(0, bottom)
    val stripeEnd = y + (targetStripeHeight * (rowIdx + 1)).roundToInt().coerceIn(0, bottom)
    val verticalMidpoint = (stripeEnd + stripeStart) / 2
    val xCoordinates = if (rowIdx.isEven()) x until right else (x until right).reversed()
    xCoordinates.forEach { xCoordinate ->
      p.x = xCoordinate
      // How far along are we in [0, 1) ?
      val normalizedX = (xCoordinate - x) / width.toDouble()
      val yDisplacement = sin(xScalingFactor * normalizedX) * targetStripeHeight / 2
      p.y = (verticalMidpoint - yDisplacement).roundToInt().coerceIn(stripeStart, stripeEnd - 1)
      yield(Point(p))
    }
  }
}.chunked(step).map{ it.first() }.take(numPoints)

@OptIn(ExperimentalTime::class)
internal class DeviceAdapter (
  private val project: Project,
  private val target: DeviceMirroringBenchmarkTarget,
  private val bitsPerChannel: Int = 0,
  private val latencyBits: Int = 6,
  private val maxTouches: Int = 10_000,
  private val step: Int = 1,
  private val spikiness: Int = 1,
  private val readyIndicator: ProgressIndicator? = null,
  private val timeSource: TimeSource = TimeSource.Monotonic,
  private val installer: MirroringBenchmarkerAppInstaller = MirroringBenchmarkerAppInstaller(project, target.serialNumber),
  private val coroutineScope: CoroutineScope = AndroidCoroutineScope(target.view),
  ) : Adapter<Point>, AbstractDisplayView.FrameListener {

  private val deviceDisplaySize: Dimension by target.view::deviceDisplaySize
  private val maxBits: Int = ceil(log2(max(deviceDisplaySize.width, deviceDisplaySize.height).toDouble())).roundToInt()
  private val numRegionsPerCoordinate = if (bitsPerChannel == 0) maxBits else (maxBits - 1) / (bitsPerChannel * 3) + 1
  private val numLatencyRegions = if (bitsPerChannel == 0) latencyBits else (latencyBits - 1) / (bitsPerChannel * 3) + 1

  @GuardedBy("this")
  private var appState = AppState.INITIALIZING

  @Volatile
  private lateinit var touchableArea: Rectangle
  @Volatile
  private lateinit var touchableImageArea: Rectangle
  @Volatile
  private lateinit var adapterCallbacks: Adapter.Callbacks<Point>
  @Volatile
  private lateinit var startedGettingReady: TimeMark

  private val pointsToTouch: Iterator<Point> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    touchableArea.scribble(maxTouches, step, spikiness).iterator()
  }
  private val numPointsToTouch by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    min(touchableArea.width * touchableArea.height, maxTouches)
  }

  private var lastPressed: Point? = null

  init {
    require(maxTouches > 0) { "Must specify a positive value for maxTouches!" }
    require(step > 0) { "Must specify a positive value for step!" }
    require(spikiness >= 0) { "Must specify a non-negative value for spikiness!" }
    require(bitsPerChannel in 0..8) { "Cannot extract $bitsPerChannel bits from a channel! Must be in [0,8]" }
  }

  @Synchronized
  override fun frameRendered(frameNumber: Int, displayRectangle: Rectangle, displayOrientationQuadrants: Int, displayImage: BufferedImage) {
    when (appState) {
      AppState.INITIALIZING -> {
        if (startedGettingReady.elapsedNow() > MAX_BECOME_READY_DURATION) {
          adapterCallbacks.onFailedToBecomeReady("Failed to detect initialized app within $MAX_BECOME_READY_DURATION")
          return
        }
        if (displayImage.isInitializationFrame()) {
          keyConfigIntoApp()
          appState = AppState.DISPLAYING_TOUCHABLE_AREA
        }
      }

      AppState.DISPLAYING_TOUCHABLE_AREA -> {
        if (displayImage.isInitializationFrame()) return
        if (startedGettingReady.elapsedNow() > MAX_BECOME_READY_DURATION) {
          adapterCallbacks.onFailedToBecomeReady("Failed to find touchable area within $MAX_BECOME_READY_DURATION")
          return
        }
        displayImage.findTouchableAreas()?.let {
          touchableImageArea = it.first
          touchableArea = it.second
          appState = AppState.READY
          readyIndicator?.apply {
            isIndeterminate = false
            fraction = 1.0
          }
          adapterCallbacks.onReady()
        }
      }

      AppState.READY -> processFrame(displayImage)
    }
  }

  override fun setCallbacks(callbacks: Adapter.Callbacks<Point>) {
    this.adapterCallbacks = callbacks
  }

  override fun inputs(): Iterator<Point> = pointsToTouch

  override fun numInputs(): Int = numPointsToTouch

  override fun dispatch(input: Point) {
    lastPressed = input
    target.view.click(input)
  }

  override fun ready() {
    readyIndicator?.isIndeterminate = true
    coroutineScope.launch {
      if (!installer.installBenchmarkingApp(readyIndicator)) {
        adapterCallbacks.onFailedToBecomeReady("Could not install benchmarking app.")
        return@launch
      }
      if (!installer.launchBenchmarkingApp(readyIndicator)) {
        adapterCallbacks.onFailedToBecomeReady("Could not launch benchmarking app.")
        return@launch
      }
      startedGettingReady = timeSource.markNow()
      target.view.addFrameListener(this@DeviceAdapter)
      target.view.repaint()
    }
  }

  override fun finalizeInputs() {
    lastPressed?.let { target.view.click(it, MouseEvent.MOUSE_RELEASED) }
  }

  override fun cleanUp() {
    target.view.removeFrameListener(this)
    coroutineScope.launch { installer.uninstallBenchmarkingApp() }
  }

  @Synchronized
  private fun keyConfigIntoApp() {
    with(target.view) {
      press(KeyEvent.VK_UP)
      typeNumber(maxBits)
      type(KeyEvent.VK_COMMA, ',')
      typeNumber(latencyBits)
      type(KeyEvent.VK_COMMA, ',')
      typeNumber(bitsPerChannel)
      press(KeyEvent.VK_ENTER)
    }
  }

  @Synchronized
  private fun processFrame(displayImage: BufferedImage) {
    val frameArrived = timeSource.markNow()
    val point = displayImage.decodeToPoint().toDisplayViewCoordinates() ?: return
    val frameLatency = displayImage.decodeLatency().milliseconds
    adapterCallbacks.inputReturned(point, frameArrived - frameLatency)
  }

  /** Returns the touchable area of the device in both image and display view coordinates. */
  private fun BufferedImage.findTouchableAreas(): Pair<Rectangle, Rectangle>? {
    val imageRectangle = findTouchableArea() ?: return null
    // Start with a nonexistent Rectangle.
    val displayViewRectangle = Rectangle(0, 0, -1, -1)
    // Get two opposite corners of the image rectangle.
    listOf(imageRectangle.location, Point(imageRectangle.right, imageRectangle.bottom))
      // Scale them to device coordinates.
      .map { it.scaledUnbiased(Dimension(width, height), target.view.deviceDisplaySize) }
      // Now transform them to the display view coordinates, if possible.
      .map { it.toDisplayViewCoordinates() ?: return null }
      // Now add each of these opposite points to the newly created Rectangle.
      .forEach(displayViewRectangle::add)
    LOG.info("Found touchable area in image: $imageRectangle. Converted to AbstractDisplayView coordinates: $displayViewRectangle")
    return imageRectangle to displayViewRectangle
  }

  /** Decode the frame to a [Point]. */
  private fun BufferedImage.decodeToPoint(): Point {
    val yRange = touchableImageArea.integerEncodingYRange()
    val x = readIntegerEncodedAt(touchableImageArea.integer1EncodingX(), yRange, numRegionsPerCoordinate, maxBits)
    val y = readIntegerEncodedAt(touchableImageArea.integer2EncodingX(), yRange, numRegionsPerCoordinate, maxBits)
    return Point(x, y)
  }

  /**
   * Reads an integer that is encoded as [numRegions] contiguous blocks of color. The blocks are located at
   * x coordinate [sampleX] and distributed across [yRange]. Only the most significant [totalBits] are used to
   * construct the integer.
   */
  private fun BufferedImage.readIntegerEncodedAt(sampleX: Int, yRange: IntRange, numRegions: Int, totalBits: Int): Int =
    yRange.sampleValues(numRegions).joinToString("") { extract(sampleX, it).decodeToBinaryString() }.take(totalBits).toInt(2)

  /** Turns this [Color] into a [String] of `1` and `0` characters by reading [bitsPerChannel] bits from each color channel. */
  private fun Color.decodeToBinaryString(): String {
    return when (bitsPerChannel) {
      0 -> if (red + green + blue > 382) "1" else "0"
      1 -> channels().joinToString("") { if (it > 127) "1" else "0" }
      else -> channels().joinToString("") { channel ->
        (channel * ((1 shl bitsPerChannel) - 1).toDouble() / 255).roundToInt().toString(2).padStart(bitsPerChannel, '0')
      }
    }
  }

  /** Get the latency associated with generating this frame. */
  private fun BufferedImage.decodeLatency(): Int {
    val latencyYRange = touchableImageArea.latencyEncodingYRange()
    return readIntegerEncodedAt(touchableImageArea.latencyEncodingX(), latencyYRange, numLatencyRegions, latencyBits)
  }

  private fun Point.toDisplayViewCoordinates(): Point? {
    val displayRectangle = target.view.displayRectangle ?: return null
    val imageSize = displayRectangle.size.rotatedByQuadrants(target.view.displayOrientationQuadrants)
    val p2 = scaledUnbiased(deviceDisplaySize, imageSize)
    val inverseScreenScale = 1.0 / target.view.screenScalingFactor
    val viewCoordinates = Point()
    when (target.view.displayOrientationQuadrants) {
      0 -> {
        viewCoordinates.x = (p2.x + displayRectangle.x).scaled(inverseScreenScale)
        viewCoordinates.y = (p2.y + displayRectangle.y).scaled(inverseScreenScale)
      }

      3 -> {  // Inverse of 1
        viewCoordinates.x = (displayRectangle.bottom - p2.y).scaled(inverseScreenScale)
        viewCoordinates.y = (p2.x + displayRectangle.x).scaled(inverseScreenScale)
      }

      2 -> {
        viewCoordinates.x = (displayRectangle.right - p2.x).scaled(inverseScreenScale)
        viewCoordinates.y = (displayRectangle.bottom - p2.y).scaled(inverseScreenScale)
      }

      else -> {  // 1, inverse of 3
        viewCoordinates.x = (p2.y + displayRectangle.y).scaled(inverseScreenScale)
        viewCoordinates.y = (displayRectangle.right - p2.x).scaled(inverseScreenScale)
      }
    }
    return viewCoordinates
  }

  private enum class AppState {
    INITIALIZING,
    DISPLAYING_TOUCHABLE_AREA,
    READY,
  }
}

data class DeviceMirroringBenchmarkTarget(val name: String, val serialNumber: String, val view: AbstractDisplayView)
