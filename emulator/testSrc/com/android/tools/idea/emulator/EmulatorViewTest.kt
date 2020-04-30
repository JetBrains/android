/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.emulator.control.ImageFormat
import com.android.testutils.TestUtils
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.emulator.RuntimeConfigurationOverrider.getRuntimeConfiguration
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import javax.swing.JScrollPane
import kotlin.streams.toList

/**
 * Tests for [EmulatorView] and emulator toolbar actions.
 */
@RunsInEdt
class EmulatorViewTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private var nullableEmulator: FakeEmulator? = null
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())

  var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  @Test
  fun testEmulatorView() {
    val view = createEmulatorView()
    @Suppress("UndesirableClassUsage")
    val container = JScrollPane(view).apply { border = null }
    val ui = FakeUi(container)

    // Check initial appearance.
    var frameNumber = view.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    container.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    var call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 266 height: 547")
    assertViewAppearance(view, "image1")
    assertThat(call.completion.isDone).isFalse() // The call is still ongoing.

    // Check resizing.
    val previousCall = call
    container.size = Dimension(500, 400)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 178 height: 365")
    assertViewAppearance(view, "image2")
    assertThat(previousCall.completion.isCancelled).isTrue() // The previous call is cancelled.
    assertThat(call.completion.isDone).isFalse() // The latest call is still ongoing.

    // Check zoom.
    val skinHeight = 3245.0
    assertThat(view.scale).isWithin(1e-4).of(400 / skinHeight)
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    view.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 360 height: 740")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.ACTUAL)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 1440 height: 2960")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isFalse()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.OUT)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 720 height: 1480")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isTrue()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isTrue()

    view.zoom(ZoomType.FIT)
    ui.layoutAndDispatchEvents()
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 178 height: 365")
    assertThat(view.canZoomIn()).isTrue()
    assertThat(view.canZoomOut()).isFalse()
    assertThat(view.canZoomToActual()).isTrue()
    assertThat(view.canZoomToFit()).isFalse()

    // Check rotation.
    executeAction("android.emulator.rotate.left", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 90.0 }")
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 456 height: 222")
    assertViewAppearance(view, "image3")

    // Check mouse input in landscape orientation.
    ui.mouse.press(19, 306)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 39 y: 52 buttons: 1")

    ui.mouse.dragTo(430, 96)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1401 y: 2720 buttons: 1")

    ui.mouse.release()
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 1401 y: 2720")

    // Check keyboard input.
    ui.keyboard.setFocus(view)
    ui.keyboard.type(FakeKeyboard.Key.A)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""text: "A"""")

    ui.keyboard.type(FakeKeyboard.Key.BACKSPACE)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Backspace"""")

    // Check clockwise rotation.
    executeAction("android.emulator.rotate.right", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setPhysicalModel")
    assertThat(shortDebugString(call.request)).isEqualTo("target: ROTATION value { data: 0.0 data: 0.0 data: 0.0 }")
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 178 height: 365")
    assertViewAppearance(view, "image2")

    // Check mouse input in portrait orientation.
    ui.mouse.press(165, 15)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendMouse")
    assertThat(shortDebugString(call.request)).isEqualTo("x: 40 y: 49 buttons: 1")

    // Check device frame cropping.
    view.cropFrame = true
    call = getStreamScreenshotCallAndWaitForFrame(view, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 195 height: 400")
    assertViewAppearance(view, "image4")
  }

  @Test
  fun testActions() {
    val view = createEmulatorView()

    // Check EmulatorPowerButtonAction.
    executeAction("android.emulator.power.button", view)
    var call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""key: "Power"""")
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keyup key: "Power"""")

    // Check EmulatorVolumeUpButtonAction.
    executeAction("android.emulator.volume.up.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "AudioVolumeUp"""")

    // Check EmulatorVolumeDownButtonAction.
    executeAction("android.emulator.volume.down.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "AudioVolumeDown"""")

    // Check EmulatorBackButtonAction.
    executeAction("android.emulator.back.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "GoBack"""")

    // Check EmulatorHomeButtonAction.
    executeAction("android.emulator.home.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "Home"""")

    // Check EmulatorOverviewButtonAction.
    executeAction("android.emulator.overview.button", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/sendKey")
    assertThat(shortDebugString(call.request)).isEqualTo("""eventType: keypress key: "AppSwitch"""")

    // Check EmulatorScreenshotAction.
    executeAction("android.emulator.screenshot", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/getScreenshot")
    assertThat((call.request as ImageFormat).format).isEqualTo(ImageFormat.ImgFormat.PNG)
    call.waitForCompletion(2, TimeUnit.SECONDS)
    waitForCondition(2, TimeUnit.SECONDS) {
      dispatchAllInvocationEvents()
      Files.list(getRuntimeConfiguration().getDesktopOrUserHomeDirectory()).use {
        it.filter { Pattern.matches("Screenshot_.*\\.png", it.fileName.toString()) }.toList()
      }.isNotEmpty()
    }

    // Check EmulatorShutdownAction.
    executeAction("android.emulator.close", view)
    call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setVmState")
    assertThat(shortDebugString(call.request)).isEqualTo("state: SHUTDOWN")
    call.completion.get()
  }

  private fun getStreamScreenshotCallAndWaitForFrame(view: EmulatorView, frameNumber: Int): GrpcCallRecord {
    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    view.waitForFrame(frameNumber, 2, TimeUnit.SECONDS)
    return call
  }

  private fun executeAction(actionId: String, emulatorView: EmulatorView) {
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction(actionId)
    val event = AnActionEvent(null, TestContext(emulatorView), ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
    action.actionPerformed(event)
  }

  private fun createEmulatorView(): EmulatorView {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root.toPath()
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)
    emulator.start()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val view = EmulatorView(emulatorController, projectRule.fixture.testRootDisposable, false)
    waitForCondition(2, TimeUnit.SECONDS) { view.emulator.connectionState == EmulatorController.ConnectionState.CONNECTED }
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getStatus" call.
    return view
  }

  @Throws(TimeoutException::class)
  private fun waitForCondition(timeout: Long, unit: TimeUnit, condition: () -> Boolean) {
    val timeoutMillis = unit.toMillis(timeout)
    val deadline = System.currentTimeMillis() + timeoutMillis
    var waitUnit = ((timeoutMillis + 9) / 10).coerceAtMost(10)
    while (waitUnit > 0) {
      dispatchAllInvocationEvents()
      if (condition()) {
        return
      }
      Thread.sleep(waitUnit)
      waitUnit = waitUnit.coerceAtMost(deadline - System.currentTimeMillis())
    }
    throw TimeoutException()
  }

  @Throws(TimeoutException::class)
  private fun EmulatorView.waitForFrame(frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { frameNumber >= frame }
  }

  private fun assertViewAppearance(view: EmulatorView, goldenImageName: String) {
    val image = ImageUtils.createDipImage(view.width, view.height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    view.print(g)
    g.dispose()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.1)
  }

  private fun getGoldenFile(name: String): File {
    return TestUtils.getWorkspaceRoot().toPath().resolve("${GOLDEN_FILE_PATH}/${name}.png").toFile()
  }

  private inner class TestContext(private val emulatorView: EmulatorView) : DataContext {

    override fun getData(dataId: String): Any? {
      return when (dataId) {
        EMULATOR_CONTROLLER_KEY.name -> emulatorView.emulator
        EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
        CommonDataKeys.PROJECT.name -> projectRule.project
        else -> null
      }
    }
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/EmulatorViewTest/golden"
