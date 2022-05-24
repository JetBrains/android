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

@file:JvmMultifileClass
@file:JvmName("AdbLibMigrationUtils")

package com.android.tools.idea.adblib.ddmlibcompatibility

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Migration function for calls to [IDevice.executeShellCommand]
 */
@Deprecated(deprecationMessage, ReplaceWith("AdbDeviceServices.shell"))
@WorkerThread
@Throws(IOException::class, AdbCommandRejectedException::class, TimeoutException::class, ShellCommandUnresponsiveException::class)
fun executeShellCommand(device: IDevice, command: String, receiver: IShellOutputReceiver) {
  throwIfDispatchThread()

  val stdoutCollector = ShellCollectorToIShellOutputReceiver(receiver)
  val flow = deviceServices.shell(device.toDeviceSelector(), command, stdoutCollector)
  return runBlocking {
    withTimeout(defaultDdmTimeoutMillis) {
      mapToDdmlibException {
        // Note: We know there is only one item in the flow (Unit), because our
        //       ShellCollector implementation forwards buffers directly to
        //       the IShellOutputReceiver
        flow.first()
      }
    }
  }
}
