/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.appinspection.api

import com.android.tools.profiler.proto.Common

/**
 * Simple data class containing basic information about a process running on a device. This is created using [Common.Stream] and
 * [Common.Process] which are supplied by the transport pipeline.
 */
class ProcessDescriptor internal constructor(
  internal val stream: Common.Stream,
  internal val process: Common.Process
) {
  /** The manufacturer of the device. */
  val manufacturer: String = stream.device.manufacturer

  /** The model of the device. */
  val model: String = stream.device.model

  /** The serial number of the device. */
  val serial: String = stream.device.serial

  /** The name of the process running on the device. */
  val processName: String = process.name

  override fun toString(): String {
    return "ProcessDescriptor(manufacturer='$manufacturer', model='$model', serial='$serial', processName='$processName')"
  }
}
