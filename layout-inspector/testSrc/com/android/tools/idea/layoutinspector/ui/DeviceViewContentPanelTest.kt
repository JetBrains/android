/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.PropertySetterRule
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.WINDOW_MANAGER_FLAG_DIM_BEHIND
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.view
import com.intellij.testFramework.ProjectRule
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import javax.imageio.ImageIO

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"
private const val DIFF_THRESHOLD = 0.05

class DeviceViewContentPanelTest {

  @get:Rule
  val chain = RuleChain.outerRule(ProjectRule()).around(DeviceViewSettingsRule())!!

  @get:Rule
  val clientFactoryRule = PropertySetterRule(
    { _, _ -> listOf(mock(InspectorClient::class.java)) },
    InspectorClient.Companion::clientFactory)

  @Test
  fun testSize() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50) {
          view(VIEW3, 30, 30, 10, 10)
        }
        view(VIEW2, 60, 160, 10, 20)
      }
    }
    val settings = DeviceViewSettings(scalePercent = 30)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    assertEquals(Dimension(188, 197), panel.preferredSize)

    settings.scalePercent = 100
    assertEquals(Dimension(510, 542), panel.preferredSize)

    model.update(
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50)
      }, ROOT, listOf(ROOT))
    assertEquals(Dimension(366, 410), panel.preferredSize)
  }

  @Test
  fun testPaint() {
    val model = model {
      view(ROOT, 0, 0, 500, 1000) {
        view(VIEW1, 125, 150, 250, 250, imageBottom = mock(Image::class.java))
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(1000, 1500, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 100)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(1000, 1500)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint.png"), generatedImage, DIFF_THRESHOLD)

    settings.scalePercent = 50
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_scaled.png"), generatedImage, DIFF_THRESHOLD)

    settings.scalePercent = 100
    panel.model.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_rotated.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.layerSpacing = 10
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_spacing1.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.layerSpacing = 200
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_spacing2.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.layerSpacing = INITIAL_LAYER_SPACING
    val windowRoot = model[ROOT]!!
    model.selection = windowRoot
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_selected.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawLabel = true
    model.selection = model[VIEW1]!!
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_label.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawBorders = false
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_noborders.png"), generatedImage, DIFF_THRESHOLD)

    model.hoveredNode = windowRoot
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_hovered.png"), generatedImage, DIFF_THRESHOLD)
  }


  @Test
  fun testRotationDoesntThrow() {
    val model = model {
      view(ROOT, 0, 0, 500, 1000) {
        // Use a RTL name to force TextLayout to be used
        view(VIEW1, 125, 150, 250, 250, qualifiedName = "שמי העברי", imageBottom = mock(Image::class.java))
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(10, 15, TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()

    model.selection = model[VIEW1]!!
    val panel = DeviceViewContentPanel(model, DeviceViewSettings())
    panel.setSize(10, 15)
    panel.model.rotate(-1.0, -1.0)

    for (i in 0..20) {
      panel.model.rotate(-2.0, 0.1)
      for (j in 0..20) {
        panel.model.rotate(0.1, 0.0)
        panel.paint(graphics)
      }
    }
  }

  @Test
  fun testOverlay() {
    val model = model {
      view(ROOT, 0, 0, 500, 1000) {
        view(VIEW1, 125, 150, 250, 250)
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(1000, 1500, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 100)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(1000, 1500)

    panel.model.overlay = ImageIO.read(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_hovered.png"))

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_overlay-60.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.overlayAlpha = 0.2f
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_overlay-20.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.overlayAlpha = 0.9f
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaint_overlay-90.png"), generatedImage, DIFF_THRESHOLD)
  }

  @Test
  fun testClipping() {
    val model = model {
      view(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 25, 50, 50, 100)
      }
    }

    @Suppress("UndesirableClassUsage")
    val childImage = BufferedImage(50, 100, TYPE_INT_ARGB)
    val childImageGraphics = childImage.createGraphics()
    childImageGraphics.color = Color.RED
    childImageGraphics.fillOval(0, 0, 50, 100)

    model[VIEW1]!!.imageBottom = childImage

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 50)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testClip.png"), generatedImage, DIFF_THRESHOLD)
  }

  @Test
  fun testDrag() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50, imageBottom = mock(Image::class.java))
      }
    }

    val settings = DeviceViewSettings(scalePercent = 100)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)
    val fakeUi = FakeUi(panel)

    fakeUi.mouse.drag(10, 10, 10, 10)
    assertEquals(0.01, panel.model.xOff)
    assertEquals(0.01, panel.model.yOff)

    panel.model.resetRotation()
    assertEquals(0.0, panel.model.xOff)
    assertEquals(0.0, panel.model.yOff)
  }

  @Test
  fun testPaintMultiWindow() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50, imageBottom = mock(Image::class.java))
      }
    }

    // Second window. Root doesn't overlap with top of first window--verify they're on separate levels in the drawing.
    val window2 = view(VIEW2, 60, 60, 30, 30) {
      view(VIEW3, 70, 70, 10, 10)
    }

    model.update(window2, VIEW2, listOf(ROOT, VIEW2))

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 100)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintMultiWindow.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[VIEW3]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintMultiWindow_selected.png"), generatedImage,
                                     DIFF_THRESHOLD)

    panel.model.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintMultiWindow_rotated.png"), generatedImage,
                                     DIFF_THRESHOLD)
  }

  @Test
  fun testPaintMultiWindowDimBehind() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50, imageBottom = mock(Image::class.java))
      }
    }

    // Second window. Root doesn't overlap with top of first window--verify they're on separate levels in the drawing.
    val window2 = view(VIEW2, 60, 60, 30, 30, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) {
      view(VIEW3, 70, 70, 10, 10)
    }

    model.update(window2, VIEW2, listOf(ROOT, VIEW2))

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 100)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintMultiWindowDimBehind.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.rotate(0.3, 0.2)
    settings.scalePercent = 50
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintMultiWindowDimBehind_rotated.png"), generatedImage,
                                     DIFF_THRESHOLD)
  }

  @Test
  fun testPaintWithImages() {
    val image1 = ImageIO.read(File(getWorkspaceRoot(), "$TEST_DATA_PATH/image1.png"))
    val image2 = ImageIO.read(File(getWorkspaceRoot(), "$TEST_DATA_PATH/image2.png"))
    val image3 = ImageIO.read(File(getWorkspaceRoot(), "$TEST_DATA_PATH/image3.png"))

    val model = model {
      view(ROOT, 0, 0, 585, 804, imageBottom = image1) {
        view(VIEW1, 0, 100, 585, 585, imageBottom = image2)
        view(VIEW2, 100, 400, 293, 402, imageBottom = image3)
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(350, 450, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 50)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(350, 450)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImages.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[ROOT]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImages_root.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[VIEW1]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImages_view1.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[VIEW2]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImages_view2.png"), generatedImage, DIFF_THRESHOLD)

    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImages_view2.png"), generatedImage, DIFF_THRESHOLD)

    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImages_view2.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawLabel = true
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImages_label.png"), generatedImage, DIFF_THRESHOLD)
  }

  @Test
  fun testPaintWithRootImageOnly() {

    val image1 = ImageIO.read(File(getWorkspaceRoot(), "$TEST_DATA_PATH/image1.png"))

    val model = model {
      view(ROOT, 0, 0, 585, 804, imageBottom = image1) {
        view(VIEW1, 0, 100, 585, 585)
        view(VIEW2, 100, 400, 293, 402)
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(350, 450, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 50)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(350, 450)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithRootImageOnly.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[ROOT]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithRootImageOnly_root.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[VIEW1]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithRootImageOnly_view1.png"), generatedImage, DIFF_THRESHOLD)
  }
}