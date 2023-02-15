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
package com.android.tools.idea.appinspection.internal

import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.google.wireless.android.sdk.stats.AppInspectionEvent

interface AppInspectionAnalyticsTracker {
  fun trackErrorOccurred(errorKind: AppInspectionEvent.ErrorKind)
  fun trackToolWindowOpened()
  fun trackToolWindowHidden()
  fun trackProcessSelected(device: DeviceDescriptor, numDevices: Int, numProcesses: Int)
  fun trackInspectionStopped()
  fun trackInspectionRestarted()
}

val STUB_TRACKER =
  object : AppInspectionAnalyticsTracker {
    override fun trackErrorOccurred(errorKind: AppInspectionEvent.ErrorKind) {}
    override fun trackToolWindowOpened() {}
    override fun trackToolWindowHidden() {}
    override fun trackProcessSelected(
      device: DeviceDescriptor,
      numDevices: Int,
      numProcesses: Int
    ) {}
    override fun trackInspectionStopped() {}
    override fun trackInspectionRestarted() {}
  }
