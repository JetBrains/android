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
import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.instantapp.InstantApps
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.TargetSelectionMode
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.android.tools.idea.run.util.LaunchUtils
import com.intellij.execution.RunManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.util.PathUtil
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

class AndroidRunConfigurations {
  @Slow
  @WorkerThread
  fun createRunConfiguration(facet: AndroidFacet) {
    // Android run configuration should always be created with the main module
    val module = facet.mainModule
    val configurationFactory = AndroidRunConfigurationType.getInstance().factory
    val configurations = RunManager.getInstance(module.project).getConfigurationsList(configurationFactory.type)
    for (configuration in configurations) {
      if (configuration is AndroidRunConfiguration && configuration.configurationModule.module == module) {
        // There is already a run configuration for this module.
        return
      }
    }
    if (LaunchUtils.isWatchFeatureRequired(facet) && !hasDefaultLauncherActivity(facet)) {
      // Don't create Wear Apps Configurations, as the user can launch Wear Surfaces from the gutter
      return
    }
    ApplicationManager.getApplication().invokeAndWait { addRunConfiguration(facet) }
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

  private fun hasDefaultLauncherActivity(facet: AndroidFacet): Boolean {
    val manifest = Manifest.getMainManifest(facet) ?: return false
    return runReadAction { DefaultActivityLocator.hasDefaultLauncherActivity(manifest) }
  }

  companion object {
    @JvmStatic
    val instance: AndroidRunConfigurations
      get() = ApplicationManager.getApplication().getService(AndroidRunConfigurations::class.java)
  }
}