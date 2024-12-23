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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext

/** Handles the QR Code pairing aspect of the "Pair device over Wi-FI" dialog */
@UiThread
class QrCodeScanningController(
  private val service: WiFiPairingService,
  private val view: WiFiPairingView,
  parentDisposable: Disposable,
) : Disposable {
  private val LOG = logger<QrCodeScanningController>()
  private val modelListener = MyModelListener()
  private val viewListener = MyViewListener()
  private var state = State.Init
  private val scope = AndroidCoroutineScope(this)

  init {
    Disposer.register(parentDisposable, this)
    view.addListener(viewListener)
    view.model.addListener(modelListener)
  }

  override fun dispose() {
    view.model.removeListener(modelListener)
    view.removeListener(viewListener)
    state = State.Disposed
  }

  suspend fun startPairingProcess() {
    view.showQrCodePairingStarted()
    generateQrCode(view.model)
    state = State.Polling
    pollMdnsServices()
  }

  private suspend fun generateQrCode(model: WiFiPairingModel) {
    val qrCode = service.generateQrCode(UIColors.QR_CODE_BACKGROUND, UIColors.QR_CODE_FOREGROUND)
    model.qrCodeImage = qrCode
  }

  private fun startPairingDevice(mdnsService: MdnsService, password: String) {
    state = State.Pairing
    view.showQrCodePairingInProgress(mdnsService)
    scope.launch(uiThread(ModalityState.any())) {
      try {
        val pairingResult = service.pairMdnsService(mdnsService, password)
        view.showQrCodePairingWaitForDevice(pairingResult)
        val device = service.waitForDevice(pairingResult)
        state = State.PairingSuccess
        view.showQrCodePairingSuccess(mdnsService, device)
      } catch (error: Throwable) {
        if (!isCancelled(error)) {
          LOG.warn("Error pairing device ${mdnsService}", error)
          state = State.PairingError
          view.showQrCodePairingError(mdnsService, error)
        }
      }
    }
  }

  private fun isCancelled(error: Throwable): Boolean {
    return error is CancellationException
  }

  private fun pollMdnsServices() {
    scope.launch {
      // Don't start a new polling request if we are not in "polling" mode
      while (state == State.Polling) {
        try {
          val services = service.scanMdnsServices()
          withContext(uiThread(ModalityState.any())) {
            view.model.pairingCodeServices =
              services.filter { it.serviceType == ServiceType.PairingCode }
            view.model.qrCodeServices = services.filter { it.serviceType == ServiceType.QrCode }
          }
        } catch (e: Throwable) {
          // TODO: Should we show an error to the user?
          LOG.warn("Error scanning mDNS services", e)
        }

        // Run again in 1 second, unless we are disposed
        delay(Duration.ofSeconds(1))
      }
    }
  }

  enum class State {
    Init,
    Polling,
    Pairing,
    PairingError,
    PairingSuccess,
    Disposed,
  }

  @UiThread
  inner class MyViewListener : WiFiPairingView.Listener {
    override fun onScanAnotherQrCodeDeviceAction() {
      when (state) {
        State.PairingError,
        State.PairingSuccess -> {
          scope.launch(uiThread(ModalityState.any())) { startPairingProcess() }
        }
        else -> {
          // Ignore
        }
      }
    }

    override fun onPairingCodePairAction(mdnsService: MdnsService) {
      // Ignore
    }

    override fun onClose() {
      // Ignore
    }
  }

  @UiThread
  inner class MyModelListener : AdbDevicePairingModelListener {
    override fun qrCodeGenerated(newImage: QrCodeImage) {}

    override fun qrCodeServicesDiscovered(services: List<MdnsService>) {
      LOG.info("${services.size} QR code connect services discovered")
      services.forEachIndexed { index, it ->
        LOG.info(
          "  QR code connect service #${index + 1}: name=${it.serviceName} - ip=${it.ipAddress} - port=${it.port}"
        )
      }

      // If there is a QR Code displayed, look for a mDNS service with the same service name
      view.model.qrCodeImage?.let { qrCodeImage ->
        services
          .firstOrNull { it.serviceName == qrCodeImage.serviceName }
          ?.let {
            // We found the service we created, meaning the phone is in "pairing" mode
            startPairingDevice(it, qrCodeImage.password)
          }
      }
    }

    override fun pairingCodeServicesDiscovered(services: List<MdnsService>) {
      LOG.info("${services.size} pairing code pairing services discovered")
      services.forEachIndexed { index, it ->
        LOG.info(
          "  Pairing code pairing service #${index + 1}: name=${it.serviceName} - ip=${it.ipAddress} - port=${it.port}"
        )
      }
    }
  }
}
