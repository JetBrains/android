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
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.Image
import java.io.File
import javax.imageio.ImageIO
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.border.EmptyBorder

/**
 * This is a placeholder for showing Screenshot Test Results.
 */
class ScreenshotResultView {

  val myView: JPanel = JPanel(BorderLayout())
  val myTabbedPane = JBTabbedPane()
  val myThreeWayDiffView: JPanel = JPanel()
  val myNewImageView: JPanel = JPanel(GridBagLayout())
  val myDiffImageView: JPanel = JPanel(GridBagLayout())
  val myRefImageView: JPanel = JPanel(GridBagLayout())

  init {
    myTabbedPane.tabPlacement = JBTabbedPane.BOTTOM

    myTabbedPane.addTab("All", JScrollPane(myThreeWayDiffView))
    myTabbedPane.addTab("New", JScrollPane(myNewImageView))
    myTabbedPane.addTab("Diff", JScrollPane(myDiffImageView))
    myTabbedPane.addTab("Reference", JScrollPane(myRefImageView))

    myTabbedPane.selectedIndex = 0

    myView.add(myTabbedPane, BorderLayout.CENTER)
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
    val maxImgWidth = 400
    val singleViewMaxWidth = 500
    val imageGap = 50
    val verticalGap = 50

    myThreeWayDiffView.removeAll()
    myThreeWayDiffView.setLayout(BoxLayout(myThreeWayDiffView, BoxLayout.X_AXIS))

    myThreeWayDiffView.border = EmptyBorder(verticalGap, 0, verticalGap, 0)
    myNewImageView.border = EmptyBorder(verticalGap, 0, verticalGap, 0)
    myDiffImageView.border = EmptyBorder(verticalGap, 0, verticalGap, 0)
    myRefImageView.border = EmptyBorder(verticalGap, 0, verticalGap, 0)

    myThreeWayDiffView.add(Box.createHorizontalStrut(imageGap))
    myThreeWayDiffView.add(createImageOrText(newImagePath, "No Preview Image", maxImgWidth))
    myThreeWayDiffView.add(Box.createHorizontalStrut(imageGap))
    myThreeWayDiffView.add(createImageOrText(diffImagePath, "", maxImgWidth))
    myThreeWayDiffView.add(Box.createHorizontalStrut(imageGap))
    myThreeWayDiffView.add(createImageOrText(refImagePath, "No Reference Image", maxImgWidth))
    myThreeWayDiffView.add(Box.createHorizontalStrut(imageGap))

    updateSingleImageView(myNewImageView, newImagePath, "No Preview Image", singleViewMaxWidth)
    updateSingleImageView(myDiffImageView, diffImagePath, "No Diff Image", singleViewMaxWidth)
    updateSingleImageView(myRefImageView, refImagePath, "No Reference Image", singleViewMaxWidth)

    myView.revalidate()
    myView.repaint()
  }

  private fun updateSingleImageView(panel: JPanel, imagePath: String, message: String, maxWidth: Int) {
    panel.removeAll()
    panel.add(createImageOrText(imagePath, message, maxWidth))
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