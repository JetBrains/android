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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.PropertySetterRule
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.WINDOW_MANAGER_FLAG_DIM_BEHIND
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.util.AndroidTestPaths
import com.intellij.testFramework.ProjectRule
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import javax.imageio.ImageIO

private val TEST_DATA_PATH = AndroidTestPaths.adtSources().resolve("layout-inspector/testData").toFile()
private const val DIFF_THRESHOLD = 0.02

class DeviceViewContentPanelTest {

  @get:Rule
  val chain = RuleChain.outerRule(ProjectRule()).around(DeviceViewSettingsRule())!!

  @get:Rule
  val clientFactoryRule = PropertySetterRule(
    { _, _ -> listOf(mock()) },
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
    assertEquals(Dimension(376, 394), panel.preferredSize)

    settings.scalePercent = 100
    assertEquals(Dimension(1020, 1084), panel.preferredSize)

    model.update(
      window(ROOT, ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50)
      }, listOf(ROOT), 0)
    // This is usually handled by a listener registered in DeviceViewPanel
    panel.model.refresh()
    assertEquals(Dimension(732, 820), panel.preferredSize)
  }

  @Test
  fun testPaint() {
    val model = model {
      view(ROOT, 0, 0, 500, 1000) {
        view(VIEW1, 125, 150, 250, 250) {
          image()
        }
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
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint.png"), generatedImage, DIFF_THRESHOLD)

    settings.scalePercent = 50
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_scaled.png"), generatedImage, DIFF_THRESHOLD)

    settings.scalePercent = 100
    panel.model.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_rotated.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.layerSpacing = 10
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_spacing1.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.layerSpacing = 200
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_spacing2.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.layerSpacing = INITIAL_LAYER_SPACING
    val windowRoot = model[ROOT]!!
    model.selection = windowRoot
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_selected.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawLabel = true
    model.selection = model[VIEW1]!!
    graphics = generatedImage.createGraphics()
    // Set the font so it will be the same across platforms
    graphics.font = Font("Droid Sans", Font.PLAIN, 12)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_label.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawBorders = false
    graphics = generatedImage.createGraphics()
    graphics.font = Font("Droid Sans", Font.PLAIN, 12)
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_noborders.png"), generatedImage, DIFF_THRESHOLD)

    model.hoveredNode = windowRoot
    graphics = generatedImage.createGraphics()
    graphics.font = Font("Droid Sans", Font.PLAIN, 12)
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_hovered.png"), generatedImage, DIFF_THRESHOLD)
  }


  @Test
  fun testRotationDoesntThrow() {
    val model = model {
      view(ROOT, 0, 0, 500, 1000) {
        // Use a RTL name to force TextLayout to be used
        view(VIEW1, 125, 150, 250, 250, qualifiedName = "שמי העברי") {
          image()
        }
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
      view(ROOT, 0, 0, 600, 600) {
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

    panel.model.overlay = ImageIO.read(File(TEST_DATA_PATH, "overlay.png"))

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_overlay-60.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.overlayAlpha = 0.2f
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_overlay-20.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.overlayAlpha = 0.9f
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaint_overlay-90.png"), generatedImage, DIFF_THRESHOLD)
  }

  @Test
  fun testDrag() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 25, 30, 50, 50) {
          image()
        }
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
        view(VIEW1, 0, 0, 50, 50) {
          image()
        }
      }
    }

    // Second window. Root doesn't overlap with top of first window--verify they're on separate levels in the drawing.
    val window2 = window(VIEW2, VIEW2, 60, 60, 30, 30) {
      view(VIEW3, 70, 70, 10, 10)
    }

    model.update(window2, listOf(ROOT, VIEW2), 0)

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 100)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaintMultiWindow.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[VIEW3]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaintMultiWindow_selected.png"), generatedImage,
                                     DIFF_THRESHOLD)

    panel.model.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaintMultiWindow_rotated.png"), generatedImage,
                                     DIFF_THRESHOLD)
  }

  @Test
  fun testPaintMultiWindowDimBehind() {
    val model = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50) {
          image()
        }
      }
    }

    // Second window. Root doesn't overlap with top of first window--verify they're on separate levels in the drawing.
    val window2 = window(VIEW2, VIEW2, 60, 60, 30, 30, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) {
      view(VIEW3, 70, 70, 10, 10)
    }

    model.update(window2, listOf(ROOT, VIEW2), 0)

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val settings = DeviceViewSettings(scalePercent = 100)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 300)
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintMultiWindowDimBehind.png"), generatedImage, DIFF_THRESHOLD)

    panel.model.rotate(0.3, 0.2)
    settings.scalePercent = 50
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaintMultiWindowDimBehind_rotated.png"), generatedImage,
                                     DIFF_THRESHOLD)
  }

  @Test
  fun testPaintWithImages() {
    val image1 = ImageIO.read(File(TEST_DATA_PATH, "image1.png"))
    val image2 = ImageIO.read(File(TEST_DATA_PATH, "image2.png"))
    val image3 = ImageIO.read(File(TEST_DATA_PATH, "image3.png"))

    val model = model {
      view(ROOT, 0, 0, 585, 804) {
        image(image1)
        view(VIEW1, 0, 100, 585, 585) {
          image(image2)
        }
        view(VIEW2, 100, 400, 293, 402) {
          image(image3)
        }
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
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaintWithImages.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[ROOT]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintWithImages_root.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[VIEW1]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintWithImages_view1.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[VIEW2]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintWithImages_view2.png"), generatedImage, DIFF_THRESHOLD)

    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintWithImages_view2.png"), generatedImage, DIFF_THRESHOLD)

    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintWithImages_view2.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawLabel = true
    graphics = generatedImage.createGraphics()
    // Set the font so it will be the same across platforms
    graphics.font = Font("Droid Sans", Font.PLAIN, 12)
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintWithImages_label.png"), generatedImage, DIFF_THRESHOLD)
  }

  @Suppress("UndesirableClassUsage")
  @Test
  fun testPaintWithImagesBetweenChildren() {
    val image1 = BufferedImage(100, 100, TYPE_INT_ARGB)
    image1.graphics.run {
      color = Color.RED
      fillRect(25, 25, 50, 50)
    }
    val image2 = BufferedImage(100, 100, TYPE_INT_ARGB)
    image2.graphics.run {
      color = Color.BLUE
      fillRect(0, 0, 60, 60)
    }
    val image3 = BufferedImage(50, 50, TYPE_INT_ARGB)
    image3.graphics.run {
      color = Color.GREEN
      fillRect(0, 0, 50, 50)
    }

    val model = model {
      view(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 0, 0, 100, 100) {
          image(image2)
        }
        image(image1)
        view(VIEW2, 50, 50, 50, 50) {
          image(image3)
        }
      }
    }

    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(1200, 1400, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val panel = DeviceViewContentPanel(model, DeviceViewSettings(scalePercent = 400))
    panel.setSize(1200, 1400)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImagesBetweenChildren.png"), generatedImage,
                                     DIFF_THRESHOLD)

    panel.model.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImagesBetweenChildren_rotated.png"),
                                     generatedImage, DIFF_THRESHOLD)

    model.selection = model[ROOT]
    graphics = generatedImage.createGraphics()
    graphics.font = Font("Droid Sans", Font.PLAIN, 12)
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintWithImagesBetweenChildren_root.png"), generatedImage, DIFF_THRESHOLD)
  }

  @Test
  fun testPaintWithRootImageOnly() {
    val image1 = ImageIO.read(File(TEST_DATA_PATH, "image1.png"))

    val model = model {
      view(ROOT, 0, 0, 585, 804) {
        image(image1)
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
    ImageDiffUtil.assertImageSimilar(File(TEST_DATA_PATH, "testPaintWithRootImageOnly.png"), generatedImage,
                                     DIFF_THRESHOLD)

    model.selection = model[ROOT]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintWithRootImageOnly_root.png"), generatedImage, DIFF_THRESHOLD)

    model.selection = model[VIEW1]
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(TEST_DATA_PATH, "testPaintWithRootImageOnly_view1.png"), generatedImage, DIFF_THRESHOLD)
  }

  @Test
  @Suppress("UndesirableClassUsage")
  fun testPaintTransformed() {
    val image1 = BufferedImage(220, 220, TYPE_INT_ARGB)
    (image1.graphics as Graphics2D).run {
      paint = GradientPaint(0f, 40f, Color.RED, 220f, 180f, Color.BLUE)
      fill(Polygon(intArrayOf(0, 180, 220, 40), intArrayOf(40, 0, 180, 220), 4))
    }

    val model = model {
      view(ROOT, 0, 0, 400, 600) {
        view(VIEW1, 50, 100, 300, 300, bounds = Polygon(intArrayOf(90, 270, 310, 130), intArrayOf(180, 140, 320, 360), 4)) {
          image(image1)
        }
      }
    }

    val generatedImage = BufferedImage(400, 600, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffUtil.getDefaultFont()

    val settings = DeviceViewSettings(scalePercent = 50)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(400, 600)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintTransformed.png"), generatedImage,
                                     DIFF_THRESHOLD)

    model.selection = model[VIEW1]
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintTransformed_view1.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawLabel = true
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintTransformed_label.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawUntransformedBounds = true
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintTransformed_untransformed.png"), generatedImage, DIFF_THRESHOLD)

    settings.drawBorders = false
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintTransformed_onlyUntransformed.png"), generatedImage, DIFF_THRESHOLD)
  }

  @Test
  @Suppress("UndesirableClassUsage")
  fun testPaintTransformedOutsideRoot() {
    val image1 = BufferedImage(80, 150, TYPE_INT_ARGB)
    (image1.graphics as Graphics2D).run {
      paint = GradientPaint(0f, 0f, Color.RED, 80f, 100f, Color.BLUE)
      fillRect(0, 0, 80, 100)
    }

    val model = model {
      view(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 20, 20, 60, 60, bounds = Polygon(intArrayOf(-20, 80, 80, -20), intArrayOf(-50, -50, 150, 150), 4)) {
          image(image1)
        }
      }
    }

    val generatedImage = BufferedImage(200, 200, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffUtil.getDefaultFont()

    val settings = DeviceViewSettings(scalePercent = 75)
    settings.drawLabel = false
    val panel = DeviceViewContentPanel(model, settings)
    panel.setSize(200, 200)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintTransformedOutsideRoot.png"), generatedImage,
                                     DIFF_THRESHOLD)

    model.selection = model[VIEW1]
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      File(getWorkspaceRoot(), "$TEST_DATA_PATH/testPaintTransformedOutsideRoot_view1.png"), generatedImage, DIFF_THRESHOLD)
  }
}