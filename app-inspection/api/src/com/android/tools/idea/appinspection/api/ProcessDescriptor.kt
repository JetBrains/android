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
 * Simple data class containing [Common.Stream] and [Common.Process] which are supplied by the transport pipeline. It is used to identify
 * processes running on device and can be matched with [LaunchedProcessDescriptor] supplied by AndroidLaunchTaskContributor.
 */
data class TransportProcessDescriptor(
  val stream: Common.Stream,
  val process: Common.Process
)


/**
 * A descriptor that is provided by AndroidLaunchTaskContributor and is used to identify apps launched by Studio.
 *
 * Note the descriptor here is different from [TransportProcessDescriptor] even though they both represent processes.
 * [TransportProcessDescriptor] is based on transport protocol whereas [LaunchedProcessDescriptor] is based on information from a launched
 * app. They share a common set of identifying attributes about a process such as its device's manufacturer, model and process name.
 */
data class LaunchedProcessDescriptor(
  /** The manufacturer of the device. */
  val manufacturer: String,

  /** The model of the device. */
  val model: String,

  /** The name of the process running on the device. */
  val processName: String?
)