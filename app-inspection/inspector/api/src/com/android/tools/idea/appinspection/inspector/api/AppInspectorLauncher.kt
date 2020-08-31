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
package com.android.tools.idea.appinspection.inspector.api

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor

interface AppInspectorLauncher {
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
     * Information about the library this inspector is targeting.
     */
    val targetLibrary: TargetLibrary,
    /**
     * If true, launch the inspector even if one is already running.
     */
    val force: Boolean = false
  )

  /**
   * Contains information that uniquely identifies the library, and also the minimum version the inspector is compatible.
   */
  data class TargetLibrary(
    val artifact: LibraryArtifact,
    val minVersion: String
  ) {
    /**
     * The name of the version file located in an app's APK's META-INF folder.
     */
    val versionFileName = "${artifact.groupId}_${artifact.artifactId}.version"

    /**
     * The coordinate for this library, i.e. how it would appear in a Gradle dependencies block.
     */
    val coordinate = "${artifact.groupId}:${artifact.artifactId}:$minVersion"
  }

  /**
   * Represents an artifact that can be uniquely identified by the information provided in this class.
   *
   * This normally refers to the maven/gradle coordinate of the artifact.
   */
  data class LibraryArtifact(
    val groupId: String,
    val artifactId: String
  )

  /**
   * Launches an inspector based on the information given by [params], returning an
   * [AppInspectorMessenger] which can be used to communicate with it.
   *
   * [params] contains information such as the inspector's id and dex location, as well as the targeted process's descriptor.
   */
  suspend fun launchInspector(params: LaunchParameters): AppInspectorMessenger
}