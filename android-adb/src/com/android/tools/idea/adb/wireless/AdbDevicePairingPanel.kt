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
import com.android.utils.HtmlBuilder
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.net.URL
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

@UiThread
internal class AdbDevicePairingPanel(private val parentDisposable: Disposable) {
  /**
   * URL to the "Learn mode" page
   *
   * TODO: Update to final URL
   */
  private val learnMoreUrl = "http://developer.android.com/docs"

  private val qrCodePanel by lazy { QrCodePanel() }
  private val pinCodePanel by lazy { PinCodePanel() }
  private val centerPanel by lazy { PairingCenterPanel() }

  private val loadingPanel: JBLoadingPanel by lazy {
    JBLoadingPanel(BorderLayout(), parentDisposable).apply {
      val centerComponent = createCenterPanel()
      add(centerComponent, BorderLayout.CENTER)
      name = "wifiPairing"
    }
  }

  val rootComponent: JComponent by lazy {
    JPanel(BorderLayout()).apply {
      val headerPanel = createHeaderPanel()
      add(headerPanel, BorderLayout.PAGE_START)
      add(loadingPanel, BorderLayout.CENTER)
    }
  }

  var isLoading: Boolean
    get() = loadingPanel.isLoading
    set(value) = if (value) {
      loadingPanel.startLoading()
      centerPanel.showEmptyContent()
    } else {
      centerPanel.showContent()
      loadingPanel.stopLoading()
    }

  fun setQrCodeImage(image: QrCodeImage) {
    qrCodePanel.setQrCode(image)
  }

  private fun createHelpLink(): JComponent {
    val link = HyperlinkLabel("Can't connect your device?")
    //TODO: Update with actual link
    link.setHyperlinkTarget("https://developer.android.com/docs")
    return link
  }

  private fun createHeaderPanel(): JComponent {
    val topLabel = JBLabel("Pair over Wi-Fi").apply {
      border = JBEmptyBorder(0, 0, 5, 0)
      font = JBUI.Fonts.label(16f).asBold()
    }

    val viewer: JEditorPane = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK)
    viewer.isOpaque = false
    viewer.isFocusable = false
    UIUtil.doNotScrollToCaret(viewer)
    viewer.addHyperlinkListener(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        val url: URL? = e.url
        if (url != null) {
          BrowserUtil.browse(url)
        }
      }
    })
    val htmlBuilder = HtmlBuilder().apply {
      add("Pair devices over Wi-Fi for wireless debugging scanning a QR code manually or using a 6 digit code.")
      add(" ")
      add("Wireless debugging allows for cable free workflows but can be slower than USB connection.")
      add("  ")
      addLink("Learn more", learnMoreUrl)
    }
    SwingHelper.setHtml(viewer, htmlBuilder.html, UIColors.HEADER_LABEL)

    return JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(10, 10, 15, 10)
      add(topLabel, BorderLayout.NORTH)
      add(viewer, BorderLayout.CENTER)
    }
  }

  private fun createCenterPanel(): JComponent {
    val qrCodePanel = qrCodePanel.component
    val pinCodePanel = pinCodePanel.component
    val helpLink = createHelpLink()

    val contentPanel = PairingContentPanel().apply {
      setQrCodeComponent(qrCodePanel)
      setPinCodeComponent(pinCodePanel)
      setHelpLinkComponent(helpLink)
    }.component

    return centerPanel.apply {
      setContentComponent(contentPanel)
    }.component
  }

  fun setQrCodePairingStatus(label: String) {
    qrCodePanel.setStatusLabel(label)
  }

  fun setLoadingText(text: String) {
    loadingPanel.setLoadingText(text)
    centerPanel.showEmptyContent()
  }

  fun setLoadingError(text: String) {
    loadingPanel.stopLoading()
    centerPanel.showError(text)
  }
}
