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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt
import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.jetbrains.rd.swing.fillRect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JPanel
import kotlin.io.path.pathString

private val TEST_DATA_PATH = Path.of("tools", "adt", "idea", "layout-inspector", "testData")
private const val DIFF_THRESHOLD = 0.2

class LayoutInspectorRendererTest {

  @get:Rule
  val testName = TestName()

  private val inspectorModel = model {
    view(ROOT, 0, 0, 100, 150) {
      view(VIEW1, 10, 15, 25, 25) {
        image()
      }
      compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
    }
  }

  private val treeSettings = FakeTreeSettings()
  private val renderSettings = FakeRenderSettings()

  private lateinit var renderModel: RenderModel
  private lateinit var renderLogic: RenderLogic

  private val screenDimension = Dimension(200, 250)
  private val deviceFrameDimension = Dimension(100, 150)

  private val deviceFrame = Rectangle(10, 10, deviceFrameDimension.width, deviceFrameDimension.height)
  private lateinit var component: Component

  @Before
  fun setUp() {
    renderModel = RenderModel(inspectorModel, treeSettings) { MockitoKt.mock() }
    renderLogic = RenderLogic(renderModel, renderSettings)

    component = JPanel().apply {
      size = screenDimension
    }
  }

  @Test
  fun testViewBordersAreRendered() {
    val layoutInspectorRenderer = LayoutInspectorRenderer(renderLogic, renderModel, component)

    @Suppress("UndesirableClassUsage")
    val renderImage = BufferedImage(screenDimension.width, screenDimension.height, BufferedImage.TYPE_INT_ARGB)
    paint(renderImage, screenDimension, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testOverlayIsRendered() {
    val layoutInspectorRenderer = LayoutInspectorRenderer(renderLogic, renderModel, component)

    renderModel.overlay = ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("${TEST_DATA_PATH}/overlay.png").toFile())

    @Suppress("UndesirableClassUsage")
    val renderImage = BufferedImage(screenDimension.width, screenDimension.height, BufferedImage.TYPE_INT_ARGB)
    paint(renderImage, screenDimension, layoutInspectorRenderer)
    assertSimilar(renderImage, testName.methodName)
  }

  private fun paint(image: BufferedImage, renderDimension: Dimension, layoutInspectorRenderer: LayoutInspectorRenderer) {
    val graphics = image.createGraphics()
    // add a gray background
    graphics.fillRect(Rectangle(0, 0, renderDimension.width, renderDimension.height), Color(250, 250, 250))
    graphics.font = ImageDiffTestUtil.getDefaultFont()

    layoutInspectorRenderer.paint(graphics, deviceFrame)
  }

  /**
   * Check that the generated [renderImage] is similar to the one stored on disk.
   * If the image stored on disk does not exist, it is created.
   */
  private fun assertSimilar(renderImage: BufferedImage, imageName: String) {
    val testDataPath = TEST_DATA_PATH.resolve(this.javaClass.simpleName)
    ImageDiffUtil.assertImageSimilar(
      TestUtils.resolveWorkspacePathUnchecked(testDataPath.resolve("$imageName.png").pathString), renderImage, DIFF_THRESHOLD
    )
  }
}