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
package com.android.tools.idea.appinspection.inspector.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate

/** Contains general information that is required to launch an inspector on device. */
sealed class AppInspectorLaunchParams {
  abstract val inspectorAgentJar: AppInspectorJar
}

class FrameworkInspectorLaunchParams(override val inspectorAgentJar: AppInspectorJar) :
  AppInspectorLaunchParams()

/**
 * In addition to the inspector agent jar, library inspectors need to provide information about the
 * library they are targeting.
 */
class LibraryInspectorLaunchParams(
  override val inspectorAgentJar: AppInspectorJar,
  /**
   * Information about the library this inspector is targeting, including the minimum version this
   * inspector is compatible with.
   */
  val minVersionLibraryCoordinate: ArtifactCoordinate
) : AppInspectorLaunchParams()
