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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import javax.swing.JFrame

@RunsInEdt
class ImageWithToolbarPanelTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun testSetImageWhenNotDisplayable() {
    val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)

    // This call should NOT trigger an exception or error log because we added the check for isDisplayable
    panel.setImage(null)
    assertFalse(panel.hasImage())

    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    panel.setImage(image)
    assertTrue(panel.hasImage())
  }

  @Test
  fun testSetImageWhenDisplayable() {
    if (GraphicsEnvironment.isHeadless()) {
      println("Skipping displayable test due to Headless environment")
      return
    }

    val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
    val frame = JFrame()

    try {
        frame.add(panel)
        frame.addNotify()

        assertTrue("Panel should be displayable", panel.isDisplayable)

        // Should perform update actions (implicitly tested by lack of exception and state change)
        val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        panel.setImage(image)

        assertTrue(panel.hasImage())

        panel.setImage(null)
        assertFalse(panel.hasImage())
    } catch (e: java.awt.HeadlessException) {
        println("Skipping displayable test due to HeadlessException")
    } finally {
        frame.dispose()
    }
  }

  @Test
  fun testZoomOperations() {
    val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    panel.setImage(image)

    val initialScale = panel.currentScale

    panel.zoomIn()
    assertTrue(panel.currentScale > initialScale)

    val zoomedInScale = panel.currentScale
    panel.zoomOut()
    assertTrue(panel.currentScale < zoomedInScale)
  }

  @Test
  fun testActualSize() {
    val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    panel.setImage(image)

    panel.zoomIn()
    assertNotEquals(1.0, panel.currentScale, 0.0)

    panel.setActualSize()
    assertEquals(1.0, panel.currentScale, 0.0)
  }

  @Test
  fun testFitToScreen() {
    val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
    val image = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB) // Larger image
    panel.setImage(image)

    // Set viewport size smaller than image
    panel.scrollPane.viewport.extentSize = Dimension(100, 100)

    panel.fitToScreen()

    // With viewport 100x100 and image 200x200, scale should be 0.5
    assertEquals(0.5, panel.currentScale, 0.01)
    assertTrue(panel.isAutoFitting)
  }

  @Test
  fun testToggles() {
    val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    panel.setImage(image)

    assertFalse(panel.isGridVisible())
    panel.setGridVisible(true)
    assertTrue(panel.isGridVisible())

    assertFalse(panel.isChessboardVisible())
    panel.setChessboardVisible(true)
    assertTrue(panel.isChessboardVisible())
  }

  @Test
  fun testPlaceholder() {
    val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
    val placeholderText = "Custom Placeholder"
    panel.setPlaceholder(placeholderText)
    panel.setImage(null)

    val viewportView = panel.scrollPane.viewport.view as? JBLabel
    assertNotNull(viewportView)
    assertEquals(placeholderText, viewportView?.text)
  }

  @Test
  fun testUpdateImageWithNoImage() {
    val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
    panel.setImage(null)

    // calling zoomIn shouldn't crash but also shouldn't do anything significant
    panel.zoomIn()
    assertFalse(panel.hasImage())
  }

  @Test
  fun testActionEnablement() {
     val panel = ImageWithToolbarPanel(ScreenshotViewType.NEW, showToolbar = true, showTitle = true)
     val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
     panel.setImage(image)

     assertTrue(panel.canZoomIn())
     assertTrue(panel.canZoomOut())

     // Zoom in until max
     for (i in 0..50) panel.zoomIn()
     assertFalse(panel.canZoomIn())

     // Zoom out until min
     for (i in 0..50) panel.zoomOut()
     assertFalse(panel.canZoomOut())
  }
}
