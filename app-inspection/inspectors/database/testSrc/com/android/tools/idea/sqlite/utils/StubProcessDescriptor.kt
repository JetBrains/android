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
package com.android.tools.idea.sqlite.utils

import com.android.sdklib.AndroidApiLevel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor

class StubProcessDescriptor : ProcessDescriptor {
  override val device =
    object : DeviceDescriptor {
      override val manufacturer = "manufacturer"
      override val model = "model"
      override val serial = "serial"
      override val isEmulator = false
      override val apiLevel = AndroidApiLevel(26)
      override val version: String = "8.0"
      override val codename: String = "O"
    }
  override val abiCpuArch = "x86"
  override val name = "processName"
  override val packageName = "packageName"
  override val isRunning = true
  override val pid = 123
  override val streamId = 123456L
}
