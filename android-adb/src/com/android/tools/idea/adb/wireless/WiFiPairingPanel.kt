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
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.function.Consumer
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkListener

@UiThread
internal class WiFiPairingPanel(
  private val parentDisposable: Disposable,
  private val hyperlinkListener: HyperlinkListener,
  private val mdnsServiceUnderPairing: TrackingMdnsService?,
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
      val headerContent = createHeaderPanel()
      val centerContent =
        JPanel(BorderLayout()).apply {
          // The dialog has an (8, 12) default padding. Apply the padding to the content below the
          // warning banner.
          border = JBUI.Borders.empty(8, 12)
          add(headerContent, BorderLayout.NORTH)
          add(loadingPanel, BorderLayout.CENTER)
          createNotificationBanner()?.let { add(it, BorderLayout.SOUTH) }
        }
      add(centerContent, BorderLayout.CENTER)
    }
  }

  val qrCodePanel by lazy { QrCodeTabPanel(Runnable { qrCodeScanAgainInvoked() }, parentDisposable, mdnsServiceUnderPairing) }

  val pairingCodePanel by lazy {
    PairingCodeTabPanel(Consumer<PairingMdnsService> { service -> pairingCodePairInvoked(service) }, mdnsServiceUnderPairing)
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

  var pairingCodePairInvoked: (PairingMdnsService) -> Unit = {}

  var qrCodeScanAgainInvoked: () -> Unit = {}

  private fun createNotificationBanner(): JComponent? {
    return DeviceNeedsUpdateBanner().takeIf { mdnsServiceUnderPairing != null && mdnsServiceUnderPairing.needsUpdate() }
  }

  private fun createHeaderPanel(): JComponent {
    val topLabel =
      JBLabel("Pair ${mdnsServiceUnderPairing?.displayString ?: "new devices"} over Wi-Fi").apply {
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
          setPairingCodeComponent(pairingCodePanel, mdnsServiceUnderPairing)
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

private class DeviceNeedsUpdateBanner : JPanel(BorderLayout()) {
  init {
    isOpaque = false
    border = JBUI.Borders.emptyTop(10)

    val container =
      JPanel(BorderLayout()).apply {
        background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND
        border =
          BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR, 1, true),
            JBUI.Borders.empty(10),
          )
      }

    val iconLabel =
      JBLabel(StudioIcons.Common.WARNING).apply {
        verticalAlignment = SwingConstants.TOP
        border = JBUI.Borders.emptyRight(10)
      }

    val textPanel =
      JPanel(GridBagLayout()).apply {
        isOpaque = false
        val gbc =
          GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
          }

        add(JBLabel("ADB Wi-Fi v1.0 device").apply { font = font.deriveFont(Font.BOLD) }, gbc)

        gbc.gridy++
        gbc.insets = JBUI.insetsTop(5)
        val message =
          "ADB Wi-Fi v1.0 has limited pairing capability. Update device to the latest API to use ADB Wi-Fi 2.0 or higher. Note: Some hardware may not support the latest API version."
        add(JBLabel("<html>$message</html>"), gbc)

        gbc.gridy++
        add(ActionLink("Learn more") { BrowserUtil.browse(Urls.learnMore) }, gbc)
      }

    container.add(iconLabel, BorderLayout.WEST)
    container.add(textPanel, BorderLayout.CENTER)

    add(container, BorderLayout.CENTER)
  }
}
