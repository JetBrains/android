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
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.ui.AppInspectionView
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorLaunchConfig
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.LibraryInspectorLaunchParams
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * This class plays a supporting role to the launch of inspector tabs in [AppInspectionView].
 *
 * It handles the querying and filtering of inspector tabs based on their compatibility with the library, as well as the
 * resolving of inspector jars from maven. And returns an [InspectorTabJarTargets] for each applicable tab provider.
 */
class AppInspectorTabLaunchSupport(
  private val getTabProviders: () -> Collection<AppInspectorTabProvider>,
  private val apiServices: AppInspectionApiServices,
  private val project: Project,
  private val artifactService: InspectorArtifactService,
) {

  /**
   * Given a target [process], and using [getTabProviders], return a mapping of each tab provider to a list of one or
   * more jar targets that contain an inspector which can be deployed against the app.
   *
   * The returned list will contain one entry per tab, although each entry itself can contain one or more jar targets.
   * It is expected that most inspector tabs will only use a single inspector, but in practice they can use any number
   * of them.
   *
   * See also: [InspectorJarTarget]
   */
  suspend fun getInspectorTabJarTargets(process: ProcessDescriptor): List<InspectorTabJarTargets> {
    return getTabProviders()
      .filter { provider -> provider.isApplicable() }
      .map { provider ->
        val (frameworkConfigs, libraryConfigs)
          = provider.launchConfigs.partition { config -> config.params is FrameworkInspectorLaunchParams }

        InspectorTabJarTargets(provider, frameworkConfigs.getFrameworkJarTargets() + libraryConfigs.getLibraryJarTargets(process, provider))
      }
  }

  private fun List<AppInspectorLaunchConfig>.getFrameworkJarTargets(): Map<String, InspectorJarTarget> {
    assert(all { config -> config.params is FrameworkInspectorLaunchParams })
    // Framework inspector jars are always resolvable because they are bundled with Studio
    return associate { config -> config.id to InspectorJarTarget.Resolved(config.params.inspectorAgentJar) }
  }

  private suspend fun List<AppInspectorLaunchConfig>.getLibraryJarTargets(
    process: ProcessDescriptor,
    provider: AppInspectorTabProvider
  ): Map<String, InspectorJarTarget> {
    assert(all { config -> config.params is LibraryInspectorLaunchParams })

    if (StudioFlags.APP_INSPECTION_USE_DEV_JAR.get()) {
      return associate { config -> config.id to InspectorJarTarget.Resolved(config.params.inspectorAgentJar) }
    }

    val coords = map { config -> (config.params as LibraryInspectorLaunchParams).minVersionLibraryCoordinate }
    val compatibilities = coords.map { LibraryCompatibility(it) }
    val compatibilityResponse = apiServices.attachToProcess(process, project.name).getLibraryVersions(compatibilities)

    return mapIndexed { i, config ->
      config.id to when (compatibilityResponse[i].status) {
        LibraryCompatbilityInfo.Status.COMPATIBLE -> {
          val coord = coords[i]
          try {
            InspectorJarTarget.Resolved(artifactService.getOrResolveInspectorArtifact(coord, project).toAppInspectorJar())
          } catch (e: AppInspectionArtifactNotFoundException) {
            InspectorJarTarget.Unresolved(coord.toUnresolvedInspectorMessage())
          }
        }
        LibraryCompatbilityInfo.Status.APP_PROGUARDED -> InspectorJarTarget.Unresolved(APP_PROGUARDED_MESSAGE)
        else -> InspectorJarTarget.Unresolved(provider.toIncompatibleVersionMessage())
      }
    }
      .toMap()
  }

  private fun Path.toAppInspectorJar(): AppInspectorJar {
    return AppInspectorJar(fileName.toString(), parent.toString(), parent.toString())
  }
}

/** A wrapper around a target inspector jar that either was successfully resolved or not. */
sealed class InspectorJarTarget {
  class Resolved(val jar: AppInspectorJar) : InspectorJarTarget()

  /**
   * Represents inspectors that cannot be launched, e.g. the target library used by the app is too
   * old or the user's app was proguarded.
   */
  class Unresolved(val error: String) : InspectorJarTarget()
}

/** A collection of one or more [InspectorJarTarget]s referenced by a given tab. */
class InspectorTabJarTargets(
  val provider: AppInspectorTabProvider,
  /** Map of inspector ID to jar targets */
  var targets: Map<String, InspectorJarTarget>,
)

internal fun AppInspectorTabProvider.toIncompatibleVersionMessage() =
  AppInspectionBundle.message("incompatible.version",
                              launchConfigs.mapNotNull { it.params as? LibraryInspectorLaunchParams }
                                .first()
                                .minVersionLibraryCoordinate
                                .toString())

private fun ArtifactCoordinate.toUnresolvedInspectorMessage() = AppInspectionBundle.message("unresolved.inspector", this.toString())

internal val APP_PROGUARDED_MESSAGE = AppInspectionBundle.message("app.proguarded")