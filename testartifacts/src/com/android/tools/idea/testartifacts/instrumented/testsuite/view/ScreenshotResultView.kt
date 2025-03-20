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

import com.android.annotations.concurrency.UiThread
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Image
import java.io.File
import javax.imageio.ImageIO
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * This is a placeholder for showing Screenshot Test Results.
 */
class ScreenshotResultView {

  val myView: JPanel = JPanel(BorderLayout())
  val myThreeWayDiffView: JPanel = JPanel()

  init {
    myView.add(JScrollPane(myThreeWayDiffView), BorderLayout.CENTER)
  }

  var newImagePath: String = ""
  var refImagePath: String = ""
  var diffImagePath: String = ""
  var testFailed: Boolean = false

  @UiThread
  fun getComponent(): JComponent {
    return myView
  }

  fun updateView() {
    val maxImgWidth = 600
    myThreeWayDiffView.removeAll()
    myThreeWayDiffView.setLayout(BoxLayout(myThreeWayDiffView, BoxLayout.X_AXIS))
    myThreeWayDiffView.add(Box.createHorizontalGlue())
    myThreeWayDiffView.add(createImageOrText(newImagePath, "No Preview Image", maxImgWidth))
    myThreeWayDiffView.add(Box.createHorizontalGlue())
    myThreeWayDiffView.add(createImageOrText(diffImagePath, "", maxImgWidth))
    myThreeWayDiffView.add(Box.createHorizontalGlue())
    myThreeWayDiffView.add(createImageOrText(refImagePath, "No Reference Image", maxImgWidth))
    myThreeWayDiffView.add(Box.createHorizontalGlue())
    myThreeWayDiffView.revalidate()
    myThreeWayDiffView.repaint()
  }

  private fun createImageOrText(filePath: String, orMessage: String, maxWidth: Int): JBLabel {
    val img = getImagesFromFile(filePath, maxWidth)
    return if (img == null) {
      JBLabel(orMessage)
    } else {
      JBLabel(ImageIcon(img))
    }
  }

  private fun getImagesFromFile(filePath: String, maxWidth: Int): Image? {
    val imageFile = File(filePath)
    if (!imageFile.exists()) {
      return null
    }
    val img = ImageIO.read(imageFile)
    return if (img.width <= maxWidth) {
      img
    } else {
      img.getScaledInstance(maxWidth, img.height * maxWidth / img.width, Image.SCALE_SMOOTH)
    }
  }
}