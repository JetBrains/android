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
package com.android.tools.idea.streaming.core

import com.android.annotations.concurrency.UiThread

/**
 * Object that owns device displays and allows components to listen for display-related events.
 */
interface DisplayOwner {
  fun addDeviceDisplayListener(listener: DeviceDisplayListener)
  fun removeDeviceDisplayListener(listener: DeviceDisplayListener)
}

/** The listener interface for receiving device display lifecycle events. */
@UiThread
interface DeviceDisplayListener {

  /** Called when a new display becomes active. */
  fun displayAdded(displayView: AbstractDisplayView)

  /** Called when a display becomes inactive. */
  fun displayRemoved(displayView: AbstractDisplayView)
}