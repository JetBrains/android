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
package com.android.tools.idea.appinspection.inspector.api.process

/**
 * Basic information about a process running on a device.
 */
interface ProcessDescriptor {
  /** Information about the device this process is running on. */
  val device: DeviceDescriptor

  /** The CPU architecture for this process, e.g. x86_64 */
  val abiCpuArch: String

  /** The fully qualified name of the process. */
  val name: String

  /** The package name of the process, which may diff from the process name. */
  val packageName: String

  /** Whether this process is actively running or not. If not running, that implies it has been terminated. */
  val isRunning: Boolean

  /** The ID of this process assigned by the OS. */
  val pid: Int

  /** An ID used by the underlying transport system associated with this process */
  val streamId: Long
}
