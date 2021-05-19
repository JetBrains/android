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
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.finallySync
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

/**
 * Handles the QR Code pairing aspect of the "Pair device over Wi-FI" dialog
 */
@UiThread
class QrCodeScanningController(private val service: AdbDevicePairingService,
                               private val view: AdbDevicePairingView,
                               edtExecutor: Executor,
                               parentDisposable: Disposable) : Disposable {
  private val LOG = logger<QrCodeScanningController>()
  private val edtExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val pollingAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private val modelListener = MyModelListener()
  private val viewListener = MyViewListener()
  private var state = State.Init

  init {
    Disposer.register(parentDisposable, this)
    view.addListener(viewListener)
    view.model.addListener(modelListener)
  }

  override fun dispose() {
    pollingAlarm.cancelAllRequests()
    view.model.removeListener(modelListener)
    view.removeListener(viewListener)
    state = State.Disposed
  }

  fun startPairingProcess() {
    view.showQrCodePairingStarted()
    generateQrCode(view.model)
    state = State.Polling
    pollMdnsServices()
  }

  private fun generateQrCode(model: AdbDevicePairingModel) {
    val futureQrCode = service.generateQrCode(UIColors.QR_CODE_BACKGROUND, UIColors.QR_CODE_FOREGROUND)
    edtExecutor.transform(futureQrCode) {
      model.qrCodeImage = it
    }
  }

  private fun startPairingDevice(mdnsService: MdnsService, password: String) {
    state = State.Pairing
    view.showQrCodePairingInProgress(mdnsService)
    val futurePairing = service.pairMdnsService(mdnsService, password)
    futurePairing.transform(edtExecutor) { pairingResult ->
      cancelIfDisposed()
      view.showQrCodePairingWaitForDevice(pairingResult)
      pairingResult
    }.transformAsync(edtExecutor) { pairingResult ->
      cancelIfDisposed()
      service.waitForDevice(pairingResult)
    }.transform(edtExecutor) { device ->
      cancelIfDisposed()
      state = State.PairingSuccess
      view.showQrCodePairingSuccess(mdnsService, device)
    }.catching(edtExecutor, Throwable::class.java) { error ->
      if (!isCancelled(error)) {
        state = State.PairingError
        view.showQrCodePairingError(mdnsService, error)
      }
    }
  }

  private fun isCancelled(error: Throwable): Boolean {
    return error is CancellationException
  }

  private fun cancelIfDisposed() {
    if (state == State.Disposed) {
      throw CancellationException("Object has been disposed")
    }
  }

  private fun pollMdnsServices() {
    // Don't start a new polling request if we are not in "polling" mode
    if (state != State.Polling) {
      return
    }

    val futureServices = service.scanMdnsServices()
    edtExecutor.transform(futureServices) { services ->
      view.model.pinCodeServices = services.filter { it.serviceType == ServiceType.PinCode }
      view.model.qrCodeServices = services.filter { it.serviceType == ServiceType.QrCode }
    }.catching(edtExecutor, Throwable::class.java) {
      //TODO: Display/log error
    }.finallySync(edtExecutor) {
      // Run again in 1 second, unless we are disposed
      if (!Disposer.isDisposed(this)) {
        pollingAlarm.addRequest({ pollMdnsServices() }, 1_000)
      }
    }
  }

  enum class State {
    Init,
    Polling,
    Pairing,
    PairingError,
    PairingSuccess,
    Disposed
  }

  @UiThread
  inner class MyViewListener : AdbDevicePairingView.Listener {
    override fun onScanAnotherQrCodeDeviceAction() {
      when(state) {
        State.PairingError, State.PairingSuccess -> {
          startPairingProcess()
        }
        else -> {
          // Ignore
        }
      }
    }

    override fun onPinCodePairAction(mdnsService: MdnsService) {
      // Ignore
    }

    override fun onClose() {
      // Ignore
    }
  }

  @UiThread
  inner class MyModelListener : AdbDevicePairingModelListener {
    override fun qrCodeGenerated(newImage: QrCodeImage) {
    }

    override fun qrCodeServicesDiscovered(services: List<MdnsService>) {
      LOG.info("${services.size} QR code connect services discovered")
      services.forEachIndexed { index, it ->
        LOG.info("  QR code connect service #${index + 1}: name=${it.serviceName} - ip=${it.ipAddress} - port=${it.port}")
      }

      // If there is a QR Code displayed, look for a mDNS service with the same service name
      view.model.qrCodeImage?.let { qrCodeImage ->
        services.firstOrNull { it.serviceName == qrCodeImage.serviceName }
          ?.let {
            // We found the service we created, meaning the phone is in "pairing" mode
            startPairingDevice(it, qrCodeImage.password)
          }
      }
    }

    override fun pinCodeServicesDiscovered(services: List<MdnsService>) {
      LOG.info("${services.size} Pin code pairing services discovered")
      services.forEachIndexed { index, it ->
        LOG.info("  Pin code pairing service #${index + 1}: name=${it.serviceName} - ip=${it.ipAddress} - port=${it.port}")
      }
    }
  }
}