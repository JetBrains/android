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
package com.android.tools.idea.appinspection.internal

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility

/**
 * Represents an app-inspection target process (on the device) being connected to from the host.
 */
abstract class AppInspectionTarget {
  /**
   * Launches an inspector in the connected process using the provided [params], returning an
   * [AppInspectorMessenger] that can be used to communicate with it.
   */
  @WorkerThread
  internal abstract suspend fun launchInspector(params: LaunchParameters): AppInspectorMessenger

  /**
   * Disposes all of the clients that were launched on this target.
   */
  @WorkerThread
  internal abstract suspend fun dispose()

  /**
   * For each of the provided target, check its version compatibility and returns the result in [LibraryCompatbilityInfo].
   *
   * The version check result can be in several different states. See [LibraryCompatbilityInfo.Status] for details.
   */
  @WorkerThread
  abstract suspend fun getLibraryVersions(libraryCoordinates: List<LibraryCompatibility>): List<LibraryCompatbilityInfo>
}