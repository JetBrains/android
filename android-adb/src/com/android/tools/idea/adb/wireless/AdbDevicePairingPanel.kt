/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import com.android.annotations.concurrency.UiThread
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JPanel

@UiThread
internal class AdbDevicePairingPanel {
  val rootComponent: JComponent by lazy {
    createPanel()
  }
  private var qrCodeImagePanel = QrCodePanel()

  fun setQrCodeImage(image: QrCodeImage) {
    qrCodeImagePanel.setQrCode(image)
    qrCodeImagePanel.setLabels("Scan the QR Code from you mobile", "phone to begin pairing")
  }

  private fun createPanel(): JComponent {
    val leftPanel = createQrCodePairingPanel()
    val rightPanel = createPinCodePairingPanel()

    // Create a non-movable splitter as a shortcut to create a view split 50/50
    // on the x-axis.
    // TODO: This should be done using a proper JSeparator and LayoutManager
    val splitter = Splitter(false, .5f, .5f, .5f)
    splitter.setResizeEnabled(false)
    splitter.firstComponent = leftPanel
    splitter.secondComponent = rightPanel
    splitter.isShowDividerControls = false
    splitter.setHonorComponentsMinimumSize(false)

    // Setup container panel
    // TODO: Add support for scroll bars (needed to properly support resizing)
    val rootPanel: JComponent = JPanel()
    val layout = BorderLayout()
    rootPanel.layout = layout
    rootPanel.add(splitter, BorderLayout.CENTER)
    return rootPanel
  }

  private fun createQrCodePairingPanel(): JComponent {
    val rootPanel: JComponent = JPanel()
    val layout = GroupLayout(rootPanel)

    val label1 = JBLabel("Pair using QR")
    scaleFontSize(label1, 1.2f)

    val label2 = JBLabel("Pair new devices by scanning QR Code")

    val qrCodePanel = createQrCodePanel()

    val bottomPanel = createQrCodeBottomPanel()

    val horizontalGroup: GroupLayout.Group = layout.createParallelGroup()
      .addComponent(label1)
      .addComponent(label2)
      .addComponent(qrCodePanel)
      .addComponent(bottomPanel)
    val verticalGroup: GroupLayout.Group = layout.createSequentialGroup()
      .addComponent(label1)
      .addComponent(label2)
      .addGap(JBUIScale.scale(20))
      .addComponent(qrCodePanel)
      .addComponent(bottomPanel)

    layout.autoCreateGaps = true
    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    rootPanel.layout = layout
    return rootPanel
  }

  private fun createPinCodePairingPanel(): JComponent {
    val rootPanel: JComponent = JPanel()
    val layout = GroupLayout(rootPanel)

    val label1 = JBLabel("Pair using pin code")
    scaleFontSize(label1, 1.2f)

    val label2 = JBLabel("Pair new devices manually using 6 digit code")

    val bottomPanel = createPairWithCodePanel()

    val horizontalGroup: GroupLayout.Group = layout.createParallelGroup()
      .addComponent(label1)
      .addComponent(label2)
      .addComponent(bottomPanel)

    val verticalGroup: GroupLayout.Group = layout.createSequentialGroup()
      .addComponent(label1)
      .addComponent(label2)
      .addGap(JBUIScale.scale(20))
      .addComponent(bottomPanel)

    layout.autoCreateGaps = true
    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    rootPanel.layout = layout
    return rootPanel
  }

  private fun scaleFontSize(comp: Component, @Suppress("SameParameterValue") scaleFactor: Float) {
    val font = comp.font
    comp.font = font.deriveFont(font.style, font.size.toFloat() * scaleFactor)
  }

  private fun createQrCodePanel(): JPanel {
    qrCodeImagePanel = QrCodePanel()
    return qrCodeImagePanel.component
  }

  private fun createQrCodeBottomPanel(): JComponent {
    val panel = JPanel(BorderLayout())

    val link = HyperlinkLabel("Where to find scanner on phone?")
    //TODO: Update with actual link
    link.setHyperlinkTarget("https://developer.android.com/docs")
    panel.add(link, BorderLayout.SOUTH)
    return panel
  }

  private fun createPairWithCodePanel(): JPanel {
    val rootPanel = JPanel()
    rootPanel.border = IdeBorderFactory.createBorder(Color.BLACK)
    rootPanel.minimumSize = JBDimension(100, 300)
    return rootPanel
  }
}
