/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib.ddmlibcompatibility

import com.android.adblib.DeviceSelector
import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.IDevice
import com.android.tools.idea.adblib.AdbLibApplicationService
import com.intellij.openapi.application.ApplicationManager
import java.nio.file.Path
import java.nio.file.Paths

internal const val deprecationMessage =
  """This method was created during the migration from ddmlib to adblib.
     It blocks the caller thread so it should always be invoked on a
     @WorkerThread. It should also eventually be replaced by a call
     to the corresponding suspending method of adblib, or to
     a suspending method wrapped as a ListenableFuture."""

internal val defaultDdmTimeoutMillis
  get() = DdmPreferences.getTimeOut().toLong()

internal val deviceServices
  get() = AdbLibApplicationService.instance.session.deviceServices

/**
 * Returns a [DeviceSelector] instance that identifies this [IDevice]
 */
fun IDevice.toDeviceSelector(): DeviceSelector = DeviceSelector.fromSerialNumber(serialNumber)

internal fun throwIfDispatchThread() {
  @Suppress("UnstableApiUsage")
  ApplicationManager.getApplication().assertIsNonDispatchThread()
}

internal fun cancelCoroutine(message: String? = null) {
  // Note: This is the Kotlin coroutine cancellation exception
  throw kotlinx.coroutines.CancellationException(message)
}

internal suspend fun createNewFileChannel(localFilename: String) =
  AdbLibApplicationService.instance.session.channelFactory.createFile(Paths.get(localFilename))

internal suspend fun createOpenFileChannel(localFilename: String) =
  AdbLibApplicationService.instance.session.channelFactory.openFile(Paths.get(localFilename))

internal suspend fun createOpenFileChannel(path: Path) =
  AdbLibApplicationService.instance.session.channelFactory.openFile(path)

