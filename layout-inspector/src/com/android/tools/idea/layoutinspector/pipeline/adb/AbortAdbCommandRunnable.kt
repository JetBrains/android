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
package com.android.tools.idea.layoutinspector.pipeline.adb

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.CollectingOutputReceiver
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import java.util.concurrent.CountDownLatch

/**
 * A runnable which starts an adb command that can later be aborted.
 *
 * This runnable is meant to be run on a background thread and target an adb command that blocks
 * indefinitely - otherwise, callers should probably just use [executeShellCommand] directly
 * instead.
 */
class AbortAdbCommandRunnable(private val adb: AndroidDebugBridge,
                              private val device: DeviceDescriptor,
                              private val command: String
) : Runnable {

  private val shouldQuit = CountDownLatch(1)
  private var receiver: CollectingOutputReceiver? = null

  fun stop() {
    receiver?.cancel()
    shouldQuit.countDown()
  }

  override fun run() {
    receiver = adb.startShellCommand(device, command, latch = shouldQuit)
  }
}
