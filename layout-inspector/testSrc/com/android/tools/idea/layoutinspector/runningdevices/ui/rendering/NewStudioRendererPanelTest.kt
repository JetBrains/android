/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui.rendering

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.android.tools.adtui.swing.FakeMouse
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.runningdevices.calculateRotationCorrection
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.window
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.swing.fillRect
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private val TEST_DATA_PATH = Path.of("tools", "adt", "idea", "layout-inspector", "testData")
private const val DIFF_THRESHOLD = 0.5

private val backgroundColor = Color(200, 200, 200)

class NewStudioRendererPanelTest {

  @get:Rule val testName = TestName()
  @get:Rule val edtRule = EdtRule()
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val disposableRule = DisposableRule()

  private val treeSettings = FakeTreeSettings(showRecompositions = false)
  private val renderSettings = FakeRenderSettings()

  private val disposable: Disposable
    get() = disposableRule.disposable

  private var navigateToSelectedViewInvocations = 0

  /** The dimension of the screen, or canvas in this case */
  private val screenDimension = Dimension(200, 250)
  /** The dimension of the device screen */
  private val deviceScreenDimension = Dimension(100, 150)
  /**
   * The rectangle that contains the device rendering, LI rendering should be overlaid to this
   * rectangle.
   */
  private val deviceDisplayRectangle =
    Rectangle(10, 10, deviceScreenDimension.width, deviceScreenDimension.height)

  /** An inspector model with views arranged vertically */
  private val verticalInspectorModel: InspectorModel
    get() =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

  /** An inspector model with views arranged horizontally */
  private val horizontalInspectorModel: InspectorModel
    get() =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.height, deviceScreenDimension.width) {
          view(VIEW1, 10, 15, 25, 25) { image() }
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

  @Test
  fun testViewBordersAreRendered() {
    val (_, renderer) = createRenderer()

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testBoundsOverflowRight() {
    val inspectorModelOverflowRight =
      model(disposable) {
        view(ROOT, 10, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelOverflowRight.resourceLookup.screenDimension = deviceScreenDimension

    val (_, renderer) = createRenderer(inspectorModel = inspectorModelOverflowRight)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testBoundsOverflowLeft() {
    val inspectorModelOverflowLeft =
      model(disposable) {
        view(ROOT, -10, 0, deviceScreenDimension.width - 10, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelOverflowLeft.resourceLookup.screenDimension = deviceScreenDimension

    val (_, renderer) = createRenderer(inspectorModel = inspectorModelOverflowLeft)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testBoundsOverflowBottom() {
    val inspectorModelOverflowBottom =
      model(disposable) {
        view(ROOT, 0, 10, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelOverflowBottom.resourceLookup.screenDimension = deviceScreenDimension

    val (_, renderer) = createRenderer(inspectorModel = inspectorModelOverflowBottom)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testBoundsOverflowTop() {
    val inspectorModelOverflowTop =
      model(disposable) {
        view(ROOT, 0, -10, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelOverflowTop.resourceLookup.screenDimension = deviceScreenDimension

    val (_, renderer) = createRenderer(inspectorModel = inspectorModelOverflowTop)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testRotation() {
    val combinations = generateAllPossibleRotations(listOf(0, 1, 2, 3), listOf(0, 90, 180, 270))
    combinations.forEach {
      verticalInspectorModel.resourceLookup.displayOrientation = it.deviceRotation
      val inspectorModel =
        when (it.deviceRotation) {
          0,
          180 -> {
            // App in portrait mode.
            verticalInspectorModel.resourceLookup.screenDimension = Dimension(1080, 1920)
            verticalInspectorModel
          }
          90,
          270 -> {
            // App is in landscape mode.
            horizontalInspectorModel.resourceLookup.screenDimension = Dimension(1920, 1080)
            horizontalInspectorModel
          }
          else -> throw IllegalArgumentException()
        }

      val quadrant = calculateRotationCorrection(inspectorModel, { it.displayQuadrant }, { 0 })

      val (_, renderer) = createRenderer(displayOrientation = quadrant)

      val renderImage = createRenderImage()
      paint(renderImage, renderer, displayQuadrant = it.displayQuadrant)
      assertSimilar(renderImage, testName.methodName + "${it.displayQuadrant}_${it.deviceRotation}")
    }
  }

  @Test
  fun testMouseEventsWithDeepInspectDisabled() {
    val parent = BorderLayoutPanel()
    val (model, renderer) = createRenderer()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension
    val fakeUi = FakeUi(renderer)

    assertThat(model.hoveredNode.value).isNull()

    // move mouse above VIEW1.
    fakeUi.mouse.moveTo(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()

    // view should not be selected since we're not intercepting clicks.
    assertThat(model.hoveredNode.value).isNull()
    assertThat(model.hoveredNode.value).isNull()

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testHoveredNode() {
    val parent = BorderLayoutPanel()
    val (model, renderer) = createRenderer()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension
    val fakeUi = FakeUi(renderer)

    renderer.interceptClicks = true

    assertThat(model.hoveredNode.value).isNull()

    // move mouse above VIEW1.
    fakeUi.mouse.moveTo(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()

    assertThat(model.hoveredNode.value!!.bounds)
      .isEqualTo(model.inspectorModel[VIEW1]!!.layoutBounds)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  @RunsInEdt
  fun testSelectedNode() {
    renderSettings.drawLabel = false
    val (model, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderer.interceptClicks = true

    val fakeUi = FakeUi(renderer)

    fakeUi.render()

    // click mouse above VIEW1.
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(model.selectedNode.value!!.bounds)
      .isEqualTo(model.inspectorModel[VIEW1]!!.layoutBounds)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  @RunsInEdt
  fun testRecomposition() {
    val recompositionModel =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
          compose(COMPOSE1, "name", x = 10, y = 50, width = 80, height = 50, composeCount = 15)
        }
      }

    val window =
      window(ROOT, ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
        view(drawId = VIEW1, x = 10, y = 15, width = 25, height = 25) { image() }
        compose(COMPOSE1, "name", x = 10, y = 50, width = 80, height = 50, composeCount = 100)
      }
    // Receive an update with recomposition counts.
    recompositionModel.update(window, listOf(ROOT), 0)

    treeSettings.showRecompositions = true

    renderSettings.drawLabel = false
    val (model, renderer) = createRenderer(inspectorModel = recompositionModel)
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderer.interceptClicks = true

    val fakeUi = FakeUi(renderer)

    fakeUi.render()

    // click mouse above VIEW1.
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(model.recomposingNodes.value).hasSize(1)
    assertThat(model.recomposingNodes.value.first().bounds)
      .isEqualTo(model.inspectorModel[COMPOSE1]!!.layoutBounds)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  @RunsInEdt
  fun testLabel() {
    val (model, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderSettings.drawLabel = true
    renderer.interceptClicks = true

    val fakeUi = FakeUi(renderer)

    fakeUi.render()

    // click mouse above COMPOSE1.
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 50)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(model.selectedNode.value!!.bounds)
      .isEqualTo(model.inspectorModel[COMPOSE1]!!.layoutBounds)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)

    renderSettings.drawLabel = false
  }

  @Test
  @RunsInEdt
  fun testLabelTopOffset() {
    val (model, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderSettings.drawLabel = true
    renderer.interceptClicks = true

    val fakeUi = FakeUi(renderer)

    fakeUi.render()

    // click mouse above ROOT.
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 10)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(model.selectedNode.value!!.bounds)
      .isEqualTo(model.inspectorModel[ROOT]!!.layoutBounds)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)

    renderSettings.drawLabel = false
  }

  @Test
  @RunsInEdt
  fun testLabelLeftOffset() {
    val customModel =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(drawId = VIEW1, x = -10, y = 15, width = 25, height = 25) { image() }
          compose(COMPOSE1, "name", x = 10, y = 50, width = 80, height = 50, composeCount = 15)
        }
      }

    val (model, renderer) = createRenderer(inspectorModel = customModel)
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderSettings.drawLabel = true
    renderer.interceptClicks = true

    val fakeUi = FakeUi(renderer)

    fakeUi.render()

    // click mouse above VIEW1.
    fakeUi.mouse.click(deviceDisplayRectangle.x + 5, deviceDisplayRectangle.y + 15)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(model.selectedNode.value!!.bounds)
      .isEqualTo(model.inspectorModel[VIEW1]!!.layoutBounds)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)

    renderSettings.drawLabel = false
  }

  @Test
  @RunsInEdt
  fun testMouseDoubleClick() {
    renderSettings.drawLabel = false
    val (_, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderer.interceptClicks = true

    val fakeUi = FakeUi(renderer)

    fakeUi.render()

    // click mouse above VIEW1.
    fakeUi.mouse.doubleClick(deviceDisplayRectangle.x + 20, deviceDisplayRectangle.y + 25)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(navigateToSelectedViewInvocations).isEqualTo(1)
  }

  @Test
  @RunsInEdt
  fun testContextMenu() {
    val (_, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderer.interceptClicks = true

    var latestPopup: FakeActionPopupMenu? = null
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, mock(), disposable)
    doAnswer { invocation ->
        latestPopup = FakeActionPopupMenu(invocation.getArgument(1))
        latestPopup
      }
      .whenever(ActionManager.getInstance())
      .createActionPopupMenu(anyString(), any<ActionGroup>())

    val fakeUi = FakeUi(renderer)
    fakeUi.render()

    // Right click on VIEW1 when system views are showing:
    fakeUi.mouse.click(
      deviceDisplayRectangle.x + 10,
      deviceDisplayRectangle.y + 15,
      FakeMouse.Button.RIGHT,
    )
    latestPopup!!.assertSelectViewActionAndGotoDeclaration(ROOT, VIEW1)
  }

  @Test
  @RunsInEdt
  fun testEventsDispatchedToParent() {
    val fakeMouseListener = FakeMouseListener()
    val parent = BorderLayoutPanel()
    parent.addMouseListener(fakeMouseListener)
    parent.addMouseMotionListener(fakeMouseListener)
    val (_, renderer) = createRenderer()
    parent.addToCenter(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    val fakeUi = FakeUi(parent)
    fakeUi.render()

    // move mouse above VIEW1.
    fakeUi.mouse.moveTo(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.layoutAndDispatchEvents()

    assertThat(fakeMouseListener.mouseClickedCount).isEqualTo(1)
    assertThat(fakeMouseListener.mouseEnteredCount).isEqualTo(1)
    assertThat(fakeMouseListener.mouseReleasedCount).isEqualTo(1)
    assertThat(fakeMouseListener.mouseMovedCount).isEqualTo(2)
    assertThat(fakeMouseListener.mousePressedCount).isEqualTo(1)

    fakeUi.mouse.press(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)
    fakeUi.mouse.dragTo(1, 1)
    fakeUi.mouse.release()
    fakeUi.mouse.moveTo(-1, -1)

    fakeUi.layoutAndDispatchEvents()

    assertThat(fakeMouseListener.mouseExitedCount).isEqualTo(1)
    assertThat(fakeMouseListener.mouseDraggedCount).isEqualTo(1)
  }

  @Test
  @RunsInEdt
  fun testEventsNotDispatchedToParent() {
    val fakeMouseListener = FakeMouseListener()
    val parent = BorderLayoutPanel()
    parent.addMouseListener(fakeMouseListener)
    parent.addMouseMotionListener(fakeMouseListener)
    val (model, renderer) = createRenderer()
    parent.addToCenter(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderer.interceptClicks = true

    val fakeUi = FakeUi(parent)
    fakeUi.render()

    fakeUi.mouse.moveTo(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.layoutAndDispatchEvents()

    assertThat(fakeMouseListener.mouseClickedCount).isEqualTo(0)
    assertThat(fakeMouseListener.mouseEnteredCount).isEqualTo(0)
    assertThat(fakeMouseListener.mouseReleasedCount).isEqualTo(0)
    assertThat(fakeMouseListener.mouseMovedCount).isEqualTo(0)
    assertThat(fakeMouseListener.mousePressedCount).isEqualTo(0)

    fakeUi.mouse.press(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)
    fakeUi.mouse.dragTo(1, 1)
    fakeUi.mouse.release()
    fakeUi.mouse.moveTo(-1, -1)

    fakeUi.layoutAndDispatchEvents()

    assertThat(fakeMouseListener.mouseExitedCount).isEqualTo(0)
    assertThat(fakeMouseListener.mouseDraggedCount).isEqualTo(0)
  }

  @Test
  fun testLayoutInspectorRenderingOutsideOfMainDisplayShowError() {
    val inspectorModelWithLeftBorder =
      model(disposable) {
        view(ROOT, 10, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelWithLeftBorder.resourceLookup.isRunningInMainDisplay = false

    val notificationModel = NotificationModel(projectRule.project)
    var seenNotificationIds = listOf<String>()
    notificationModel.notificationListeners.add {
      seenNotificationIds = notificationModel.notifications.map { it.id }
    }
    val (_, renderer) =
      createRenderer(
        inspectorModel = inspectorModelWithLeftBorder,
        notificationModel = notificationModel,
      )

    val renderImage = createRenderImage()
    paint(renderImage, renderer)

    assertThat(seenNotificationIds).containsExactly("rendering.in.secondary.display.not.supported")
    notificationModel.notifications
      .find { it.id == "rendering.in.secondary.display.not.supported" }!!
      .actions
      .isEmpty()

    inspectorModelWithLeftBorder.resourceLookup.isRunningInMainDisplay = true

    paint(renderImage, renderer)

    assertThat(seenNotificationIds).isEmpty()
  }

  @Test
  @RunsInEdt
  fun testDisablingInterceptClicksClearsSelection() {
    val (model, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    renderer.interceptClicks = true

    val fakeUi = FakeUi(renderer)

    fakeUi.render()

    // click mouse above VIEW1.
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)
    fakeUi.mouse.moveTo(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(model.selectedNode.value!!.bounds)
      .isEqualTo(model.inspectorModel[VIEW1]!!.layoutBounds)
    assertThat(model.hoveredNode.value!!.bounds)
      .isEqualTo(model.inspectorModel[VIEW1]!!.layoutBounds)

    renderer.interceptClicks = false

    assertThat(model.selectedNode.value).isNull()
    assertThat(model.hoveredNode.value).isNull()
  }

  @Test
  fun testOverlayIsRendered() = runTest {
    val file = resolveWorkspacePathUnchecked("${TEST_DATA_PATH}/overlay.png").toFile()
    val imageBytes = file.readBytes()

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val (model, renderer) = createRenderer(scope = scope)
    model.setOverlay(imageBytes)

    testScheduler.advanceUntilIdle()

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testOverlayAlpha() = runTest {
    val file = resolveWorkspacePathUnchecked("${TEST_DATA_PATH}/overlay.png").toFile()
    val imageBytes = file.readBytes()

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val (model, renderer) = createRenderer(scope = scope)
    model.setOverlay(imageBytes)
    model.setOverlayTransparency(1f)

    testScheduler.advanceUntilIdle()

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  private fun paint(
    image: BufferedImage,
    renderer: NewStudioRendererPanel,
    displayQuadrant: Int = 0,
  ) {
    val graphics = image.createGraphics()
    // add a gray background
    graphics.fillRect(
      Rectangle(0, 0, screenDimension.width, screenDimension.height),
      backgroundColor,
    )
    // render the display rectangle in black, the rendering from LI should be overlaid to it.
    graphics.color = Color(0, 0, 0)
    // rotate the device display rectangle to match the quadrant rotation
    val displayRect =
      when (displayQuadrant) {
        0,
        2 -> deviceDisplayRectangle
        1,
        3 ->
          Rectangle(
            deviceDisplayRectangle.y,
            deviceDisplayRectangle.x,
            deviceDisplayRectangle.height,
            deviceDisplayRectangle.width,
          )
        else -> throw IllegalArgumentException()
      }
    graphics.draw(displayRect)
    graphics.font = ImageDiffTestUtil.getDefaultFont()

    renderer.paint(graphics)
  }

  private fun createRenderer(
    inspectorModel: InspectorModel = verticalInspectorModel,
    deviceDisplayRectangle: Rectangle = this.deviceDisplayRectangle,
    displayOrientation: Int = 0,
    notificationModel: NotificationModel = NotificationModel(projectRule.project),
    scope: CoroutineScope = disposable.createCoroutineScope(),
  ): Pair<EmbeddedRendererModel, NewStudioRendererPanel> {
    val renderModel =
      EmbeddedRendererModel(
        parentDisposable = disposable,
        inspectorModel = inspectorModel,
        treeSettings = treeSettings,
        renderSettings = renderSettings,
        navigateToSelectedViewOnDoubleClick = { navigateToSelectedViewInvocations += 1 },
      )

    val panel =
      NewStudioRendererPanel(
        disposable = disposable,
        scope = scope,
        renderModel = renderModel,
        notificationModel = notificationModel,
        displayRectangleProvider = { deviceDisplayRectangle },
        screenScaleProvider = { 1.0 },
        orientationQuadrantProvider = { displayOrientation },
      )

    return Pair(renderModel, panel)
  }

  private fun createRenderImage(): BufferedImage {
    @Suppress("UndesirableClassUsage")
    return BufferedImage(screenDimension.width, screenDimension.height, BufferedImage.TYPE_INT_ARGB)
  }

  /**
   * Check that the generated [renderImage] is similar to the one stored on disk. If the image
   * stored on disk does not exist, it is created.
   */
  private fun assertSimilar(renderImage: BufferedImage, imageName: String) {
    val testDataPath = TEST_DATA_PATH.resolve(this.javaClass.simpleName)
    ImageDiffUtil.assertImageSimilar(
      TestUtils.resolveWorkspacePathUnchecked(testDataPath.resolve("$imageName.png").pathString),
      renderImage,
      DIFF_THRESHOLD,
    )
  }
}

private data class RotationCombination(val displayQuadrant: Int, val deviceRotation: Int)

/** Generates all possible combinations of display quadrants and device rotation */
private fun generateAllPossibleRotations(
  displayQuadrants: List<Int>,
  deviceRotations: List<Int>,
): List<RotationCombination> {
  val combinations = mutableListOf<RotationCombination>()

  for (num1 in displayQuadrants) {
    for (num2 in deviceRotations) {
      val pair = RotationCombination(num1, num2)
      combinations.add(pair)
    }
  }

  return combinations
}
