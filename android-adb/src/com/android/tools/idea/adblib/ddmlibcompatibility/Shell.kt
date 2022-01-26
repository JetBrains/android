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

import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.nio.ByteBuffer

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

/**
 * Coroutine-based wrapper around DDMLib's [IDevice.executeShellCommand], returning a Flow of shell output,
 * like that produced by [AdbDeviceServices.shell].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> executeShellCommand(device: IDevice, command: String, shellCollector: ShellCollector<T>): Flow<T> =
  flow {
    shellCollector.start(this)
    callbackFlow<ByteBuffer> {
      device.executeShellCommand(command, object : IShellOutputReceiver {
        override fun addOutput(data: ByteArray?, offset: Int, length: Int) {
          trySendBlocking(ByteBuffer.wrap(data, offset, length))
        }

        override fun flush() {
          close()
        }

        override fun isCancelled(): Boolean =
          channel.isClosedForSend
      })
      awaitClose()
    }.flowOn(ioDispatcher).collect { value ->
      shellCollector.collect(this, value)
    }
    shellCollector.end(this)
  }
