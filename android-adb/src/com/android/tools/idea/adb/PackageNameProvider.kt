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
package com.android.tools.idea.adb

import com.android.tools.idea.concurrency.transform
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.ide.PooledThreadExecutor

object PackageNameProvider {
  private val logger: Logger = Logger.getInstance(PackageNameProvider::class.java)
  private val taskExecutor = PooledThreadExecutor.INSTANCE

  /**
   * Tries to infer the package name for [processName]. The package name is not always equal to the
   * process name, because of the `process` attribute that can be added to the app's manifest.
   *
   * On devices with api lower than R, the package name will just be inferred from the process name.
   */
  fun getPackageName(
    project: Project,
    deviceSerialNumber: String,
    processName: String,
  ): ListenableFuture<String> {
    val adb =
      AdbFileProvider.fromProject(project).get() ?: throw IllegalStateException("adb not found")

    return AdbService.getInstance().getDebugBridge(adb).transform(taskExecutor) { androidDebugBridge
      ->
      val device = androidDebugBridge.devices.find { deviceSerialNumber == it.serialNumber }
      if (device == null) {
        logger.info("Can't find device from serial number.")
        return@transform processName
      }
      val client = device.getClient(processName)
      val packageName = client.clientData.packageName
      if (packageName == null) {
        logger.info("Can't find package name.")
        processName
      } else {
        packageName
      }
    }
  }
}
