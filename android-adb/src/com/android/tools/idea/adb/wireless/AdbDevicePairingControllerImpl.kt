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
import com.android.tools.idea.concurrency.transform
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Executor

@UiThread
class AdbDevicePairingControllerImpl(private val project: Project,
                                     parentDisposable: Disposable,
                                     edtExecutor: Executor,
                                     private val pairingService: AdbDevicePairingService,
                                     private val view: AdbDevicePairingView,
                                     private val pinCodePairingControllerFactory: (MdnsService) -> PinCodePairingController = {
                                       createPinCodePairingController(project, edtExecutor, pairingService, it)
                                     }
) : AdbDevicePairingController {
  companion object {
    fun createPinCodePairingController(project: Project,
                                       edtExecutor: Executor,
                                       pairingService: AdbDevicePairingService,
                                       mdnsService: MdnsService): PinCodePairingController {
      val model = PinCodePairingModel(mdnsService)
      val view = PinCodePairingViewImpl(project, model)
      return PinCodePairingController(edtExecutor, pairingService, view)
    }
  }

  private val edtExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val qrCodeScanningController = QrCodeScanningController(pairingService, view, edtExecutor, this)

  private val viewListener = MyViewListener(this)

  init {
    // Ensure we are disposed when the project closes
    Disposer.register(parentDisposable, this)

    // Ensure we are disposed when the view closes
    view.addListener(viewListener)
  }

  override fun dispose() {
    view.removeListener(viewListener)
  }

  override fun showDialog() {
    view.startMdnsCheck()

    // Check ADB is valid and mDNS is supported on this platform
    pairingService.checkMdnsSupport().transform(edtExecutor) { supportState ->
      when(supportState) {
        MdnsSupportState.Supported -> {
          view.showMdnsCheckSuccess()
          qrCodeScanningController.startPairingProcess()
        }
        MdnsSupportState.NotSupported -> {
          view.showMdnsNotSupportedError()
        }
        MdnsSupportState.AdbVersionTooLow -> {
          view.showMdnsNotSupportedByAdbError()
        }
        MdnsSupportState.AdbInvocationError -> {
          view.showMdnsCheckError()
        }
      }
    }

    // Note: This call is blocking and returns only when the dialog is closed
    view.showDialog()
  }

  inner class MyViewListener(private val parentDisposable: Disposable) : AdbDevicePairingView.Listener {
    override fun onScanAnotherQrCodeDeviceAction() {
      // Ignore
    }

    override fun onPinCodePairAction(mdnsService: MdnsService) {
      pinCodePairingControllerFactory.invoke(mdnsService).showDialog()
    }

    override fun onClose() {
      Disposer.dispose(parentDisposable)
    }
  }
}
