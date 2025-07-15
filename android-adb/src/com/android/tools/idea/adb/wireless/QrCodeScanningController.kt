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
import com.android.sdklib.deviceprovisioner.SetChange.Add
import com.android.sdklib.deviceprovisioner.SetChange.Remove
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.WifiPairingEvent.PairingMethod.QR_CODE
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext

/** Handles the QR Code pairing aspect of the "Pair device over Wi-FI" dialog */
@UiThread
class QrCodeScanningController(
  private val service: WiFiPairingService,
  private val view: WiFiPairingView,
  parentDisposable: Disposable,
  private val mdnsServiceUnderPairing: TrackingMdnsService?,
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
    if (StudioFlags.WIFI_V2_ENABLED.get() && service.isTrackMdnsServiceAvailable()) {
      startMdnsTrackingService()
    } else {
      pollMdnsServices()
    }
  }

  private suspend fun generateQrCode(model: WiFiPairingModel) {
    val qrCode = service.generateQrCode(UIColors.QR_CODE_BACKGROUND, UIColors.QR_CODE_FOREGROUND)
    model.qrCodeImage = qrCode
  }

  private fun startPairingDevice(pairingMdnsService: PairingMdnsService, password: String) {
    state = State.Pairing
    view.showQrCodePairingInProgress(pairingMdnsService)
    scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val now = System.currentTimeMillis()
      val adbVersion = service.getAdbVersion()
      try {
        val pairingResult = service.pairMdnsService(pairingMdnsService, password)
        view.showQrCodePairingWaitForDevice(pairingResult)
        val device = service.waitForDevice(pairingResult)
        WifiPairingUsageTracker.trackSuccess(
          adbVersion,
          QR_CODE,
          device.properties["ro.build.version.sdk"],
          device.properties["ro.build.version.codename"],
          System.currentTimeMillis() - now,
        )
        state = State.PairingSuccess
        view.showQrCodePairingSuccess(pairingMdnsService, device)
      } catch (error: Throwable) {
        if (!isCancelled(error)) {
          WifiPairingUsageTracker.trackFailure(
            adbVersion,
            QR_CODE,
            error,
            System.currentTimeMillis() - now,
          )
          LOG.warn("Error pairing device ${pairingMdnsService}", error)
          state = State.PairingError
          view.showQrCodePairingError(pairingMdnsService, error)
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
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
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

  private suspend fun startMdnsTrackingService() {
    service
      .trackMdnsServices()
      .map { it.pairingMdnsServices.toSet() }
      .trackSetChanges()
      .takeWhile { state == State.Polling }
      .collect {
        val newServices =
          when (it) {
            is Add -> {
              listOf(
                PairingMdnsService(
                  it.value.mdnsService.serviceInstanceName.instance,
                  if (it.value.mdnsService.serviceInstanceName.instance.startsWith("studio-"))
                    ServiceType.QrCode
                  else ServiceType.PairingCode,
                  InetAddress.getByName(it.value.mdnsService.ipv4),
                  it.value.mdnsService.port,
                )
              )
            }
            is Remove -> {
              emptyList()
            }
          }
        view.model.pairingCodeServices =
          newServices.filter {
            it.serviceType == ServiceType.PairingCode &&
              (mdnsServiceUnderPairing == null ||
                mdnsServiceUnderPairing.serviceName == it.serviceName)
          }
        view.model.qrCodeServices = newServices.filter { it.serviceType == ServiceType.QrCode }
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
          scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            startPairingProcess()
          }
        }
        else -> {
          // Ignore
        }
      }
    }

    override fun onPairingCodePairAction(pairingMdnsService: PairingMdnsService) {
      // Ignore
    }

    override fun onClose() {
      // Ignore
    }
  }

  @UiThread
  inner class MyModelListener : AdbDevicePairingModelListener {
    override fun qrCodeGenerated(newImage: QrCodeImage) {}

    override fun qrCodeServicesDiscovered(services: List<PairingMdnsService>) {
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

    override fun pairingCodeServicesDiscovered(services: List<PairingMdnsService>) {
      LOG.info("${services.size} pairing code pairing services discovered")
      services.forEachIndexed { index, it ->
        LOG.info(
          "  Pairing code pairing service #${index + 1}: name=${it.serviceName} - ip=${it.ipAddress} - port=${it.port}"
        )
      }
    }
  }
}
