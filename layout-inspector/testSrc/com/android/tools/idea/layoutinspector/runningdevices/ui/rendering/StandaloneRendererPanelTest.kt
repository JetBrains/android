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
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.swing.fillRect
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

private val TEST_DATA_PATH = Path.of("tools", "adt", "idea", "layout-inspector", "testData")
private const val DIFF_THRESHOLD = 0.5

private val backgroundColor = Color(200, 200, 200)

class StandaloneRendererPanelTest {

  @get:Rule val testName = TestName()
  @get:Rule val edtRule = EdtRule()
  @get:Rule val disposableRule = DisposableRule()

  private val treeSettings = FakeTreeSettings(showRecompositions = false)
  private val renderSettings = FakeRenderSettings()

  private val disposable: Disposable
    get() = disposableRule.disposable

  /** The dimension of the canvas/panel */
  private val screenDimension = Dimension(500, 500)

  /** The dimension of the device screen (root view) */
  private val deviceScreenDimension = Dimension(200, 300)

  private val inspectorModel: InspectorModel
    get() =
      model(disposable) {
        view(ROOT, 0, 0, deviceScreenDimension.width, deviceScreenDimension.height) {
          view(VIEW1, 50, 50, 50, 50) { image() }
        }
      }

  @Test
  fun testRenderingCentered() {
    val (_, renderer) = createRenderer()
    renderer.setSize(screenDimension.width, screenDimension.height)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testRenderingScaled() {
    val (_, renderer) = createRenderer()
    renderer.setSize(screenDimension.width, screenDimension.height)

    // Zoom in to 200%
    renderSettings.scalePercent = 200

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  @RunsInEdt
  fun testPreferredSize() {
    val (_, renderer) = createRenderer()

    // 100% scale
    renderSettings.scalePercent = 100
    assertThat(renderer.preferredSize)
      .isEqualTo(Dimension(deviceScreenDimension.width, deviceScreenDimension.height))

    // 200% scale
    renderSettings.scalePercent = 200
    assertThat(renderer.preferredSize)
      .isEqualTo(Dimension((deviceScreenDimension.width * 2), (deviceScreenDimension.height * 2)))

    // 50% scale
    renderSettings.scalePercent = 50
    assertThat(renderer.preferredSize)
      .isEqualTo(
        Dimension(
          (deviceScreenDimension.width * 0.5).toInt(),
          (deviceScreenDimension.height * 0.5).toInt(),
        )
      )
  }

  @Test
  fun testHover() {
    val (model, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    val fakeUi = FakeUi(renderer)

    val view1ScreenPos = Point(200 + 10, 150 + 10)

    fakeUi.mouse.moveTo(view1ScreenPos.x, view1ScreenPos.y)
    fakeUi.render()

    assertThat(model.inspectorModel.hoveredNode?.drawId).isEqualTo(VIEW1)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testClickSelection() {
    val (model, renderer) = createRenderer()
    val parent = BorderLayoutPanel()
    parent.add(renderer)
    parent.size = screenDimension
    renderer.size = screenDimension

    val fakeUi = FakeUi(renderer)

    val view1ScreenPos = Point(210, 160)

    fakeUi.mouse.click(view1ScreenPos.x, view1ScreenPos.y)
    fakeUi.render()

    assertThat(model.inspectorModel.selection?.drawId).isEqualTo(VIEW1)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testChangingRootBoundsUpdatesCentering() {
    // Model with a different root size
    val smallRootModel =
      model(disposable) { view(ROOT, 0, 0, 100, 100) { view(VIEW1, 10, 10, 20, 20) { image() } } }

    val (_, renderer) = createRenderer(inspectorModel = smallRootModel)
    renderer.setSize(screenDimension.width, screenDimension.height)

    val renderImage = createRenderImage()
    paint(renderImage, renderer)
    assertSimilar(renderImage, testName.methodName)
  }

  private fun paint(image: BufferedImage, renderer: StandaloneRendererPanel) {
    val graphics = image.createGraphics()
    // add a gray background
    graphics.fillRect(
      Rectangle(0, 0, screenDimension.width, screenDimension.height),
      backgroundColor,
    )
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    renderer.paint(graphics)
    graphics.dispose()
  }

  private fun createRenderer(
    inspectorModel: InspectorModel = this.inspectorModel,
    scope: CoroutineScope = disposable.createCoroutineScope(),
  ): Pair<EmbeddedRendererModel, StandaloneRendererPanel> {
    val renderModel =
      EmbeddedRendererModel(
        parentDisposable = disposable,
        inspectorModel = inspectorModel,
        treeSettings = treeSettings,
        renderSettings = renderSettings,
        navigateToSelectedViewOnDoubleClick = {},
      )

    val panel =
      StandaloneRendererPanel(disposable = disposable, scope = scope, renderModel = renderModel)

    return Pair(renderModel, panel)
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
}
