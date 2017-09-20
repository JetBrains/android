/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.device.fs

import com.intellij.openapi.util.Key

/**
 * Persistent identifier of a file on a given device.
 *
 * The identifier remains valid across device and/or JVM restarts.
 */
data class DeviceFileId(val deviceId: String, val devicePath: String) {
  companion object {
    @JvmField val UNKNOWN = DeviceFileId("", "")
    @JvmField val KEY = Key.create<DeviceFileId>("DEVICE-ENTRY-INFO-KEY")
  }
}
