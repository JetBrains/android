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
package com.android.tools.idea.device.explorer.monitor

import com.android.annotations.concurrency.UiThread

@UiThread
interface DeviceMonitorViewListener {
  fun refreshInvoked()
  fun killNodesInvoked(rows: IntArray)
  fun forceStopNodesInvoked(rows: IntArray)
  fun debugNodes(rows: IntArray)
  fun packageFilterToggled(isActive: Boolean)
  fun backupApplication(rows: IntArray)
  fun restoreApplication(rows: IntArray)
}
