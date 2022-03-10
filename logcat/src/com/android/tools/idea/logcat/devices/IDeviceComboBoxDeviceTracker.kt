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
package com.android.tools.idea.logcat.devices

import kotlinx.coroutines.flow.Flow

/**
 * Tracks devices for [DeviceComboBox].
 *
 * Notifies when a previously unseen device comes online and when a tracked device changes state. Only offline/online states are tracked.
 *
 * Devices are not removed when they go offline.
 */
internal interface IDeviceComboBoxDeviceTracker {
  suspend fun trackDevices(): Flow<DeviceEvent>
}

