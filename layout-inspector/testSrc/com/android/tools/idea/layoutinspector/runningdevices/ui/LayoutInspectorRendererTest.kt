/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.android.tools.adtui.swing.FakeMouse
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.common.SelectViewAction
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.runDispatching
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.swing.fillRect
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.function.Supplier
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPopupMenu
import kotlin.io.path.pathString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer

private val TEST_DATA_PATH = Path.of("tools", "adt", "idea", "layout-inspector", "testData")
private const val DIFF_THRESHOLD = 0.2

class LayoutInspectorRendererTest {

  private val androidProjectRule = AndroidProjectRule.withSdk()
  private val fileOpenCaptureRule = FileOpenCaptureRule(androidProjectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(androidProjectRule).around(fileOpenCaptureRule)!!

  @get:Rule val testName = TestName()

  @get:Rule val edtRule = EdtRule()

  @get:Rule val applicationRule = ApplicationRule()

  private lateinit var sessionStats: SessionStatisticsImpl

  private val treeSettings = FakeTreeSettings(showRecompositions = false)
  private val renderSettings = FakeRenderSettings()

  private val disposable: Disposable
    get() = androidProjectRule.testRootDisposable

  private lateinit var renderModel: RenderModel
  private lateinit var renderLogic: RenderLogic

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

  private val verticalInspectorModel: InspectorModel
    get() =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

  private val horizontalInspectorModel: InspectorModel
    get() =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.height, deviceScreenDimension.width) {
          view(VIEW1, 10, 15, 25, 25) { image() }
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

  @Before
  fun setUp() {
    renderModel = RenderModel(verticalInspectorModel, mock(), treeSettings) { DisconnectedClient }
    renderLogic = RenderLogic(renderModel, renderSettings)
    sessionStats = SessionStatisticsImpl(DisconnectedClient.clientType)
  }

  @Test
  fun testViewBordersAreRendered() {
    val layoutInspectorRenderer = createRenderer()

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testScreenWithLeftBorder() {
    val inspectorModelWithLeftBorder =
      model(disposable) {
        view(ROOT, 10, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelWithLeftBorder.resourceLookup.screenDimension = deviceScreenDimension

    val renderModel =
      RenderModel(inspectorModelWithLeftBorder, mock(), treeSettings) { DisconnectedClient }
    val layoutInspectorRenderer = createRenderer(renderModel = renderModel)

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testScreenWithRightBorder() {
    val inspectorModelWithRightBorder =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.width - 10, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelWithRightBorder.resourceLookup.screenDimension = deviceScreenDimension

    val renderModel =
      RenderModel(inspectorModelWithRightBorder, mock(), treeSettings) { DisconnectedClient }
    val layoutInspectorRenderer = createRenderer(renderModel = renderModel)

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testScreenWithTopBorder() {
    val inspectorModelWithTopBorder =
      model(disposable) {
        view(ROOT, 0, 10, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelWithTopBorder.resourceLookup.screenDimension = deviceScreenDimension

    val renderModel =
      RenderModel(inspectorModelWithTopBorder, mock(), treeSettings) { DisconnectedClient }
    val layoutInspectorRenderer = createRenderer(renderModel = renderModel)

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testScreenWithBottomBorder() {
    val inspectorModelWithTopBorder =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height - 10) {
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }
    inspectorModelWithTopBorder.resourceLookup.screenDimension = deviceScreenDimension

    val renderModel =
      RenderModel(inspectorModelWithTopBorder, mock(), treeSettings) { DisconnectedClient }
    val layoutInspectorRenderer = createRenderer(renderModel = renderModel)

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  private data class RotationCombination(val displayQuadrant: Int, val deviceRotation: Int)

  private fun allPossibleCombinations(
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

  @Test
  fun testRotation() {
    // test all possible combinations of rotations
    val combinations = allPossibleCombinations(listOf(0, 1, 2, 3), listOf(0, 90, 180, 270))
    combinations.forEach {
      val inspectorModelWithTopBorder =
        model(disposable) {
          view(ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height - 10) {
            view(VIEW1, 10, 15, 25, 25) { image() }
          }
        }
      inspectorModelWithTopBorder.resourceLookup.screenDimension = deviceScreenDimension
      verticalInspectorModel.resourceLookup.displayOrientation = it.deviceRotation
      val inspectorModel =
        when (it.deviceRotation) {
          0,
          180 -> {
            verticalInspectorModel.resourceLookup.screenDimension = Dimension(1080, 1920)
            verticalInspectorModel
          }
          // assume that when the device is horizontal the app is in landscape mode.
          90,
          270 -> {
            horizontalInspectorModel.resourceLookup.screenDimension = Dimension(1920, 1080)
            horizontalInspectorModel
          }
          else -> throw IllegalArgumentException()
        }

      val renderModel =
        RenderModel(inspectorModelWithTopBorder, mock(), treeSettings) { DisconnectedClient }
      val quadrant = calculateRotationCorrection(inspectorModel, { it.displayQuadrant }, { 0 })

      val layoutInspectorRenderer =
        createRenderer(renderModel = renderModel, displayOrientation = quadrant)

      val renderImage = createRenderImage()
      paint(renderImage, layoutInspectorRenderer, displayQuadrant = it.displayQuadrant)
      assertSimilar(renderImage, testName.methodName + "${it.displayQuadrant}_${it.deviceRotation}")
    }
  }

  @Test
  fun testOverlayIsRendered() {
    val layoutInspectorRenderer = createRenderer()

    renderModel.overlay =
      ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/overlay.png").toFile())

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testMouseHoverRegular() {
    val parent = BorderLayoutPanel()
    val layoutInspectorRenderer = createRenderer()
    parent.add(layoutInspectorRenderer)
    parent.size = screenDimension
    layoutInspectorRenderer.size = screenDimension
    val fakeUi = FakeUi(layoutInspectorRenderer)

    assertThat(renderModel.model.hoveredNode).isNull()

    // move mouse above VIEW1.
    fakeUi.mouse.moveTo(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()

    // mouse hover should be disabled when we are not in deep inspect mode.
    assertThat(renderModel.model.hoveredNode).isNull()

    renderModel.overlay =
      ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/overlay.png").toFile())

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testMouseHoverInterceptClicks() {
    val parent = BorderLayoutPanel()
    val layoutInspectorRenderer = createRenderer()
    layoutInspectorRenderer.interceptClicks = true
    parent.add(layoutInspectorRenderer)
    parent.size = screenDimension
    layoutInspectorRenderer.size = screenDimension
    val fakeUi = FakeUi(layoutInspectorRenderer)

    assertThat(renderModel.model.hoveredNode).isNull()

    // move mouse above VIEW1.
    fakeUi.mouse.moveTo(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()

    assertThat(renderModel.model.hoveredNode).isEqualTo(renderModel.model[VIEW1])

    renderModel.overlay =
      ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/overlay.png").toFile())

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  @RunsInEdt
  fun testMouseClick() {
    renderSettings.drawLabel = false
    val layoutInspectorRenderer = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(layoutInspectorRenderer)
    parent.size = screenDimension
    layoutInspectorRenderer.size = screenDimension
    layoutInspectorRenderer.interceptClicks = true

    val fakeUi = FakeUi(layoutInspectorRenderer)

    fakeUi.render()

    // click mouse above VIEW1.
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(renderModel.model.selection).isEqualTo(renderModel.model[VIEW1])

    renderModel.overlay =
      ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/overlay.png").toFile())

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  @RunsInEdt
  fun testMouseDoubleClick() {
    loadComposeFiles()
    val inspectorModel = createModel()
    val renderModel = RenderModel(inspectorModel, mock(), treeSettings) { DisconnectedClient }
    renderSettings.drawLabel = false
    val layoutInspectorRenderer = createRenderer(renderModel)
    val parent = BorderLayoutPanel()
    parent.add(layoutInspectorRenderer)
    parent.size = screenDimension
    layoutInspectorRenderer.size = screenDimension
    layoutInspectorRenderer.interceptClicks = true

    val fakeUi = FakeUi(layoutInspectorRenderer)

    fakeUi.render()

    // click mouse above VIEW1.
    fakeUi.mouse.doubleClick(deviceDisplayRectangle.x + 20, deviceDisplayRectangle.y + 25)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(renderModel.model.selection?.drawId).isEqualTo(2L)
    runDispatching { GotoDeclarationAction.lastAction?.join() }
    fileOpenCaptureRule.checkEditor("demo.xml", 2, "<RelativeLayout")

    val data = DynamicLayoutInspectorSession.newBuilder()
    sessionStats.save(data)
    assertThat(data.gotoDeclaration.doubleClicksFromRender).isEqualTo(1)
  }

  @Test
  @RunsInEdt
  fun testContextMenu() {
    val layoutInspectorRenderer = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(layoutInspectorRenderer)
    parent.size = screenDimension
    layoutInspectorRenderer.size = screenDimension
    layoutInspectorRenderer.interceptClicks = true

    var latestPopup: FakeActionPopupMenu? = null
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, mock(), androidProjectRule.testRootDisposable)
    doAnswer { invocation ->
        latestPopup = FakeActionPopupMenu(invocation.getArgument(1))
        latestPopup
      }
      .whenever(ActionManager.getInstance())
      .createActionPopupMenu(anyString(), any<ActionGroup>())

    val fakeUi = FakeUi(layoutInspectorRenderer)
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
    val layoutInspectorRenderer = createRenderer()
    parent.addToCenter(layoutInspectorRenderer)
    parent.size = screenDimension
    layoutInspectorRenderer.size = screenDimension

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
    val layoutInspectorRenderer = createRenderer()
    parent.addToCenter(layoutInspectorRenderer)
    parent.size = screenDimension
    layoutInspectorRenderer.size = screenDimension

    layoutInspectorRenderer.interceptClicks = true

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
  @RunsInEdt
  fun testSelectionIsClearedWhenDisablingDeepInspect() {
    val layoutInspectorRenderer = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(layoutInspectorRenderer)
    parent.size = screenDimension
    layoutInspectorRenderer.size = screenDimension
    layoutInspectorRenderer.interceptClicks = true

    val fakeUi = FakeUi(layoutInspectorRenderer)

    fakeUi.render()

    // click mouse above VIEW1.
    fakeUi.mouse.click(deviceDisplayRectangle.x + 10, deviceDisplayRectangle.y + 15)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(renderModel.model.selection).isEqualTo(renderModel.model[VIEW1])

    layoutInspectorRenderer.interceptClicks = false

    assertThat(renderModel.model.selection).isNull()
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

    val renderModel =
      RenderModel(inspectorModelWithLeftBorder, mock(), treeSettings) { DisconnectedClient }
    val notificationModel = NotificationModel(androidProjectRule.project)
    var seenNotificationIds = listOf<String>()
    notificationModel.notificationListeners.add {
      seenNotificationIds = notificationModel.notifications.map { it.id }
    }
    val layoutInspectorRenderer =
      createRenderer(renderModel = renderModel, notificationModel = notificationModel)

    val renderImage = createRenderImage()
    paint(renderImage, layoutInspectorRenderer)

    assertThat(seenNotificationIds).containsExactly("rendering.in.secondary.display.not.supported")
    notificationModel.notifications
      .find { it.id == "rendering.in.secondary.display.not.supported" }!!
      .actions
      .isEmpty()

    inspectorModelWithLeftBorder.resourceLookup.isRunningInMainDisplay = true

    paint(renderImage, layoutInspectorRenderer)

    assertThat(seenNotificationIds).isEmpty()
  }

  private fun paint(
    image: BufferedImage,
    layoutInspectorRenderer: LayoutInspectorRenderer,
    displayQuadrant: Int = 0,
  ) {
    val graphics = image.createGraphics()
    // add a gray background
    graphics.fillRect(
      Rectangle(0, 0, screenDimension.width, screenDimension.height),
      Color(250, 250, 250),
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

    layoutInspectorRenderer.paint(graphics)
  }

  private fun createRenderer(
    renderModel: RenderModel = this.renderModel,
    deviceDisplayRectangle: Rectangle = this.deviceDisplayRectangle,
    displayOrientation: Int = 0,
    notificationModel: NotificationModel = NotificationModel(androidProjectRule.project),
  ): LayoutInspectorRenderer {
    return LayoutInspectorRenderer(
      androidProjectRule.testRootDisposable,
      AndroidCoroutineScope(androidProjectRule.testRootDisposable),
      renderLogic,
      renderModel,
      notificationModel,
      displayRectangleProvider = { deviceDisplayRectangle },
      screenScaleProvider = { 1.0 },
      orientationQuadrantProvider = { displayOrientation },
    ) {
      sessionStats
    }
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

  private fun createModel(): InspectorModel =
    model(
      androidProjectRule.testRootDisposable,
      androidProjectRule.project,
      FakeTreeSettings(),
      body =
        DemoExample.setUpDemo(androidProjectRule.fixture) {
          view(0, qualifiedName = "androidx.ui.core.AndroidComposeView") {
            compose(-2, "Column", "MyCompose.kt", 49835523, 532, 17) {
              compose(-3, "Text", "MyCompose.kt", 49835523, 585, 18)
              compose(-4, "Greeting", "MyCompose.kt", 49835523, 614, 19) {
                compose(-5, "Text", "MyCompose.kt", 1216697758, 156, 3)
              }
            }
          }
        },
    )

  private fun loadComposeFiles() {
    val fixture = androidProjectRule.fixture
    fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/compose").toString()
    fixture.copyFileToProject("java/com/example/MyCompose.kt")
    fixture.copyFileToProject("java/com/example/composable/MyCompose.kt")
  }
}

private class FakeMouseListener : MouseAdapter() {
  var mouseClickedCount = 0
  var mouseDraggedCount = 0
  var mouseEnteredCount = 0
  var mouseExitedCount = 0
  var mouseReleasedCount = 0
  var mouseMovedCount = 0
  var mousePressedCount = 0

  override fun mouseClicked(e: MouseEvent) {
    mouseClickedCount += 1
  }

  override fun mouseDragged(e: MouseEvent) {
    mouseDraggedCount += 1
  }

  override fun mouseEntered(e: MouseEvent) {
    mouseEnteredCount += 1
  }

  override fun mouseExited(e: MouseEvent) {
    mouseExitedCount += 1
  }

  override fun mouseReleased(e: MouseEvent) {
    mouseReleasedCount += 1
  }

  override fun mouseMoved(e: MouseEvent) {
    mouseMovedCount += 1
  }

  override fun mousePressed(e: MouseEvent) {
    mousePressedCount += 1
  }
}

private class FakeActionPopupMenu(private val group: ActionGroup) : ActionPopupMenu {
  val popup: JPopupMenu = mock()

  override fun getComponent(): JPopupMenu = popup

  override fun getActionGroup(): ActionGroup = group

  override fun getPlace(): String = error("Not implemented")

  override fun setTargetComponent(component: JComponent) = error("Not implemented")

  override fun setDataContext(dataProvider: Supplier<out DataContext>) = error("Not implemented")

  fun assertSelectViewActionAndGotoDeclaration(vararg expected: Long) {
    val event: AnActionEvent = mock()
    whenever(event.actionManager).thenReturn(ActionManager.getInstance())
    val actions = group.getChildren(event)
    assertThat(actions.size).isEqualTo(2)
    assertThat(actions[0]).isInstanceOf(DropDownAction::class.java)
    val selectActions = (actions[0] as DropDownAction).getChildren(event)
    val selectedViewsIds =
      selectActions.toList().filterIsInstance(SelectViewAction::class.java).map { it.view.drawId }
    assertThat(selectedViewsIds).containsExactlyElementsIn(expected.toList())
    assertThat(actions[1]).isEqualTo(GotoDeclarationAction)
  }
}
