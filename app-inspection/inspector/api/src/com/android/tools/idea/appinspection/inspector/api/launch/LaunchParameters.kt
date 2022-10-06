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
package com.android.tools.idea.appinspection.inspector.api.launch

import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor

/**
 * Encapsulates all of the parameters that are required for launching an inspector.
 */
data class LaunchParameters(
  /**
   * Identifies the target process in which to launch the inspector. It is supplied by [AppInspectionProcessDiscovery].
   */
  val processDescriptor: ProcessDescriptor,
  /**
   * Id of the inspector.
   */
  val inspectorId: String,
  /**
   * The [AppInspectorJar] containing the location of the dex to be installed on device.
   */
  val inspectorJar: AppInspectorJar,
  /**
   * The name of the studio project launching the inspector.
   */
  val projectName: String,
  /**
   * Information about the library this inspector is targeting. Null if inspector doesn't target a library (ex: framework inspector).
   */
  val library: LibraryCompatibility? = null,
  /**
   * If true, launch the inspector even if one is already running.
   */
  val force: Boolean = false
)