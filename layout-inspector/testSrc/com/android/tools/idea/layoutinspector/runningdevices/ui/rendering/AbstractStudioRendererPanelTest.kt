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
import com.android.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.android.tools.adtui.swing.FakeMouse
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.pipeline.appinspection.Screenshot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.processBitmap
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.layoutinspector.BitmapType
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
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private val TEST_DATA_PATH = Path.of("tools", "adt", "idea", "layout-inspector", "testData")
private const val DIFF_THRESHOLD = 0.1

private val backgroundColor = Color(200, 200, 200)

class AbstractStudioRendererPanelTest {

  @get:Rule val testName = TestName()
  @get:Rule val edtRule = EdtRule()
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val disposableRule = DisposableRule()

  /** The dimension of the device screen */
  private val deviceScreenDimension = Dimension(100, 150)

  private val treeSettings = FakeTreeSettings(showRecompositions = false)
  private val renderSettings = FakeRenderSettings()

  private val disposable: Disposable
    get() = disposableRule.disposable

  private val screenDimension = Dimension(500, 500)

  /** A simple inspector model for basic rendering tests */
  private val simpleInspectorModel: InspectorModel
    get() =
      model(disposable) { view(ROOT, 0, 0, 100, 100) { view(VIEW1, 10, 10, 50, 50) { image() } } }

  @Test
  @RunsInEdt
  fun testCoordinateMappingWithTransform() {
    // Define a transform: Scale 2x, Translate (100, 100)
    // A point (x, y) in model coordinates corresponds to (2x + 100, 2y + 100) on screen.
    val transform =
      AffineTransform().apply {
        translate(100.0, 100.0)
        scale(2.0, 2.0)
      }

    val (model, renderer) = createRenderer(renderTransformProvider = { transform })
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    model.setInterceptClicks(true)
    val fakeUi = FakeUi(renderer)

    // Target VIEW1 at (10, 10) in model coordinates.
    // Screen coordinates = (10 * 2 + 100, 10 * 2 + 100) = (120, 120)
    // Click at screen (130, 130) -> model (15, 15)
    fakeUi.mouse.click(130, 130)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(model.inspectorModel.selection?.drawId).isEqualTo(VIEW1)
    assertThat(model.selectedNode.value!!.bounds)
      .isEqualTo(model.inspectorModel[VIEW1]!!.layoutBounds)
  }

  @Test
  @RunsInEdt
  fun testHoverWithTransform() {
    val transform = AffineTransform().apply { scale(0.5, 0.5) }

    val (model, renderer) = createRenderer(renderTransformProvider = { transform })
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    model.setInterceptClicks(true)
    val fakeUi = FakeUi(renderer)

    // Target VIEW1 at (10, 10) to (60, 60) in model coordinates.
    // With 0.5 scale, this is (5, 5) to (30, 30) on screen.
    // Hover at screen (10, 10) -> model (20, 20), which is inside VIEW1.
    fakeUi.mouse.moveTo(10, 10)

    fakeUi.render()

    assertThat(model.inspectorModel.hoveredNode?.drawId).isEqualTo(VIEW1)
  }

  @Test
  @RunsInEdt
  fun testMouseExitClearsHover() {
    val (model, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    model.setInterceptClicks(true)
    val fakeUi = FakeUi(renderer)

    // Hover inside VIEW1 (located at 10,10 with size 50x50)
    fakeUi.mouse.moveTo(20, 20)
    fakeUi.render()
    assertThat(model.inspectorModel.hoveredNode?.drawId).isEqualTo(VIEW1)

    // Move outside the panel
    fakeUi.mouse.moveTo(-1, -1)
    fakeUi.render()
    assertThat(model.hoveredNode.value).isNull()
  }

  @Test
  @RunsInEdt
  fun testContextMenuInvoked() {
    val (model, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    model.setInterceptClicks(true)

    // Mock ActionManager to capture the popup menu
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

    // Right click on VIEW1 (at 10,10 w/ size 50x50)
    fakeUi.mouse.click(20, 20, FakeMouse.Button.RIGHT)

    assertThat(model.inspectorModel.selection?.drawId).isEqualTo(VIEW1)
    assertThat(latestPopup).isNotNull()
    latestPopup!!.assertSelectViewActionAndGotoDeclaration(ROOT, VIEW1)
  }

  @Test
  @RunsInEdt
  fun testClicksIgnoredWhenInterceptDisabled() {
    val (model, renderer) = createRenderer(interceptClicks = false)
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    val fakeUi = FakeUi(renderer)

    // Click on VIEW1
    fakeUi.mouse.click(20, 20)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(model.selectedNode.value).isNull()
  }

  @Test
  fun testRenderingWithImage() {
    val screenshot = Screenshot("test_image.png", BitmapType.RGB_565)
    val imageBytes = processBitmap(screenshot.bytes)

    val inspectorModel =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          image = imageBytes
          view(VIEW1, 10, 15, 25, 25) { image() }
        }
      }

    val (_, renderer) = createRenderer(inspectorModel = inspectorModel)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  private fun createRenderer(
    inspectorModel: InspectorModel = simpleInspectorModel,
    interceptClicks: Boolean = true,
    renderTransformProvider: () -> AffineTransform = { AffineTransform() },
    overlayBoundsProvider: (AffineTransform) -> Rectangle? = { null },
    scope: CoroutineScope = disposable.createCoroutineScope(),
  ): Pair<EmbeddedRendererModel, TestStudioRendererPanel> {
    val renderModel =
      EmbeddedRendererModel(
        parentDisposable = disposable,
        inspectorModel = inspectorModel,
        treeSettings = treeSettings,
        renderSettings = renderSettings,
        navigateToSelectedViewOnDoubleClick = {},
      )

    val panel =
      TestStudioRendererPanel(
        disposable = disposable,
        scope = scope,
        renderModel = renderModel,
        interceptClicks = interceptClicks,
        renderTransformProvider = renderTransformProvider,
        overlayBoundsProvider = overlayBoundsProvider,
      )

    return Pair(renderModel, panel)
  }

  private fun paint(image: BufferedImage, renderer: AbstractStudioRendererPanel) {
    val graphics = image.createGraphics()
    graphics.fillRect(
      Rectangle(0, 0, screenDimension.width, screenDimension.height),
      backgroundColor,
    )
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    renderer.bounds = Rectangle(0, 0, screenDimension.width, screenDimension.height)
    renderer.paint(graphics)
  }

  private fun createRenderImage(): BufferedImage {
    @Suppress("UndesirableClassUsage")
    return BufferedImage(screenDimension.width, screenDimension.height, BufferedImage.TYPE_INT_ARGB)
  }

  private fun assertSimilar(
    renderImage: BufferedImage,
    imageName: String,
    maxDiff: Double = DIFF_THRESHOLD,
  ) {
    val testDataPath = TEST_DATA_PATH.resolve(this.javaClass.simpleName)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked(testDataPath.resolve("$imageName.png").pathString),
      renderImage,
      maxDiff,
    )
  }

  /**
   * Concrete implementation of [AbstractStudioRendererPanel] for testing purposes. Allows injecting
   * behavior for abstract methods.
   */
  private class TestStudioRendererPanel(
    disposable: Disposable,
    scope: CoroutineScope,
    renderModel: EmbeddedRendererModel,
    override val interceptClicks: Boolean,
    private val renderTransformProvider: () -> AffineTransform?,
    private val overlayBoundsProvider: (AffineTransform) -> Rectangle?,
  ) : AbstractStudioRendererPanel(disposable, scope, renderModel) {

    override fun getRenderTransform(): AffineTransform? {
      return renderTransformProvider()
    }

    override fun getOverlayBounds(transform: AffineTransform): Rectangle? {
      return overlayBoundsProvider(transform)
    }
  }
}
