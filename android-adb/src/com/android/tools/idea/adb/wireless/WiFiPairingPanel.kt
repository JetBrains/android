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
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.HyperlinkListener

@UiThread
internal class WiFiPairingPanel(
  private val parentDisposable: Disposable,
  private val hyperlinkListener: HyperlinkListener,
) {
  private val centerPanel by lazy { WiFiPairingCenterPanel(hyperlinkListener) }

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

  val qrCodePanel by lazy {
    QrCodeTabPanel(Runnable { qrCodeScanAgainInvoked() }, parentDisposable)
  }

  val pairingCodePanel by lazy {
    PairingCodeTabPanel(Consumer<MdnsService> { service -> pairingCodePairInvoked(service) })
  }

  var isLoading: Boolean
    get() = loadingPanel.isLoading
    set(value) =
      if (value) {
        loadingPanel.startLoading()
        centerPanel.showEmptyContent()
      } else {
        centerPanel.showContent()
        loadingPanel.stopLoading()
      }

  var pairingCodePairInvoked: (MdnsService) -> Unit = {}

  var qrCodeScanAgainInvoked: () -> Unit = {}

  private fun createHeaderPanel(): JComponent {
    val topLabel =
      JBLabel("Pair new devices over Wi-Fi").apply {
        border = JBEmptyBorder(0, 0, 10, 0)
        font = JBUI.Fonts.label(22f) // .asBold()
      }

    val editorPane = createHtmlEditorPane()
    editorPane.addHyperlinkListener(hyperlinkListener)
    val htmlBuilder =
      HtmlBuilder().apply {
        add("Pair devices to enable wireless debugging.")
        add(" ")
        add(" ")
        add("Other devices can be paired using a pairing code.")
        add("  ")
        addLink("Learn more", Urls.learnMore)
        add(".")
      }
    editorPane.setHtml(htmlBuilder, UIColors.HEADER_LABEL)

    return JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(10, 10, 22, 10)
      add(topLabel, BorderLayout.NORTH)
      add(editorPane, BorderLayout.CENTER)
    }
  }

  private fun createCenterPanel(): JComponent {
    val qrCodePanel = qrCodePanel.component
    val pairingCodePanel = pairingCodePanel.component

    val contentPanel =
      WiFiPairingContentPanel(parentDisposable)
        .apply {
          setQrCodeComponent(qrCodePanel)
          setPairingCodeComponent(pairingCodePanel)
        }
        .component

    return centerPanel.apply { setContentComponent(contentPanel) }.component
  }

  fun setLoadingText(text: String) {
    loadingPanel.setLoadingText(text)
    centerPanel.showEmptyContent()
  }

  fun setLoadingError(html: HtmlBuilder) {
    loadingPanel.stopLoading()
    centerPanel.showError(html)
  }
}
