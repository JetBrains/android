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

import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project
import java.nio.file.Path
import javax.swing.JComponent

/**
 * This class plays a supporting role to the launch of inspector tabs in [AppInspectionView].
 *
 * It handles the querying and filtering of inspector tabs based on their compatibility with the library, as well as the
 * resolving of inspector jars from maven. And returns a [AppInspectorTabLaunchParams] for each applicable tab provider.
 */
class AppInspectorTabLaunchSupport(
  private val getTabProviders: () -> Collection<AppInspectorTabProvider>,
  private val apiServices: AppInspectionApiServices,
  private val project: Project,
  private val artifactService: InspectorArtifactService,
) {

  /**
   * This function goes through all library [AppInspectorTabProvider]s, queries the AppInspectionService to perform compatibility checks,
   * and then returns a list of [AppInspectorTabLaunchParams]. The result is used by AppInspectionView to launch the inspectors and create
   * their UI tabs.
   *
   * This function returns a list of [AppInspectorTabLaunchParams], one for each applicable tab provider. Each could be either a
   * [LaunchableInspectorTabLaunchParams] or [StaticInspectorTabLaunchParams]. The former contains all of the information necessary to
   * launch a live inspector while the latter contains the appropriate info message to be displayed in its tab.
   */
  private suspend fun getApplicableLibraryInspectors(process: ProcessDescriptor): List<InspectorTabLaunchParams> {
    val applicableTabProviders = getTabProviders()
                                   .filter {
                                     provider -> provider.isApplicable() && provider.inspectorLaunchParams is LibraryInspectorLaunchParams
                                   }
                                   .takeIf { it.isNotEmpty() } ?: return emptyList()

    if (StudioFlags.APP_INSPECTION_USE_DEV_JAR.get()) {
      // If in dev mode, launch with the provided development inspector jar.
      return applicableTabProviders
        .map {
          LaunchableInspectorTabLaunchParams(it, it.inspectorLaunchParams.inspectorAgentJar)
        }
    }

    // Build a map of ArtifactCoordinate -> AppInspectorTabProvider for easy reverse lookup in the following code.
    val artifactToProvider = applicableTabProviders.associateBy {
      (it.inspectorLaunchParams as LibraryInspectorLaunchParams).minVersionLibraryCoordinate
    }

    // Send list of potential inspectors to AppInspectionService and check their compatibility. Filter out incompatible ones.
    val targetLibraries = applicableTabProviders
      .map { provider -> (provider.inspectorLaunchParams as LibraryInspectorLaunchParams).minVersionLibraryCoordinate }
    val compatibilityResponse = apiServices.attachToProcess(process, project.name).getLibraryVersions(targetLibraries)

    // Partition libraries based on compatibility.
    val (compatibleLibraries, incompatibleLibraries) = compatibilityResponse.partition {
      it.status == LibraryCompatbilityInfo.Status.COMPATIBLE
    }

    // The inspectors for these compatible libraries need to be resolved.
    val resolvableInfos = compatibleLibraries.map {
      InspectorJarContext(artifactToProvider[it.libraryCoordinate]!!, it.getTargetLibraryCoordinate())
    }

    // Show a static info message for these incompatible inspectors.
    val incompatibleInspectorTabs = incompatibleLibraries.map {
      val appInspectorTabProvider = artifactToProvider[it.libraryCoordinate]!!
      val message =
        if (it.status == LibraryCompatbilityInfo.Status.APP_PROGUARDED) appProguardedMessage
        else appInspectorTabProvider.toIncompatibleVersionMessage()
      StaticInspectorTabLaunchParams(appInspectorTabProvider, message)
    }

    return processCompatibleLibraries(resolvableInfos) + incompatibleInspectorTabs
  }

  private suspend fun processCompatibleLibraries(
    resolvableLibraries: List<InspectorJarContext>
  ): List<InspectorTabLaunchParams> {
    val artifacts = resolvableLibraries.associateWith {
      artifactService.getOrResolveInspectorArtifact(it.targetLibrary, project)
    }

    // Partition the artifacts based on whether they were resolved or not.
    val (resolved, unresolved) = artifacts.entries.partition { it.value != null }

    // These are inspector tabs whose jars we managed to resolve and can launch.
    val resolvedInspectorTabs = resolved
      .map { pair ->
        LaunchableInspectorTabLaunchParams(pair.key.provider, pair.value!!.toAppInspectorJar())
      }

    // We didn't manage to resolve artifacts for these tabs, so we show an empty tab with an info message.
    val unresolvedInspectorTabs = unresolved
      .map { pair ->
        StaticInspectorTabLaunchParams(pair.key.provider, pair.key.targetLibrary.toUnresolvedInspectorMessage())
      }

    return resolvedInspectorTabs + unresolvedInspectorTabs
  }

  private fun getApplicableFrameworkInspectors(): List<InspectorTabLaunchParams> {
    return getTabProviders()
      .filter { it.isApplicable() && it.inspectorLaunchParams is FrameworkInspectorLaunchParams }
      .map { LaunchableInspectorTabLaunchParams(it, it.inspectorLaunchParams.inspectorAgentJar) }
  }

  suspend fun getApplicableTabLaunchParams(process: ProcessDescriptor): List<InspectorTabLaunchParams> {
    return getApplicableFrameworkInspectors() + getApplicableLibraryInspectors(process)
  }

  private fun Path.toAppInspectorJar(): AppInspectorJar {
    return AppInspectorJar(fileName.toString(), parent.toString(), parent.toString())
  }
}

private class InspectorJarContext(
  val provider: AppInspectorTabProvider,
  val targetLibrary: ArtifactCoordinate
)

/**
 * Collects all of the information necessary to launch an inspector tab, live or dead, in [AppInspectionView].
 *
 * This is used primarily by [AppInspectionView] to decide which tabs to launch, and which to show an error message.
 */
sealed class InspectorTabLaunchParams(val provider: AppInspectorTabProvider)

/**
 * Represents the capability to launch a tab with the inspector [jar].
 */
class LaunchableInspectorTabLaunchParams(
  provider: AppInspectorTabProvider,
  val jar: AppInspectorJar
) : InspectorTabLaunchParams(provider)

/**
 * Represents inspector instances that for the following reasons cannot be launched:
 *  1) Inspector jar could not be resolved
 *  2) Inspector tab is incompatible with the version of the library in the app
 */
class StaticInspectorTabLaunchParams(provider: AppInspectorTabProvider, private val reason: String) : InspectorTabLaunchParams(provider) {
  fun toInfoMessageTab(): JComponent {
    return EmptyStatePanel(reason)
  }
}

internal fun AppInspectorTabProvider.toIncompatibleVersionMessage() =
  AppInspectionBundle.message("incompatible.version",
                              (inspectorLaunchParams as LibraryInspectorLaunchParams).minVersionLibraryCoordinate.toString())

private fun ArtifactCoordinate.toUnresolvedInspectorMessage() = AppInspectionBundle.message("unresolved.inspector", this.toString())

internal val appProguardedMessage = AppInspectionBundle.message("app.proguarded")