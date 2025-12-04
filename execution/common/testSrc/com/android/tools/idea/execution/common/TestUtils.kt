/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.execution.common

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.fakeadbserver.DeviceState
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal fun DeviceState.launchAndWaitForProcess(pid: Int, userId: Int, packageName: String, waitingForDebugger: Boolean): Client {
  val latch = CountDownLatch(1)
  var launchedClient: Client? = null
  // If the client is waiting for debugger attachment then we wait for the debugger status
  // change, otherwise we just wait for the application ID to be returned
  val desiredEvent = if (waitingForDebugger) Client.CHANGE_DEBUGGER_STATUS else Client.CHANGE_NAME

  val clientListener = AndroidDebugBridge.IClientChangeListener { client, changeMask ->
    if (client.device.serialNumber == deviceId && client.clientData.pid == pid && changeMask == desiredEvent) {
      assertThat(client.isValid).isTrue()
      launchedClient = client
      latch.countDown()
    }
  }

  AndroidDebugBridge.addClientChangeListener(clientListener)
  try {
    startClient(pid, userId, packageName, waitingForDebugger)
    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
  }
  finally {
    AndroidDebugBridge.removeClientChangeListener(clientListener)
  }

  return launchedClient!!
}