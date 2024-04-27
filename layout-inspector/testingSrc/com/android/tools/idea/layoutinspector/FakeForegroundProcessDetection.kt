/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcess
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetection
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessListener

class FakeForegroundProcessDetection : ForegroundProcessDetection {
  var startInvokeCounter = 0
  var stopInvokeCounter = 0

  val foregroundProcessListeners = mutableListOf<ForegroundProcessListener>()

  override fun addForegroundProcessListener(foregroundProcessListener: ForegroundProcessListener) {
    foregroundProcessListeners.add(foregroundProcessListener)
  }

  override fun removeForegroundProcessListener(
    foregroundProcessListener: ForegroundProcessListener
  ) {
    foregroundProcessListeners.remove(foregroundProcessListener)
  }

  override fun startPollingDevice(
    newDevice: DeviceDescriptor,
    stopPollingPreviousDevice: Boolean
  ) {}

  override fun stopPollingSelectedDevice() {}

  override fun start() {
    startInvokeCounter += 1
  }

  override fun stop() {
    stopInvokeCounter += 1
  }

  fun addNewForegroundProcess(
    deviceDescriptor: DeviceDescriptor,
    foregroundProcess: ForegroundProcess,
    isDebuggable: Boolean
  ) {
    foregroundProcessListeners.forEach {
      it.onNewProcess(deviceDescriptor, foregroundProcess, isDebuggable)
    }
  }
}
