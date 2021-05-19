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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer

@UiThread
class AdbDevicePairingViewImpl(val project: Project, override val model: AdbDevicePairingModel) : AdbDevicePairingView {
  private val dlg: AdbDevicePairingDialog
  private val listeners = ArrayList<AdbDevicePairingView.Listener>()

  init {
    // Note: No need to remove the listener, as the Model and View have the same lifetime
    model.addListener(ModelListener())
    dlg = AdbDevicePairingDialog(project, true, DialogWrapper.IdeModalityType.PROJECT)
    dlg.pinCodePairInvoked = { service ->
      listeners.forEach { it.onPinCodePairAction(service) }
    }
    dlg.qrCodeScanAgainInvoked = {
      listeners.forEach { it.onScanAnotherQrCodeDeviceAction() }
    }
    Disposer.register(dlg.disposable, Disposable {
      // Note: Create a copy of the listener list in case one of the listener removes
      //       itself in its [onClose] implementation.
      listeners.toList().forEach { it.onClose() }
    })
  }

  override fun showDialog() {
    dlg.show()
  }

  override fun startMdnsCheck() {
    dlg.startLoading("Preparing Wi-Fi pairing...")
  }

  override fun showMdnsCheckSuccess() {
    updateQrCodeImage(model.qrCodeImage)
    dlg.stopLoading()
  }

  override fun showMdnsNotSupportedError() {
    dlg.showLoadingError(buildErrorHtml(arrayOf(
      "This system does not meet the requirements to support Wi-Fi pairing.",
      "Please update to the latest version of \"platform-tools\" using the SDK manager."
    )))
  }

  override fun showMdnsNotSupportedByAdbError() {
    dlg.showLoadingError(buildErrorHtml(arrayOf(
      "The currently installed version of the \"Android Debug Bridge\" (adb) does not support Wi-Fi pairing.",
      "Please update to the latest version of \"platform-tools\" using the SDK manager."
    )))
  }

  override fun showMdnsCheckError() {
    dlg.showLoadingError(buildErrorHtml(arrayOf(
      "There was an unexpected error during Wi-Fi pairing initialization."
    )))
  }

  private fun buildErrorHtml(lines: Array<String>): HtmlBuilder {
    return HtmlBuilder().apply {
      beginDiv("text-align: center;")
      lines.forEach { line ->
        add(line)
        newline()
      }
      newline()
      addLink("Learn more", Urls.learnMore)
      endDiv()
    }
  }

  override fun showQrCodePairingStarted() {
    dlg.showQrCodePairingStarted()
  }

  override fun showQrCodePairingInProgress(mdnsService: MdnsService) {
    dlg.showQrCodePairingInProgress()
  }

  override fun showQrCodePairingWaitForDevice(pairingResult: PairingResult) {
    dlg.showQrCodePairingWaitForDevice()
  }

  override fun showQrCodePairingSuccess(mdnsService: MdnsService, device: AdbOnlineDevice) {
    dlg.showQrCodePairingSuccess(device)
  }

  override fun showQrCodePairingError(mdnsService: MdnsService, error: Throwable) {
    dlg.showQrCodePairingError(error)
  }

  override fun addListener(listener: AdbDevicePairingView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: AdbDevicePairingView.Listener) {
    listeners.remove(listener)
  }

  private fun updateQrCodeImage(image: QrCodeImage?) {
    image?.let { dlg.setQrCodeImage(it) }
  }

  @UiThread
  private inner class ModelListener : AdbDevicePairingModelListener {
    override fun qrCodeGenerated(newImage: QrCodeImage) {
      updateQrCodeImage(newImage)
    }

    override fun qrCodeServicesDiscovered(services: List<MdnsService>) {
      // Ignore, as this is handled by the controller via the model
    }

    override fun pinCodeServicesDiscovered(services: List<MdnsService>) {
      //TODO: Move logic to controller?
      dlg.showPinCodeServices(services)
    }
  }
}
