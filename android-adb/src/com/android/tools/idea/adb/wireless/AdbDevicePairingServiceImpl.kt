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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.awt.Color
import java.util.concurrent.Executor

@UiThread
class AdbDevicePairingServiceImpl(private val randomProvider: RandomProvider,
                                  taskExecutor: Executor) : AdbDevicePairingService {
  private val taskExecutor = FutureCallbackExecutor.wrap(taskExecutor)

  override fun generateQrCode(backgroundColor: Color, foregroundColor: Color): ListenableFuture<QrCodeImage> {
    return taskExecutor.executeAsync {
      val service = createRandomString(10)
      val pairingString = createPairingString(service)
      val image = QrCodeGenerator.encodeQrCodeToImage(pairingString, backgroundColor, foregroundColor)
      QrCodeImage(service, pairingString, image)
    }
  }

  override fun devices(): ListenableFuture<List<AdbDevice>> {
    return Futures.immediateFuture(emptyList())
  }

  /**
   * Format is "WIFI:T:ADB;S:service;P:password;;" (without the quotes)
   */
  private fun createPairingString(service: String): String {
    return "WIFI:T:ADB;S:${service};P:${createRandomString(12)};;"
  }

  private fun createRandomString(charCount: Int): String {
    @Suppress("SpellCheckingInspection")
    val charSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-+*/<>{}"
    val sb = StringBuilder()
    for (i in 1..charCount) {
      val char = charSet[randomProvider.nextInt(charSet.length)]
      sb.append(char)
    }
    return sb.toString()
  }
}
