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
import com.intellij.openapi.Disposable

/**
 * Interface definition of the ADB device pairing controller.
 */
@UiThread
interface AdbDevicePairingController : Disposable {
  /**
   * Starts the modal dialog allowing pairing devices using QR codes and pin codes.
   * The newly paired devices are added to the underlying adb service implementation.
   */
  fun showDialog()
}
