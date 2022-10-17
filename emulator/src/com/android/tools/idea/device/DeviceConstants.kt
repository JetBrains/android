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
@file:JvmName("DeviceConstants")
package com.android.tools.idea.device

import com.intellij.openapi.actionSystem.DataKey

/** Constants for mirroring of physical devices. */

@JvmField val DEVICE_CONTROLLER_KEY = DataKey.create<DeviceController>("DeviceController")

@JvmField val DEVICE_VIEW_KEY = DataKey.create<DeviceView>("DeviceView")

@JvmField val DEVICE_CONFIGURATION_KEY = DataKey.create<DeviceConfiguration>("DeviceConfiguration")

internal const val DEVICE_MAIN_TOOLBAR_ID = "DeviceToolbar"

internal const val UNKNOWN_ORIENTATION = -1