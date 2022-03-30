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
package com.android.tools.idea.adb.processnamemonitor

import com.android.tools.idea.adb.AdbAdapter
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Online
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Implementation of ProcessNameMonitorFlows
 */
@Suppress("EXPERIMENTAL_API_USAGE") // Not experimental in main
internal class ProcessNameMonitorFlowsImpl(
  private val adbAdapter: AdbAdapter,
) : ProcessNameMonitorFlows {
  private val logger = thisLogger()

  override fun trackDevices(): Flow<DeviceMonitorEvent> = callbackFlow {
    val listener = DevicesMonitorListener(this)
    adbAdapter.addDeviceChangeListener(listener)

    // Adding a listener does not fire events about existing devices so we have to add them manually.
    adbAdapter.getDevices().filter { it.isOnline }.forEach {
      trySendBlocking(Online(it))
        .onFailure { e -> logger.warn("Failed to send a DeviceMonitorEvent", e) }
    }

    awaitClose { adbAdapter.removeDeviceChangeListener(listener) }
  }.flowOn(workerThread)
}
