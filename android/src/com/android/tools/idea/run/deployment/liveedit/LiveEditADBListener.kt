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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener

interface AllAdbEventsListener: IClientChangeListener, IDeviceChangeListener

/**
 * LiveEdit needs to listen for event from ADB regarding device lifecycle but also app process (Client) lifecycle
 * in order to maintain its GUI state.
 */
open class LiveEditAdbEventsListener {
  open fun addListener(listener: AllAdbEventsListener) {
    AndroidDebugBridge.addClientChangeListener(listener)
    AndroidDebugBridge.addDeviceChangeListener(listener)
  }

  open fun removeListener(listener: AllAdbEventsListener) {
    AndroidDebugBridge.removeClientChangeListener(listener)
    AndroidDebugBridge.removeDeviceChangeListener(listener)
  }
}