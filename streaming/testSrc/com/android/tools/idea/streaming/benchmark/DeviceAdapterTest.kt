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
package com.android.tools.idea.streaming.benchmark

import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.interpolate
import com.android.tools.idea.streaming.core.location
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeMark
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests the [DeviceAdapter] class. */
@RunWith(JUnit4::class)
class DeviceAdapterTest {
  @get:Rule val projectRule = ProjectRule()

  private val deviceDisplaySize = Dimension(WIDTH, HEIGHT)
  private val displayRectangle = Rectangle(deviceDisplaySize)
  private val mousePressedLocations: MutableList<Point> = mutableListOf()
  private val mouseReleasedLocations: MutableList<Point> = mutableListOf()
  private val keyEvents: MutableList<Pair<Int, Any>> = mutableListOf()
  private val testTimeSource = TestTimeSource()
  private val p = Point(42, 99)
  private val returnedInputs: MutableList<Pair<Point, TimeMark>> = mutableListOf()
  private val errors: MutableList<String> = mutableListOf()
  private val callbacks =
    object : Benchmarker.Adapter.Callbacks<Point> {
      override fun inputReturned(input: Point, effectiveDispatchTime: TimeMark) {
        returnedInputs.add(input to effectiveDispatchTime)
      }

      override fun onReady() {
        onReadyCalls++
      }

      override fun onFailedToBecomeReady(msg: String) {
        errors.add(msg)
      }
    }
  private val fakeInstaller =
    object : StreamingBenchmarkerAppInstaller {
      override suspend fun installBenchmarkingApp(indicator: ProgressIndicator?): Boolean {
        installCalls++
        return installSuccess
      }

      override suspend fun launchBenchmarkingApp(indicator: ProgressIndicator?): Boolean {
        launchCalls++
        return launchSuccess
      }

      override suspend fun uninstallBenchmarkingApp(): Boolean {
        uninstallCalls++
        return true
      }
    }
  private var installCalls = 0
  private var installSuccess = true
  private var launchCalls = 0
  private var launchSuccess = true
  private var uninstallCalls = 0
  private var onReadyCalls = 0
  private var frameNumber = 0u

  private lateinit var view: TestDisplayView
  private lateinit var adapter: DeviceAdapter

  @Before
  fun setUp() {
    view = TestDisplayView(deviceDisplaySize)
    adapter = createAdapter()
  }

  @Test
  fun zeroMaxTouches_throwsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> { createAdapter(maxTouches = 0) }
  }

  @Test
  fun zeroStep_throwsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> { createAdapter(step = 0) }
  }

  @Test
  fun negativeSpikiness_throwsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> { createAdapter(spikiness = -1) }
  }

  @Test
  fun bitsPerChannelOutsideRange_throwsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> { createAdapter(bitsPerChannel = -1) }
    assertFailsWith<IllegalArgumentException> { createAdapter(bitsPerChannel = 9) }
  }

  @Test
  fun dispatch_clicksMouse() {
    adapter.dispatch(p)
    UIUtil.pump()

    assertThat(mousePressedLocations).hasSize(1)
    assertThat(mousePressedLocations[0]).isEqualTo(p)
  }

  @Test
  fun finalizeInputs_releasesMouse() {
    adapter.dispatch(p)
    adapter.finalizeInputs()
    UIUtil.pump()

    assertThat(mouseReleasedLocations).hasSize(1)
    assertThat(mouseReleasedLocations[0]).isEqualTo(p)
  }

  @Test
  fun ready_registersFrameListener() {
    view.notifyFrame(INITIALIZED_FRAME)
    view.notifyFrame(ALL_TOUCHABLE_FRAME)
    view.notifyFrame(p.toBufferedImage())

    assertThat(returnedInputs).isEmpty()

    adapter.ready()
    view.notifyFrame(INITIALIZED_FRAME)
    view.notifyFrame(ALL_TOUCHABLE_FRAME)
    view.notifyFrame(p.toBufferedImage())

    assertThat(returnedInputs).hasSize(1)
  }

  @Test
  fun ready_installsApp() {
    assertThat(installCalls).isEqualTo(0)

    adapter.ready()

    assertThat(installCalls).isEqualTo(1)
  }

  @Test
  fun ready_launchesApp() {
    assertThat(launchCalls).isEqualTo(0)

    adapter.ready()

    assertThat(launchCalls).isEqualTo(1)
  }

  @Test
  fun ready_onlyLaunchesIfInstallSucceeds() {
    installSuccess = false

    adapter.ready()

    assertThat(installCalls).isEqualTo(1)
    assertThat(launchCalls).isEqualTo(0)
    assertThat(errors).hasSize(1)
    assertThat(errors[0]).isEqualTo("Could not install benchmarking app.")
  }

  @Test
  fun ready_failsIfLaunchFails() {
    launchSuccess = false

    adapter.ready()
    assertThat(installCalls).isEqualTo(1)
    assertThat(launchCalls).isEqualTo(1)
    assertThat(errors).hasSize(1)
    assertThat(errors[0]).isEqualTo("Could not launch benchmarking app.")
  }

  @Test
  fun ready_keysConfigurationIntoApp() {
    FakeUi(view, createFakeWindow = true)
    view.isVisible = true
    FakeKeyboardFocusManager(projectRule.disposable).focusOwner = view

    adapter.ready()
    view.notifyFrame(INITIALIZED_FRAME)

    UIUtil.pump()
    val configCharEvents =
      listOf(MAX_BITS, LATENCY_MAX_BITS, BITS_PER_CHANNEL).joinToString(",").map {
        KeyEvent.KEY_TYPED to it
      }
    val expectedKeyEvents =
      listOf(KeyEvent.KEY_PRESSED to KeyEvent.VK_UP, KeyEvent.KEY_RELEASED to KeyEvent.VK_UP) +
        configCharEvents +
        listOf(
          KeyEvent.KEY_PRESSED to KeyEvent.VK_ENTER,
          KeyEvent.KEY_RELEASED to KeyEvent.VK_ENTER,
        )
    assertThat(keyEvents).isEqualTo(expectedKeyEvents)
  }

  @Test
  fun cleanUp_unregistersFrameListener() {
    adapter.ready()
    view.notifyFrame(INITIALIZED_FRAME)
    view.notifyFrame(ALL_TOUCHABLE_FRAME)
    adapter.cleanUp()
    repeat(10) { view.notifyFrame(p.toBufferedImage()) }

    assertThat(returnedInputs).isEmpty()
  }

  @Test
  fun cleanUp_uninstallsApp() {
    assertThat(uninstallCalls).isEqualTo(0)

    adapter.cleanUp()

    assertThat(uninstallCalls).isEqualTo(1)
  }

  @Test
  fun failedToDetectInitializedApp() {
    adapter.ready()
    adapter.frameRendered(ALL_TOUCHABLE_FRAME)
    assertThat(errors).isEmpty()
    // This should be plenty of time to hit the limit
    testTimeSource += 1.hours

    // Need a frame to trigger this
    adapter.frameRendered(ALL_TOUCHABLE_FRAME)

    assertThat(onReadyCalls).isEqualTo(0)
    assertThat(errors).hasSize(1)
    assertThat(errors[0]).startsWith("Failed to detect initialized app")
  }

  @Test
  fun failedToFindTouchableArea() {
    adapter.ready()
    adapter.frameRendered(INITIALIZED_FRAME)

    repeat(10) { adapter.frameRendered(NONE_TOUCHABLE_FRAME) }
    assertThat(errors).isEmpty()

    // This should be plenty of time to hit the limit
    testTimeSource += 1.hours

    // Need a frame to trigger this (one that doesn't have a touchable area)
    adapter.frameRendered(NONE_TOUCHABLE_FRAME)

    assertThat(onReadyCalls).isEqualTo(0)
    assertThat(errors).hasSize(1)
    assertThat(errors[0]).startsWith("Failed to find touchable area within")
  }

  @Test
  fun onlyTouchableAreaReturned() {
    val allPointsAdapter = createAdapter(maxTouches = Int.MAX_VALUE)
    allPointsAdapter.ready()
    allPointsAdapter.frameRendered(INITIALIZED_FRAME)
    allPointsAdapter.frameRendered(TOUCHABLE_AREA_FRAME)

    assertThat(onReadyCalls).isEqualTo(1)
    assertThat(errors).isEmpty()

    assertThat(allPointsAdapter.numInputs()).isEqualTo((WIDTH - 2) * (HEIGHT - 2))
    val allPoints = allPointsAdapter.inputs().asSequence().toList()
    assertThat(allPoints).hasSize(allPointsAdapter.numInputs())
    assertThat(allPoints).containsNoDuplicates()
    allPoints.forEach {
      assertThat(it.x).isIn(1 until WIDTH - 1)
      assertThat(it.y).isIn(1 until HEIGHT - 1)
    }
  }

  @Test
  fun observesStepParameter() {
    val step = 3
    // First a normal adapter
    val oneStepAdapter = createAdapter(maxTouches = step * MAX_TOUCHES)
    oneStepAdapter.ready()
    oneStepAdapter.frameRendered(INITIALIZED_FRAME)
    oneStepAdapter.frameRendered(TOUCHABLE_AREA_FRAME)

    assertThat(oneStepAdapter.numInputs()).isEqualTo(step * MAX_TOUCHES)

    // Now one that uses a step
    val multiStepAdapter = createAdapter(maxTouches = MAX_TOUCHES, step = step)
    multiStepAdapter.ready()
    multiStepAdapter.frameRendered(INITIALIZED_FRAME)
    multiStepAdapter.frameRendered(TOUCHABLE_AREA_FRAME)

    assertThat(multiStepAdapter.numInputs()).isEqualTo(MAX_TOUCHES)

    assertThat(multiStepAdapter.inputs().asSequence().toList())
      .isEqualTo(oneStepAdapter.inputs().asSequence().chunked(step).map { it.first() }.toList())
  }

  @Test
  fun decodesFrame() {
    (0 until 8).forEach { bitsPerChannel ->
      val latency = (9 + bitsPerChannel).milliseconds
      val point = Point(25 - bitsPerChannel, 50 + bitsPerChannel)
      val customAdapter = createAdapter(bitsPerChannel = bitsPerChannel)
      customAdapter.ready()
      customAdapter.frameRendered(INITIALIZED_FRAME)
      customAdapter.frameRendered(ALL_TOUCHABLE_FRAME)
      assertThat(onReadyCalls).isEqualTo(bitsPerChannel + 1)
      assertThat(errors).isEmpty()

      customAdapter.frameRendered(
        point.toBufferedImage(bitsPerChannel = bitsPerChannel, latency = latency)
      )

      assertThat(returnedInputs).hasSize(bitsPerChannel + 1)
      assertThat(returnedInputs.last().first).isEqualTo(point)
      assertThat(returnedInputs.last().second.elapsedNow()).isEqualTo(latency)
    }
  }

  private fun createAdapter(
    maxTouches: Int = MAX_TOUCHES,
    step: Int = 1,
    spikiness: Int = 1,
    bitsPerChannel: Int = 4,
    latencyBits: Int = 6,
  ): DeviceAdapter {
    return DeviceAdapter(
        projectRule.project,
        target = StreamingBenchmarkTarget("Device Name", "12345", view),
        bitsPerChannel = bitsPerChannel,
        latencyBits = latencyBits,
        maxTouches = maxTouches,
        step = step,
        spikiness = spikiness,
        timeSource = testTimeSource,
        installer = fakeInstaller,
        coroutineScope = TestCoroutineScope(TestCoroutineDispatcher()),
      )
      .apply { setCallbacks(callbacks) }
  }

  inner class TestDisplayView(override val deviceDisplaySize: Dimension) : AbstractDisplayView(0) {
    init {
      displayRectangle = Rectangle(deviceDisplaySize)
      val mouseListener =
        object : MouseListener {
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
      val keyListener =
        object : KeyListener {
          override fun keyTyped(e: KeyEvent) {
            keyEvents.add(KeyEvent.KEY_TYPED to e.keyChar)
          }

          override fun keyPressed(e: KeyEvent) {
            keyEvents.add(KeyEvent.KEY_PRESSED to e.keyCode)
          }

          override fun keyReleased(e: KeyEvent) {
            keyEvents.add(KeyEvent.KEY_RELEASED to e.keyCode)
          }
        }
      addKeyListener(keyListener)
      Disposer.register(projectRule.disposable, this)
    }

    override val deviceId: DeviceId = DeviceId.ofPhysicalDevice("test")
    override val displayOrientationQuadrants = 0
    override val apiLevel: Int
      get() = 0

    override fun hardwareInputStateChanged(event: AnActionEvent, enabled: Boolean) {}

    override fun canZoom() = false

    override fun computeActualSize() = deviceDisplaySize

    override fun dispose() {}

    fun notifyFrame(frame: BufferedImage) {
      notifyFrameListeners(Rectangle(), frame)
    }
  }

  companion object {
    private const val BITS_PER_CHANNEL = 4
    private const val MAX_TOUCHES = 10
    private const val WIDTH = 50
    private const val HEIGHT = 100
    private const val LATENCY_MAX_BITS = 6
    private val MAX_BITS = ceil(log2(max(WIDTH, HEIGHT).toDouble())).roundToInt()
    private val ALL_TOUCHABLE_FRAME = bufferedImage { _, _ -> Color.GREEN }
    private val NONE_TOUCHABLE_FRAME = bufferedImage { _, _ -> Color.RED }
    private val TOUCHABLE_AREA_FRAME = bufferedImage { i, j ->
      if ((i in 1 until WIDTH - 1) && (j in 1 until HEIGHT - 1)) Color.GREEN else Color.RED
    }
    private val INITIALIZED_GRADIENT = listOf(Color.RED, Color.GREEN, Color.BLUE)
    private val INITIALIZED_FRAME = bufferedImage { i, _ ->
      interpolate(INITIALIZED_GRADIENT, i / WIDTH.toDouble())
    }

    private fun Int.bits(maxBits: Int = MAX_BITS): String =
      toUInt().toString(2).padStart(maxBits, '0')

    private fun List<String>.toColor(): Color {
      require(!isEmpty()) { "Must provide at least one value." }
      return when (size) {
        1 -> Color(get(0).toColorChannel(), 0, 0)
        2 -> Color(get(0).toColorChannel(), get(1).toColorChannel(), 0)
        else -> Color(get(0).toColorChannel(), get(1).toColorChannel(), get(2).toColorChannel())
      }
    }

    private fun String.toColorChannel(): Int {
      require(length in 1..8) { "Cannot fit $length bits in a color channel." }
      return (toInt(2) * (255 / ((1 shl length) - 1).toDouble())).roundToInt().coerceIn(0, 255)
    }

    private fun Int.toColors(
      maxBits: Int = MAX_BITS,
      bitsPerChannel: Int = BITS_PER_CHANNEL,
    ): List<Color> {
      require(this >= 0) { "Cannot encode a negative integer" }
      require(bitsPerChannel in 0..8) { "Can only use 1 to 8 bits per color channel." }
      if (bitsPerChannel == 0)
        return bits(maxBits).map { if (it == '1') Color.WHITE else Color.BLACK }
      return bits(maxBits).chunked(bitsPerChannel).chunked(3) { it.toColor() }
    }

    /**
     * Gets the item out of the list that is the same proportion of the way through the list as
     * [value] is through [min, max].
     */
    private fun <T> List<T>.getFractional(min: Int, max: Int, value: Int): T {
      val index = (size * (value - min) / (max - min).toDouble()).toInt().coerceIn(indices)
      return get(index)
    }

    private fun Point.toBufferedImage(
      width: Int = WIDTH,
      height: Int = HEIGHT,
      maxBits: Int = MAX_BITS,
      bitsPerChannel: Int = BITS_PER_CHANNEL,
      latencyMaxBits: Int = LATENCY_MAX_BITS,
      latency: Duration = Duration.ZERO,
    ): BufferedImage {
      val xColors = x.toColors(maxBits, bitsPerChannel)
      val yColors = y.toColors(maxBits, bitsPerChannel)
      val latencyColors =
        latency.inWholeMilliseconds.toInt().toColors(latencyMaxBits, bitsPerChannel)
      return bufferedImage(width, height) { i, j ->
        if (j <= height / 2) {
          if (i < width / 2) {
            xColors.getFractional(0, height / 2, j)
          } else {
            yColors.getFractional(0, height / 2, j)
          }
        } else if (j in (height * 17 / 20) until height) {
          latencyColors.getFractional(height * 17 / 20, height, j)
        } else {
          Color.BLACK
        }
      }
    }

    private fun bufferedImage(
      width: Int = WIDTH,
      height: Int = HEIGHT,
      pixelColorSupplier: (Int, Int) -> Color,
    ): BufferedImage {
      val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
      repeat(width) { i ->
        repeat(height) { j -> bufferedImage.setRGB(i, j, pixelColorSupplier(i, j).rgb) }
      }
      return bufferedImage
    }
  }

  private fun DeviceAdapter.frameRendered(bufferedImage: BufferedImage) {
    frameRendered(frameNumber++, displayRectangle, 0, bufferedImage)
  }
}
