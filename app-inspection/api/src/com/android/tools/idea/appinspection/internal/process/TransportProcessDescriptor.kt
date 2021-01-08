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
package com.android.tools.idea.appinspection.internal.process

import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.profiler.proto.Common

/**
 * A [ProcessDescriptor] implementation build using transport-related protos.
 */
class TransportProcessDescriptor(
  val stream: Common.Stream,
  val process: Common.Process
): ProcessDescriptor {
  override val device = object : DeviceDescriptor {
    override val manufacturer: String = stream.device.manufacturer
    override val model: String = stream.device.model
    override val serial: String = stream.device.serial
    override val isEmulator: Boolean = stream.device.isEmulator
    override val apiLevel: Int = stream.device.apiLevel
    override val version: String = stream.device.version
    override val codename: String? = stream.device.codename.takeUnless { it.isNullOrBlank() }

    override fun toString(): String {
      return "DeviceDescriptor(manufacturer='$manufacturer', model='$model', serial='$serial', isEmulator='$isEmulator', apiLevel='$apiLevel')"
    }
  }
  override val abiCpuArch: String = process.abiCpuArch
  override val name: String = process.name
  override val isRunning: Boolean = process.state != Common.Process.State.DEAD
  override val pid: Int = process.pid
  override val streamId: Long = stream.streamId

  override fun toString(): String {
    return "ProcessDescriptor(device='$device', name='$name', isRunning='$isRunning')"
  }
}
