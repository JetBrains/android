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
import com.android.tools.idea.ui.SimpleDialog
import com.android.tools.idea.ui.SimpleDialogOptions
import com.android.utils.HtmlBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBDimension
import javax.swing.JComponent

@UiThread
class AdbDevicePairingDialog(project: Project, canBeParent: Boolean, ideModalityType: DialogWrapper.IdeModalityType) {
  private val dialog: SimpleDialog
  private val pairingPanel: AdbDevicePairingPanel

  init {
    val options = SimpleDialogOptions(project,
                                      canBeParent,
                                      ideModalityType,
                                      title = "Pair devices over Wi-Fi",
                                      isModal = true,
                                      hasOkButton = false,
                                      cancelButtonText = "Close",
                                      centerPanelProvider = { createCenterPanel() })
    dialog = SimpleDialog(options)
    pairingPanel = AdbDevicePairingPanel(dialog.disposable)
    dialog.init()
  }

  var pinCodePairInvoked: (MdnsService) -> Unit = {}

  var qrCodeScanAgainInvoked: () -> Unit = {}

  val disposable: Disposable
    get() = dialog.disposable

  fun createCenterPanel(): JComponent {
    // Set a preferred size so that the containing dialog shows big enough
    pairingPanel.rootComponent.preferredSize = panelPreferredSize
    pairingPanel.pinCodePairInvoked = { service -> this.pinCodePairInvoked(service) }
    pairingPanel.qrCodeScanAgainInvoked = { this.qrCodeScanAgainInvoked() }
    return pairingPanel.rootComponent
  }

  fun show() {
    dialog.show()
  }

  fun startLoading(text: String) {
    pairingPanel.isLoading = true
    pairingPanel.setLoadingText(text)
  }

  fun showLoadingError(html: HtmlBuilder) {
    pairingPanel.isLoading = false
    pairingPanel.setLoadingError(html)
  }

  fun stopLoading() {
    pairingPanel.isLoading = false
  }

  fun showPinCodeServices(services: List<MdnsService>) {
    pairingPanel.pinCodePanel.showAvailableServices(services)
  }

  fun setQrCodeImage(qrCodeImage: QrCodeImage) {
    pairingPanel.qrCodePanel.setQrCode(qrCodeImage)
  }

  fun showQrCodePairingStarted() {
    pairingPanel.qrCodePanel.showQrCodePairingStarted()
  }

  fun showQrCodePairingInProgress() {
    pairingPanel.qrCodePanel.showQrCodePairingInProgress()
  }

  fun showQrCodePairingWaitForDevice() {
    pairingPanel.qrCodePanel.showQrCodePairingWaitForDevice()
  }

  fun showQrCodePairingSuccess(device: AdbOnlineDevice) {
    pairingPanel.qrCodePanel.showQrCodePairingSuccess(device)
  }

  fun showQrCodePairingError(error: Throwable) {
    pairingPanel.qrCodePanel.showQrCodePairingError()
  }

  private val panelPreferredSize: JBDimension
    get() = JBDimension(600, 600)
}
