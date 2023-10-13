/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.project

import com.android.AndroidProjectTypes
import com.android.tools.idea.instantapp.InstantApps
import com.android.tools.idea.model.MergedManifestException
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.TargetSelectionMode
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.android.tools.idea.run.util.LaunchUtils
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PathUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

@Service(Service.Level.PROJECT)
class AndroidRunConfigurations(
  private val project: Project,
  private val coroutineScope: CoroutineScope
) {
  fun setupRunConfigurations() {
    coroutineScope.launch {
      withBackgroundProgress(project, AndroidBundle.message("android.progress.title.setting.up.run.configurations")) {
        val configurations = readAction {
          project.getAndroidFacets()
            .filter { it.configuration.isAppProject }
        }

        for (configuration in configurations) {
          if (!shouldCreateRunConfiguration(configuration)) continue

          writeAction {
            if (configuration.isDisposed) return@writeAction

            addRunConfiguration(configuration)
          }
        }
      }
    }
  }

  fun setupRunConfigurationsBlocking() {
    val configurations = ReadAction.compute<List<AndroidFacet>, Throwable> {
      project.getAndroidFacets()
        .filter { it.configuration.isAppProject && shouldCreateRunConfigurationBlocking(it) }
    }

    for (facet in configurations) {
      ApplicationManager.getApplication().invokeAndWait {
        WriteAction.run<Throwable> {
          if (!facet.isDisposed) {
            addRunConfiguration(facet)
          }
        }
      }
    }
  }

  private suspend fun shouldCreateRunConfiguration(facet: AndroidFacet): Boolean {
    val manifestFuture = readAction {
      if (facet.isDisposed) return@readAction null

      val module = facet.mainModule
      val configurationFactory = AndroidRunConfigurationType.getInstance().factory
      val configurations = RunManager.getInstance(module.project).getConfigurationsList(configurationFactory.type)

      if (configurations.hasAndroidRunConfigurationForModule(module)) {
        // There is already a run configuration for this module.
        return@readAction null
      }
      MergedManifestManager.getMergedManifest(facet.module)
    }

    val snapshot = try {
      manifestFuture?.await()  // do not call under read lock!
    }
    catch (ex: InterruptedException) {
      Logger.getInstance(AndroidRunConfigurations::class.java).warn(ex)
      null
    }
    catch (ex: MergedManifestException) {
      Logger.getInstance(AndroidRunConfigurations::class.java).warn(ex)
      null
    }

    return readAction {
      val isWatchFeatureRequired = LaunchUtils.isWatchFeatureRequired(snapshot)
      val hasDefaultLauncherActivity = hasDefaultLauncherActivity(facet)
      val requiresAndroidWatchRunConfiguration = isWatchFeatureRequired && !hasDefaultLauncherActivity
      // Don't create Wear Apps Configurations, as the user can launch Wear Surfaces from the gutter
      !requiresAndroidWatchRunConfiguration
    }
  }

  private fun shouldCreateRunConfigurationBlocking(facet: AndroidFacet): Boolean {
    if (facet.isDisposed) return false

    val module = facet.mainModule
    val configurationFactory = AndroidRunConfigurationType.getInstance().factory
    val configurations = RunManager.getInstance(module.project).getConfigurationsList(configurationFactory.type)

    if (configurations.hasAndroidRunConfigurationForModule(module)) {
      // There is already a run configuration for this module.
      return false
    }

    val isWatchFeatureRequired = LaunchUtils.isWatchFeatureRequired(facet)
    val hasDefaultLauncherActivity = hasDefaultLauncherActivity(facet)
    val requiresAndroidWatchRunConfiguration = isWatchFeatureRequired && !hasDefaultLauncherActivity
    // Don't create Wear Apps Configurations, as the user can launch Wear Surfaces from the gutter
    return !requiresAndroidWatchRunConfiguration
  }

  private fun addRunConfiguration(facet: AndroidFacet) {
    val module = facet.mainModule
    val runManager = RunManager.getInstance(module.project)
    val projectNameInExternalSystemStyle = PathUtil.suggestFileName(module.project.name, true, false)
    val moduleName = module.getHolderModule().name
    val configurationName = moduleName.removePrefix("$projectNameInExternalSystemStyle.")
    val settings = runManager.createConfiguration(configurationName, AndroidRunConfigurationType::class.java)
    val configuration = settings.configuration as AndroidRunConfiguration
    configuration.setModule(module)
    if (facet.configuration.projectType == AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP) {
      configuration.setLaunchUrl(InstantApps.getDefaultInstantAppUrl(facet))
    }
    else {
      configuration.MODE = AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY
    }

    configuration.deployTargetContext.targetSelectionMode = TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX

    if (!module.project.isDisposed) {
      runManager.addConfiguration(settings)
      runManager.selectedConfiguration = settings
    }
  }

  private fun List<RunConfiguration>.hasAndroidRunConfigurationForModule(mainModule: Module): Boolean = any { configuration ->
    configuration is AndroidRunConfiguration && configuration.configurationModule.module == mainModule
  }

  private fun hasDefaultLauncherActivity(facet: AndroidFacet): Boolean {
    val manifest = Manifest.getMainManifest(facet) ?: return false
    return DefaultActivityLocator.hasDefaultLauncherActivity(manifest)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AndroidRunConfigurations = project.service<AndroidRunConfigurations>()
  }
}