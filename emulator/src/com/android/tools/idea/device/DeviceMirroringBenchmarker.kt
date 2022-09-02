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
package com.android.tools.idea.device

import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.right
import com.android.tools.idea.emulator.bottom
import com.android.tools.idea.emulator.rotatedByQuadrants
import com.android.tools.idea.emulator.scaled
import com.android.tools.idea.emulator.scaledUnbiased
import com.android.tools.idea.util.fsm.StateMachine
import com.google.common.math.Quantiles
import com.intellij.openapi.diagnostic.Logger
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

typealias ProgressCallback = (Double, Double) -> Unit

/** Class that conducts a generic benchmarking operation for device mirroring. */
@OptIn(ExperimentalTime::class)
class DeviceMirroringBenchmarker(
    private val abstractDisplayView: AbstractDisplayView,
    touchRateHz: Int = 60,
    maxTouches: Int = 10_000,
    step: Int = 1,
    spikiness: Int = 1,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    timer: Timer = Timer(),
  ): AbstractDisplayView.FrameListener {

  private val frameDurationMillis: Long = (1000 / touchRateHz.toDouble()).roundToLong()
  private val deviceDisplaySize: Dimension by abstractDisplayView::deviceDisplaySize

  init {
    require(maxTouches > 0) { "Must specify a positive value for maxTouches!" }
    require(step > 0) { "Must specify a positive value for step!" }
    require(spikiness >= 0) { "Must specify a non-negative value for spikiness!" }
  }

  // ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌────────┐
  // │INITIAL├─►│FINDING├─►│SENDING├─►│WAITING├─►│COMPLETE│
  // └───┬───┘  └───┬───┘  └───┬───┘  └───┬───┘  └────────┘
  //     │          │          │          │
  //     │          │          │          │       ┌───────┐
  //     └──────────┴──────────┴──────────┴──────►│STOPPED│
  //                                              └───────┘
  // TODO(b/243841143): Convert to suspend function/coroutines.
  private val stateMachine = StateMachine.stateMachine(
    State.INITIALIZED, StateMachine.Config(logger = LOG, timeSource = timeSource)) {
    State.INITIALIZED.transitionsTo(State.FINDING_TOUCHABLE_AREA, State.STOPPED)
    State.FINDING_TOUCHABLE_AREA {
      transitionsTo(State.SENDING_TOUCHES, State.STOPPED)
      onEnter {
        abstractDisplayView.addFrameListener(this@DeviceMirroringBenchmarker)
        abstractDisplayView.repaint() // Make sure get the first frame.
      }
    }
    State.SENDING_TOUCHES {
      transitionsTo(State.WAITING_FOR_OUTSTANDING_TOUCHES, State.STOPPED)
      onEnter {
        timer.scheduleAtFixedRate(delay = 0, period = frameDurationMillis) {
          dispatchNextTouch()
        }
      }
      onExit { timer.cancel() }
    }
    State.WAITING_FOR_OUTSTANDING_TOUCHES.transitionsTo(State.STOPPED, State.COMPLETE)
    State.STOPPED.onEnter {
      abstractDisplayView.removeFrameListener(this@DeviceMirroringBenchmarker)
      onStoppedCallbacks.forEach { it() }
    }
    State.COMPLETE.onEnter {
      abstractDisplayView.removeFrameListener(this@DeviceMirroringBenchmarker)
      onStoppedCallbacks.forEach { it() }
      onCompleteCallbacks.forEach { it(BenchmarkResults(touchRoundTrips, computePercentiles())) }
    }
  }
  private var state by stateMachine::state

  private val onProgressCallbacks: MutableList<ProgressCallback> = mutableListOf()
  private val onCompleteCallbacks: MutableList<(BenchmarkResults) -> Unit> = mutableListOf()
  private val onStoppedCallbacks: MutableList<() -> Unit> = mutableListOf()

  @Volatile private lateinit var touchableArea: Rectangle
  private val pointsToTouch: Iterator<Point> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    touchableArea.scribble(maxTouches, step, spikiness).iterator()
  }
  private val numPointsToTouch by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    min(touchableArea.width * touchableArea.height, maxTouches)
  }
  private val outstandingTouches: MutableMap<Point, TimeMark> = LinkedHashMap()
  private val touchRoundTrips: MutableMap<Point, Duration> = mutableMapOf()

  @Synchronized
  fun addOnStoppedCallback(callback: () -> Unit) {
    onStoppedCallbacks.add(callback)
  }

  @Synchronized
  fun addOnCompleteCallback(callback: (BenchmarkResults) -> Unit) {
    onCompleteCallbacks.add(callback)
  }

  @Synchronized
  fun addOnProgressCallback(callback: (Double, Double) -> Unit) {
    onProgressCallbacks.add(callback)
  }

  @Synchronized
  fun start() {
    state = State.FINDING_TOUCHABLE_AREA
    abstractDisplayView.dispatchKey(AKEYCODE_VOLUME_UP)
  }

  @Synchronized
  fun stop() {
    state = State.STOPPED
  }

  @Synchronized
  private fun dispatchNextTouch() {
    if (pointsToTouch.hasNext()) {
      pointsToTouch.next().let {
        outstandingTouches[it] = timeSource.markNow()
        LOG.trace("Dispatching touch at $it.")
        abstractDisplayView.toDeviceDisplayCoordinates(it)?.let { converted ->
          abstractDisplayView.dispatchTouch(converted)
        }
        // TODO(b/243838958): Convert this to use the below code once we figure out why it causes a
        //  deadlock/race condition.
        // abstractDisplayView.dispatchEvent(
        //   MouseEvent(abstractDisplayView,
        //              MouseEvent.MOUSE_PRESSED,
        //              System.currentTimeMillis(),
        //              0,
        //              it.x,
        //              it.y,
        //              1,
        //              false,
        //              MouseEvent.BUTTON1))
      }
      if (!pointsToTouch.hasNext()) state = State.WAITING_FOR_OUTSTANDING_TOUCHES
    }
  }

  @Synchronized
  fun isDone(): Boolean = state == State.COMPLETE

  @Synchronized
  override fun frameRendered(frameNumber: Int, displayRectangle: Rectangle, displayOrientationQuadrants: Int, displayImage: BufferedImage) {
    when (state) {
      State.FINDING_TOUCHABLE_AREA -> {
        displayImage.findTouchableAreaInDisplayViewCoordinates()?.let {
          touchableArea = it
          state = State.SENDING_TOUCHES
          return
        }
        if (stateMachine.getDurationInCurrentState() > MAX_DURATION_FIND_TOUCHABLE_AREA) {
          LOG.warn("Unable to find touchable area within $MAX_DURATION_FIND_TOUCHABLE_AREA.")
          state = State.STOPPED
        }
      }
      State.SENDING_TOUCHES, State.WAITING_FOR_OUTSTANDING_TOUCHES -> processFrame(displayImage)
      else -> LOG.debug("Got frame for ${displayImage.decodeToPoint()} but not expecting touch. Ignoring.")
    }
  }

  @Synchronized
  private fun processFrame(displayImage: BufferedImage) {
    val frameArrived = timeSource.markNow()
    val point = displayImage.decodeToPoint().toDisplayViewCoordinates() ?: return
    val frameLatency = displayImage.decodeLatency().milliseconds
    onTouchReturned(point, frameArrived - frameLatency)
  }

  @Synchronized
  private fun onTouchReturned(p: Point, frameProcessingOffset: TimeMark) {
    if (state in listOf(State.INITIALIZED, State.STOPPED, State.COMPLETE)) return
    LOG.trace("Got touch at $p")
    if (outstandingTouches.contains(p)) {
      val iterator = outstandingTouches.iterator()
      while (iterator.hasNext()) {
        // Complete all previously dispatched touches.
        val cur = iterator.next()
        iterator.remove()
        touchRoundTrips[cur.key] = cur.value.elapsedNow() - frameProcessingOffset.elapsedNow()
        if (cur.key == p) break
      }
    }
    val dispatchedProgress = (touchRoundTrips.size + outstandingTouches.size) / numPointsToTouch.toDouble()
    val receivedProgress = touchRoundTrips.size / numPointsToTouch.toDouble()
    onProgressCallbacks.forEach { it(dispatchedProgress, receivedProgress) }
    if (state == State.WAITING_FOR_OUTSTANDING_TOUCHES && outstandingTouches.isEmpty()) {
      state = State.COMPLETE
    }
  }

  /**
   * Returns a map of percentile to that percentile touch-to-video latency.
   *
   * e.g. the key 50, if present, will map to the value for the median latency, while the key
   * 95 will map to the 95-th percentile latency.
   */
  private fun computePercentiles(): Map<Int, Double> {
    check(isDone()) { "Cannot compute statistics until benchmarking is complete." }
    return Quantiles.percentiles().indexes(IntRange(1, 100).toList()).compute(touchRoundTrips.values.map { it.inWholeMilliseconds })
  }

  /** Extracts the [Color] of the pixel at ([x], [y]) in the image. */
  private fun BufferedImage.extract(x: Int, y: Int): Color = Color(getRGB(x,y))

  private fun BufferedImage.findTouchableAreaInDisplayViewCoordinates(): Rectangle? {
    val imageRectangle = findTouchableArea() ?: return null
    // Start with a nonexistent Rectangle.
    val displayViewRectangle = Rectangle(0, 0, -1, -1)
    // Get two opposite corners of the image rectangle.
    listOf(imageRectangle.location, Point(imageRectangle.right, imageRectangle.bottom))
      // Scale them to device coordinates.
      .map { it.scaledUnbiased(size, abstractDisplayView.deviceDisplaySize) }
      // Now transform them to the display view coordinates, if possible.
      .map { it.toDisplayViewCoordinates() ?: return null }
      // Now add each of these opposite points to the newly created Rectangle.
      .forEach(displayViewRectangle::add)
    return displayViewRectangle.also { LOG.info("Converted touchable area to AbstractDisplayView coordinates: $it")}
  }

  private val BufferedImage.size: Dimension
    get() = Dimension(width, height)

  /**
   * Finds a [Rectangle] of touchable area of the screen, which should be colored green.
   *
   * This method assumes that the marked touchable area overlaps the center of the screen. This method also
   * returns the [Rectangle] in image coordinates.
   */
  private fun BufferedImage.findTouchableArea(): Rectangle? {
    val threshold = 0x1F
    val center = Point(width / 2, height / 2)

    fun Color.isGreenish() = red < threshold && green > 0xFF - threshold && blue < threshold

    val left = (0 until width).find { extract(it, center.y).isGreenish() } ?: return null
    val right = (0 until width).reversed().find { extract(it, center.y).isGreenish() } ?: return null
    val top = (0 until height).find { extract(center.x, it).isGreenish() } ?: return null
    val bottom = (0 until height).reversed().find { extract(center.x, it).isGreenish() } ?: return null

    val width = right - left + 1
    val height = bottom - top + 1
    return Rectangle(left, top, width, height).also { LOG.info("Found touchable area: $it") }
  }

  /** Decode the pixel at ([x],[y]) to an [Int] value. */
  private fun BufferedImage.decode(x: Int, y: Int, bitsPerChannel: Int = BITS_PER_CHANNEL): Int {
    val c = extract(x, y)
    val ignoredBits = 8 - bitsPerChannel
    // Need to round the lower "ignored" bits. I.e. 0b10001111 -> 0b10010000 -> 0b1001
    val roundingFactor = (1 shl ignoredBits).toDouble()
    val r = (c.red / roundingFactor).roundToInt()
    val g = (c.green / roundingFactor).roundToInt()
    val b = (c.blue / roundingFactor).roundToInt()
    return (((r shl bitsPerChannel) or g) shl bitsPerChannel) or b
  }

  /** Decode the frame to a [Point]. */
  private fun BufferedImage.decodeToPoint(): Point {
    val sampleY = height / 4
    val sampleX1 = width / 4
    val sampleX2 = width * 3 / 4
    return Point(decode(sampleX1, sampleY), decode(sampleX2, sampleY))
  }

  /** Get the latency associated with generating this frame. */
  private fun BufferedImage.decodeLatency(): Int = decode(width * 9 / 10, height * 19 / 20, LATENCY_BITS_PER_CHANNEL)

  private fun Point.toDisplayViewCoordinates(): Point? {
    val displayRectangle = abstractDisplayView.displayRectangle ?: return null
    val imageSize = displayRectangle.size.rotatedByQuadrants(abstractDisplayView.displayOrientationQuadrants)
    val p2 = scaledUnbiased(deviceDisplaySize, imageSize)
    val inverseScreenScale = 1.0 / abstractDisplayView.screenScalingFactor
    val viewCoordinates = Point()
    when (abstractDisplayView.displayOrientationQuadrants) {
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

  data class BenchmarkResults(val raw: Map<Point, Duration>, val percentiles: Map<Int, Double>)

  private enum class State {
    INITIALIZED,
    FINDING_TOUCHABLE_AREA,
    SENDING_TOUCHES,
    WAITING_FOR_OUTSTANDING_TOUCHES,
    STOPPED,
    COMPLETE,
  }

  companion object {
    private val LOG = Logger.getInstance(DeviceMirroringBenchmarker::class.java)
    private const val BITS_PER_CHANNEL = 4
    private const val LATENCY_BITS_PER_CHANNEL = 2
    private val MAX_DURATION_FIND_TOUCHABLE_AREA = 1.seconds

    /**
     * Returns a [Sequence] of [Point]s scribbling sinusoidally back and forth across the [Rectangle].
     */
    private fun Rectangle.scribble(numPoints: Int, step: Int, spikiness: Int): Sequence<Point> = sequence {
      // Each row is width points, so make sure we have enough rows.
      val numRows = if (numPoints > (width * height) / step) height else ((numPoints * step + width - 1) / width).coerceIn(1, height)
      val targetStripeHeight = height / numRows.toDouble()
      val p = Point()
      // Scaling factor to compress x coordinates so we go from [0, 1) to [0, 2πn)
      val xScalingFactor = 2 * Math.PI * spikiness
      repeat(numRows) { rowIdx ->
        val stripeStart = y + (targetStripeHeight * rowIdx).roundToInt().coerceIn(0, bottom)
        val stripeEnd = y + (targetStripeHeight * (rowIdx + 1)).roundToInt().coerceIn(0, bottom)
        val verticalMidpoint = (stripeEnd + stripeStart) / 2
        val xCoords = if (rowIdx.isEven()) x until right else (x until right).reversed()
        xCoords.forEach { xCoord ->
          p.x = xCoord
          // How far along are we in [0, 1) ?
          val normalizedX = (xCoord - x) / width.toDouble()
          val yDisplacement = sin(xScalingFactor * normalizedX) * targetStripeHeight / 2
          p.y = (verticalMidpoint - yDisplacement).roundToInt().coerceIn(stripeStart, stripeEnd - 1)
          yield(Point(p))
        }
      }
    }.chunked(step).map{ it.first() }.take(numPoints)

    private fun Int.isEven() = this % 2 == 0
  }
}
