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
package com.android.tools.idea.appinspection.internal.process

import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.profiler.proto.Common

private data class TransportDeviceDescriptor(
  override val manufacturer: String,
  override val model: String,
  override val serial: String,
  override val isEmulator: Boolean,
  override val apiLevel: Int,
  override val version: String,
  override val codename: String?
) : DeviceDescriptor {
  constructor(
    device: Common.Device
  ) : this(
    device.manufacturer,
    device.model,
    device.serial,
    device.isEmulator,
    device.apiLevel,
    device.version,
    device.codename.takeUnless { it.isNullOrBlank() }
  )
}

fun Common.Device.toDeviceDescriptor(): DeviceDescriptor = TransportDeviceDescriptor(this)
