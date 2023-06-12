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
package com.android.tools.idea.compose.pickers.preview.model

import com.android.sdklib.devices.Device
import com.intellij.openapi.actionSystem.DataKey

/**
 * Key to obtain the currently active Device based on the @Preview.device field.
 *
 * If the value corresponds to device specs ("spec:...") it will return a custom Device instance
 * with those parameters, otherwise, it will be a Device that matches the display name or id.
 *
 * @see com.android.tools.idea.compose.preview.pickers.properties.utils.findOrParseFromDefinition
 */
internal val CurrentDeviceKey = DataKey.create<Device?>("preview.picker.current.device")

/** Key to obtain the list of all available devices from the device manager. */
internal val AvailableDevicesKey =
  DataKey.create<Collection<Device>>("preview.picker.available.devices")
