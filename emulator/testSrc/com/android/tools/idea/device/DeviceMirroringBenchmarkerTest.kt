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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.location
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.UIUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.image.BufferedImage
import java.util.Timer
import java.util.TimerTask
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/** Tests the [DeviceMirroringBenchmarker] class. */
@OptIn(ExperimentalTime::class)
@RunWith(JUnit4::class)
class DeviceMirroringBenchmarkerTest {
  private val view = TestDisplayView(Dimension(WIDTH, HEIGHT))
  private val mousePressedLocations: MutableList<Point> = mutableListOf()
  private val mouseReleasedLocations: MutableList<Point> = mutableListOf()
  private val dispatchedKeys: MutableList<Int> = mutableListOf()
  private val benchmarkResults: MutableList<DeviceMirroringBenchmarker.BenchmarkResults> = mutableListOf()
  private val testTimeSource = TestTimeSource()
  private val mockTimer: Timer = mock()
  private val benchmarker = createBenchmarker()
  private var stopCallbackCalled = false
  private var completeCallbackCalled = false

  @Test
  fun zeroMaxTouches_throwsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> { createBenchmarker(maxTouches = 0) }
  }

  @Test
  fun zeroStep_throwsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> { createBenchmarker(step = 0) }
  }

  @Test
  fun negativeSpikiness_throwsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> { createBenchmarker(spikiness = -1) }
  }

  @Test
  fun timerSchedulingStarted() {
    benchmarker.start()

    // We shouldn't start sending events until we have the touchable area.
    verifyNoInteractions(mockTimer)

    // Currently in FINDING_TOUCHABLE_AREA, so give it one.
    view.notifyFrame(ALL_TOUCHABLE_FRAME)

    // Should now be in SENDING_TOUCHES
    val expectedFrameDurationMillis = (1.seconds / TOUCH_RATE_HZ).inWholeMilliseconds
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), eq(0), eq(expectedFrameDurationMillis))
    assertThat(mousePressedLocations).isEmpty()
    taskCaptor.value.run()
    UIUtil.pump() // Wait for dispatched events to be processed.
    assertThat(mousePressedLocations).hasSize(1)
    verifyNoMoreInteractions(mockTimer)
  }

  @Test
  fun stop_callsCallbacksAndCancelsTimer() {
    benchmarker.start()
    view.notifyFrame(ALL_TOUCHABLE_FRAME)
    verify(mockTimer).scheduleAtFixedRate(any(), anyLong(), anyLong())

    benchmarker.stop()

    verify(mockTimer).cancel()
    verifyNoMoreInteractions(mockTimer)
    assertThat(stopCallbackCalled).isTrue()
  }

  @Test
  fun timerSchedulingStopped_maxTouchesReached() {
    benchmarker.start()
    view.notifyFrame(TOUCHABLE_AREA_FRAME)

    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(mousePressedLocations).isEmpty()

    repeat(MAX_TOUCHES) {taskCaptor.value.run() }

    UIUtil.pump() // Wait for dispatched events to be processed.
    assertThat(mousePressedLocations).hasSize(MAX_TOUCHES)
    verify(mockTimer).cancel()
  }

  @Test
  fun timerSchedulingStopped_allPointsTouched() {
    val allPointsBenchmarker = createBenchmarker(maxTouches = Int.MAX_VALUE)
    val numTouchablePixels = WIDTH * HEIGHT
    allPointsBenchmarker.start()
    view.notifyFrame(ALL_TOUCHABLE_FRAME)
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(mousePressedLocations).isEmpty()
    repeat(numTouchablePixels) { taskCaptor.value.run() }

    UIUtil.pump() // Wait for dispatched events to be processed.
    assertThat(mousePressedLocations).hasSize(numTouchablePixels)

    verify(mockTimer).cancel()
  }

  @Test
  fun releasesMouseWhenBenchmarkingEnds() {
    benchmarker.start()
    view.notifyFrame(TOUCHABLE_AREA_FRAME)

    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(mousePressedLocations).isEmpty()

    repeat(MAX_TOUCHES) {taskCaptor.value.run() }

    UIUtil.pump() // Wait for dispatched events to be processed.
    assertThat(mouseReleasedLocations).containsExactly(mousePressedLocations.last())
  }

  @Test
  fun releasesMouseWhenBenchmarkingCanceled() {
    benchmarker.start()
    view.notifyFrame(TOUCHABLE_AREA_FRAME)

    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(mousePressedLocations).isEmpty()

    // Just touch once
    taskCaptor.value.run()
    benchmarker.stop()
    UIUtil.pump() // Wait for dispatched events to be processed.

    assertThat(mouseReleasedLocations).containsExactly(mousePressedLocations.last())
  }

  @Test
  fun failedToFindTouchableArea() {
    benchmarker.start()

    repeat(10) {
      view.notifyFrame(NONE_TOUCHABLE_FRAME)
    }

    assertThat(stopCallbackCalled).isFalse()

    // This should be plenty of time to hit the limit
    testTimeSource += 1.hours
    // Need a frame to trigger this (one that doesn't have a touchable area)
    view.notifyFrame(NONE_TOUCHABLE_FRAME)

    assertThat(stopCallbackCalled).isTrue()
  }

  @Test
  fun onlyTouchesTouchableArea() {
    val allPointsBenchmarker = createBenchmarker(maxTouches = Int.MAX_VALUE)
    allPointsBenchmarker.start()
    view.notifyFrame(TOUCHABLE_AREA_FRAME)
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(mousePressedLocations).isEmpty()
    val numTouchablePixels = (WIDTH - 2) * (HEIGHT - 2)

    repeat(numTouchablePixels) { taskCaptor.value.run() }

    UIUtil.pump() // Wait for dispatched events to be processed.
    assertThat(mousePressedLocations).hasSize(numTouchablePixels)
    // Shouldn't include any points on the border.
    mousePressedLocations.forEach {
      assertThat(it.x).isIn(1 until WIDTH - 1)
      assertThat(it.y).isIn(1 until HEIGHT - 1)
    }
    assertThat(mousePressedLocations).containsNoDuplicates()
  }

  @Test
  fun observesStepParameter() {
    val step = 2  // Anything bigger and there aren't enough pixels
    // First a normal benchmarker
    val oneStepBenchmarker = createBenchmarker(maxTouches = step * MAX_TOUCHES)
    oneStepBenchmarker.start()
    view.notifyFrame(TOUCHABLE_AREA_FRAME)
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    repeat(step * MAX_TOUCHES) { taskCaptor.value.run() }
    UIUtil.pump() // Wait for dispatched events to be processed.
    oneStepBenchmarker.stop()
    assertThat(mousePressedLocations).hasSize(step * MAX_TOUCHES)
    // Save these as we will reuse the mutable list.
    val benchmarkerTouches = ArrayList(mousePressedLocations)
    mousePressedLocations.clear()

    // Now one that uses a step
    val twoStepBenchmarker = createBenchmarker(maxTouches = MAX_TOUCHES, step = step)
    twoStepBenchmarker.start()
    view.notifyFrame(TOUCHABLE_AREA_FRAME)
    verify(mockTimer, times(2)).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    repeat(MAX_TOUCHES) { taskCaptor.value.run() }
    UIUtil.pump() // Wait for dispatched events to be processed.
    assertThat(mousePressedLocations).hasSize(MAX_TOUCHES)

    benchmarkerTouches.forEachIndexed { i, touch ->
      when (i % step) {
        0 -> assertThat(mousePressedLocations).contains(touch)
        else -> assertThat(mousePressedLocations).doesNotContain(touch)
      }
    }
  }

  @Test
  fun isDone() {
    benchmarker.start()
    view.notifyFrame(ALL_TOUCHABLE_FRAME)

    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())

    repeat(MAX_TOUCHES + 1) { taskCaptor.value.run() }

    UIUtil.pump() // Wait for dispatched events to be processed.
    assertThat(mousePressedLocations).hasSize(MAX_TOUCHES)
    mousePressedLocations.forEach {
      view.notifyFrame(it.toBufferedImage(WIDTH, HEIGHT))
    }
    // All touches should be received now, so we should be in state COMPLETE.
    assertThat(benchmarker.isDone()).isTrue()
    assertThat(stopCallbackCalled).isTrue()
    assertThat(completeCallbackCalled).isTrue()
    verify(mockTimer).cancel()
    assertThat(benchmarkResults).hasSize(1)
    assertThat(benchmarkResults[0].raw).hasSize(MAX_TOUCHES)
    assertThat(benchmarkResults[0].raw.values.toSet()).containsExactly(Duration.ZERO)
    benchmarkResults[0].percentiles.values.forEach {
      assertThat(it).isWithin(0.0000000001).of(0.0)
    }
  }

  @Test
  fun computesResultsCorrectly() {
    val allPointsBenchmarker = createBenchmarker(maxTouches = Int.MAX_VALUE)
    allPointsBenchmarker.start()
    view.notifyFrame(ALL_TOUCHABLE_FRAME)
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(mousePressedLocations).isEmpty()

    val latencyMs = 15
    val numTouchablePixels = WIDTH * HEIGHT
    repeat(numTouchablePixels) {
      taskCaptor.value.run()
      UIUtil.pump() // Wait for dispatched event to be processed.
      testTimeSource += it.seconds  // Each touch will take 1s longer than the last.
      view.notifyFrame(mousePressedLocations.last().toBufferedImage(WIDTH, HEIGHT, latencyMs))
    }
    assertThat(mousePressedLocations).hasSize(numTouchablePixels)
    assertThat(allPointsBenchmarker.isDone()).isTrue()
    val expectedRawResults = (0 until numTouchablePixels).associate { mousePressedLocations[it] to (it.seconds - latencyMs.milliseconds) }
    assertThat(benchmarkResults[0].raw).containsExactlyEntriesIn(expectedRawResults)
    // This distribution just increases linearly, so each percentile is a relative fraction of the max.
    val maxDurationMillis = (numTouchablePixels - 1).seconds.toDouble(DurationUnit.MILLISECONDS)
    benchmarkResults[0].percentiles.forEach { (k, v) ->
      assertThat(v).isWithin(0.00000001).of(maxDurationMillis * k / 100 - latencyMs)
    }
  }

  @Test
  fun callsOnProgressCallbacks() {
    val progressValues: MutableList<Pair<Double, Double>> = mutableListOf()
    benchmarker.addOnProgressCallback { sentProgress, receivedProgress ->
      progressValues.add(sentProgress to receivedProgress)
    }

    benchmarker.start()
    view.notifyFrame(ALL_TOUCHABLE_FRAME)

    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())

    repeat(MAX_TOUCHES + 1) { taskCaptor.value.run() }

    UIUtil.pump() // Wait for dispatched events to be processed.
    assertThat(mousePressedLocations).hasSize(MAX_TOUCHES)

    mousePressedLocations.forEach {
      view.notifyFrame(it.toBufferedImage(WIDTH, HEIGHT))
    }

    assertThat(progressValues).hasSize(mousePressedLocations.size)
    assertThat(progressValues.map{it.second}).isStrictlyOrdered()
    assertThat(progressValues.first().second).isWithin(0.000001).of(1 / mousePressedLocations.size.toDouble())
    assertThat(progressValues.last().second).isWithin(0.000001).of(1.0)
    progressValues.map{it.first}.forEach {
      assertThat(it).isWithin(0.000001).of(1.0)
    }
  }

  private fun createBenchmarker(maxTouches: Int = MAX_TOUCHES, step: Int = 1, spikiness: Int = 1) : DeviceMirroringBenchmarker {
    return DeviceMirroringBenchmarker(
      abstractDisplayView = view,
      touchRateHz = TOUCH_RATE_HZ,
      maxTouches = maxTouches,
      step = step,
      spikiness = spikiness,
      timeSource = testTimeSource,
      timer = mockTimer)
      .apply {
        addOnStoppedCallback { stopCallbackCalled = true }
        addOnCompleteCallback {
          benchmarkResults.add(it)
          completeCallbackCalled = true
        }
      }
  }

  inner class TestDisplayView(override val deviceDisplaySize: Dimension) : AbstractDisplayView(0) {
    init {
      displayRectangle = Rectangle(deviceDisplaySize)
      val mouseListener = object : MouseListener {
        override fun mouseClicked(e: MouseEvent) {}

        override fun mousePressed(e: MouseEvent) {
          mousePressedLocations.add(e.location)
        }

        override fun mouseReleased(e: MouseEvent) {
          mouseReleasedLocations.add(e.location)
        }

        override fun mouseEntered(e: MouseEvent) {}

        override fun mouseExited(e: MouseEvent) {}
      }
      addMouseListener(mouseListener)
    }
    override val displayOrientationQuadrants = 0
    override fun canZoom() = false
    override fun computeActualSize() = deviceDisplaySize
    override fun dispose() {}
    override fun dispatchKey(keyCode: Int) {
      dispatchedKeys.add(keyCode)
    }
    fun notifyFrame(frame: BufferedImage) {
      notifyFrameListeners(Rectangle(), frame)
    }
  }

  companion object {
    private const val BITS_PER_CHANNEL = 4
    private const val LATENCY_BITS_PER_CHANNEL = 2
    private const val MAX_TOUCHES = 10
    private const val TOUCH_RATE_HZ = 5
    private const val WIDTH = 5
    private const val HEIGHT = 10
    private val ALL_TOUCHABLE_FRAME = bufferedImage(WIDTH, HEIGHT) {_, _ -> Color.GREEN}
    private val NONE_TOUCHABLE_FRAME = bufferedImage(WIDTH, HEIGHT) {_, _ -> Color.RED}
    private val TOUCHABLE_AREA_FRAME = bufferedImage(WIDTH, HEIGHT) {i, j ->
        if ((i in 1 until WIDTH - 1) && (j in 1 until HEIGHT - 1)) Color.GREEN else Color.RED
    }

    private fun Int.toColor(bitsPerChannel: Int = BITS_PER_CHANNEL) : Color {
      val bitmask = (1 shl bitsPerChannel) - 1
      val emptyBits = 8 - bitsPerChannel
      val r = ((this shr (bitsPerChannel * 2)) and bitmask) shl emptyBits
      val g = ((this shr bitsPerChannel) and bitmask) shl emptyBits
      val b = (this and bitmask) shl emptyBits
      return Color(r, g, b)
    }

    private fun Point.toBufferedImage(width: Int, height: Int, latencyMs: Int = 0): BufferedImage {
      val xColor = x.toColor()
      val yColor = y.toColor()
      val latencyColor = latencyMs.toColor(LATENCY_BITS_PER_CHANNEL)
      return bufferedImage(width, height) { i, j ->
        if (j <= height / 2) {
          if (i < width / 2) xColor else yColor
        }
        else {
          latencyColor
        }
      }
    }

    private fun bufferedImage(width: Int, height: Int, pixelColorSupplier: (Int, Int) -> Color): BufferedImage {
      val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
      repeat(width) { i ->
        repeat(height) { j ->
          bufferedImage.setRGB(i, j, pixelColorSupplier(i,j).rgb)
        }
      }
      return bufferedImage
    }
  }
}
