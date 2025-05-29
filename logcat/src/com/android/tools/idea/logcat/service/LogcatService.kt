/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.service

import com.android.adblib.INFINITE_DURATION
import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.message.LogcatMessage
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import java.time.Duration

/** Reads and clears a logcat from a device */
interface LogcatService {
  suspend fun readLogcat(
    serialNumber: String,
    sdk: Int,
    duration: Duration = INFINITE_DURATION,
    newMessagesOnly: Boolean = false,
  ): Flow<List<LogcatMessage>>

  suspend fun readLogcat(
    device: Device,
    duration: Duration = INFINITE_DURATION,
    newMessagesOnly: Boolean = false,
  ): Flow<List<LogcatMessage>> =
    readLogcat(device.serialNumber, device.sdk, duration, newMessagesOnly)

  suspend fun clearLogcat(serialNumber: String)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LogcatService = project.getService(LogcatService::class.java)
  }
}
