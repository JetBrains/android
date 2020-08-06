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

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.profiler.proto.Common

/**
 * A [ProcessDescriptor] implementation build using transport-related protos.
 */
class TransportProcessDescriptor(
  val stream: Common.Stream,
  val process: Common.Process
): ProcessDescriptor {
  override val manufacturer: String = stream.device.manufacturer
  override val model: String = stream.device.model
  override val serial: String = stream.device.serial
  override val processName: String = process.name
  override val isEmulator: Boolean = stream.device.isEmulator
  override val isRunning: Boolean = process.state != Common.Process.State.DEAD

  override fun toString(): String {
    return "ProcessDescriptor(manufacturer='$manufacturer', model='$model', serial='$serial', processName='$processName', isEmulator='$isEmulator', isRunning='$isRunning')"
  }
}

/**
 * In this module, [ProcessDescriptor]s are always implemented by [TransportProcessDescriptor],
 * so this convenience method provides cleaner syntax for casting.
 */
internal fun ProcessDescriptor.toTransportImpl() = this as TransportProcessDescriptor

/**
 * Return true if the process it represents is inspectable.
 *
 * Currently, a process is deemed inspectable if the device it's running on is O+ and if it's debuggable. The latter condition is
 * guaranteed to be true because transport pipeline only provides debuggable processes, so there is no need to check.
 */
internal fun ProcessDescriptor.isInspectable(): Boolean {
  return this.toTransportImpl().stream.device.apiLevel >= AndroidVersion.VersionCodes.O
}
