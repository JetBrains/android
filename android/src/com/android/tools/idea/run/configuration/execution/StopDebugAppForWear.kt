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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import java.util.concurrent.TimeUnit


private val DEBUG_SURFACE_CLEAR =
  "am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation 'clear-debug-app'"
private val ACTIVITY_MANAGER_CLEAR = "am clear-debug-app"


internal fun stopDebugApp(device: IDevice) {
  val dummyReceiver = NullOutputReceiver()
  device.executeShellCommand(DEBUG_SURFACE_CLEAR, dummyReceiver, 5, TimeUnit.SECONDS)
  device.executeShellCommand(ACTIVITY_MANAGER_CLEAR, dummyReceiver, 5, TimeUnit.SECONDS)
}

