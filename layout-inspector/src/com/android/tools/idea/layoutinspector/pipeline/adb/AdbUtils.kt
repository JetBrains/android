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
package com.android.tools.idea.layoutinspector.pipeline.adb

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val ADB_TIMEOUT_SECONDS = 2L

fun AndroidDebugBridge.findDevice(device: DeviceDescriptor): IDevice? {
  return devices.find { it.serialNumber == device.serial }
}

fun IDevice.findClient(process: ProcessDescriptor): Client? {
  return clients.find { it.clientData.pid == process.pid }
}

fun AndroidDebugBridge.findClient(process: ProcessDescriptor): Client? {
  return findDevice(process.device)?.findClient(process)
}

/**
 * Attempts to execute the target [command], returning the output of the command or throwing
 * an exception otherwise
 */
@Slow
fun AndroidDebugBridge.executeShellCommand(device: DeviceDescriptor,
                                           command: String,
                                           timeoutSecs: Long = ADB_TIMEOUT_SECONDS): String {
  return findDevice(device)?.let { adbDevice ->
    val latch = CountDownLatch(1)
    val receiver = CollectingOutputReceiver(latch)
    adbDevice.executeShellCommand(command, receiver, ADB_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    latch.await(timeoutSecs, TimeUnit.SECONDS)
    receiver.output.trim()
  } ?: throw IllegalArgumentException("Could not execute ADB command [$command]. Device (${device.model}) is disconnected.")
}


object AdbUtils {
  fun getAdbFuture(project: Project): ListenableFuture<AndroidDebugBridge> {
    return AndroidSdkUtils.getAdb(project)?.let { AdbService.getInstance()?.getDebugBridge(it) }
           ?: Futures.immediateFuture(AndroidDebugBridge.createBridge())
  }
}