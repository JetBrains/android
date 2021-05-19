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
package com.android.tools.idea.appinspection.ide

import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryVersionResponse
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.intellij.openapi.project.Project

/**
 * This class plays a supporting role to the launch of inspector tabs in [AppInspectionView].
 *
 * It handles the querying and filtering of inspector tabs based on their compatibility with the library. And returns a
 * [AppInspectorTabLaunchParams] for each applicable tab provider.
 */
class AppInspectorTabLaunchSupport(
  private val getTabProviders: () -> Collection<AppInspectorTabProvider>,
  private val apiServices: AppInspectionApiServices,
  private val project: Project
) {

  /**
   * This function goes through all library [AppInspectorTabProvider]s, queries the AppInspectionService to perform compatibility checks,
   * and then returns a list of [AppInspectorTabLaunchParams]. The result is used by AppInspectionView to launch the inspectors and create
   * their UI tabs.
   *
   * This function returns a list of [AppInspectorTabLaunchParams], one for each applicable tab provider. Each param contains a status
   * field which is set according to the following scenarios:
   *   1) Tab is compatible with the library - [AppInspectorTabLaunchParams.Status.LAUNCH]
   *   2) Tab is not compatible with the library - [AppInspectorTabLaunchParams.Status.INCOMPATIBLE]
   */
  private suspend fun getApplicableLibraryInspectors(process: ProcessDescriptor): List<AppInspectorTabLaunchParams> {
    val applicableTabProviders = getTabProviders()
      .filter { provider -> provider.isApplicable() && provider.inspectorLaunchParams is LibraryInspectorLaunchParams }

    if (applicableTabProviders.isEmpty()) {
      return emptyList()
    }

    // Send list of potential inspectors to AppInspectionService and check their compatibility. Filter out incompatible ones.
    val targetLibraries = applicableTabProviders
      .map { provider -> (provider.inspectorLaunchParams as LibraryInspectorLaunchParams).minVersionLibraryCoordinate }
    val compatibilityResponse = apiServices.attachToProcess(process, project.name).getLibraryVersions(targetLibraries)

    // Build a map of ArtifactCoordinate -> AppInspectorTabProvider for easy reverse lookup in the following code.
    val artifactToProvider = applicableTabProviders.associateBy {
      (it.inspectorLaunchParams as LibraryInspectorLaunchParams).minVersionLibraryCoordinate
    }

    return compatibilityResponse.map {
      if (it.status == LibraryVersionResponse.Status.COMPATIBLE) {
        AppInspectorTabLaunchParams(AppInspectorTabLaunchParams.Status.LAUNCH, artifactToProvider[it.targetLibrary]!!)
      }
      else {
        AppInspectorTabLaunchParams(AppInspectorTabLaunchParams.Status.INCOMPATIBLE,
                                    artifactToProvider[it.targetLibrary]!!)
      }
    }
  }

  private fun getApplicableFrameworkInspectors(): List<AppInspectorTabLaunchParams> {
    return getTabProviders()
      .filter { it.isApplicable() && it.inspectorLaunchParams is FrameworkInspectorLaunchParams }
      .map { AppInspectorTabLaunchParams(AppInspectorTabLaunchParams.Status.LAUNCH, it) }
  }


  suspend fun getApplicableTabLaunchParams(process: ProcessDescriptor): List<AppInspectorTabLaunchParams> {
    return getApplicableFrameworkInspectors() + getApplicableLibraryInspectors(process)
  }
}

/**
 * Collects all of the information necessary to launch an inspector tab in [AppInspectionView].
 *
 * The [status] field reflects the outcome of the compatibility check. It acts as a signal to [AppInspectionView] on whether this tab
 * should be launched.
 */
class AppInspectorTabLaunchParams(
  val status: Status,
  val provider: AppInspectorTabProvider
) {
  enum class Status {
    /**
     * The tab is to be launched.
     */
    LAUNCH,

    /**
     * The inspector tab is not compatible with the version of the library running in the app.
     */
    INCOMPATIBLE,
  }
}