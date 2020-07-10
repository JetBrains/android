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
class AdbDevicePairingControllerImpl(project: Project,
                                     edtExecutor: Executor,
                                     private val service: AdbDevicePairingService,
                                     private val view: AdbDevicePairingView
) : AdbDevicePairingController {
  private val edtExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val qrCodeScanningController = QrCodeScanningController(service, view, edtExecutor, this)

  init {
    // Ensure we are disposed when the project closes
    Disposer.register(project, this)

    // Ensure we are disposed when the view closes
    view.addListener(MyViewListener(this))
  }

  override fun startPairingProcess() {
    view.startAdbCheck()

    // Check ADB is valid and mDNS is supported on this platform
    service.isMdnsSupported().transform(edtExecutor) { mdnsIsSupported ->
      if (mdnsIsSupported) {
        view.showAdbCheckSuccess()
        qrCodeScanningController.startPairingProcess()
      } else {
        //TODO: Add error message (or failure cause) here?
        view.showAdbCheckError()
      }
    }

    // Note: This call is blocking and returns only when the dialog is closed
    view.showDialog()
  }

  override fun dispose() {
    // Nothing to do (the view or project disposal is what makes us being disposed)
  }

  class MyViewListener(private val parentDisposable: Disposable) : AdbDevicePairingView.Listener {
    override fun onClose() {
      Disposer.dispose(parentDisposable)
    }
  }
}
