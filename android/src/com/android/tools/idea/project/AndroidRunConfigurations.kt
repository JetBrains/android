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
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.instantapp.InstantApps
import com.android.tools.idea.model.MergedManifestModificationTracker
import com.android.tools.idea.project.coroutines.runReadActionInSmartModeWithIndexes
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.TargetSelectionMode
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.android.tools.idea.run.configuration.AndroidComplicationRunConfigurationProducer
import com.android.tools.idea.run.configuration.AndroidTileRunConfigurationProducer
import com.android.tools.idea.run.configuration.AndroidWatchFaceRunConfigurationProducer
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.run.util.LaunchUtils
import com.android.utils.cache.ChangeTracker
import com.android.utils.cache.ChangeTrackerCachedValue
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiClass
import com.intellij.util.PathUtil
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.manifest.UsesFeature
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils

private val wearConfigurationProducers = listOf(
  AndroidTileRunConfigurationProducer(),
  AndroidComplicationRunConfigurationProducer(),
  AndroidWatchFaceRunConfigurationProducer()
)

private val LOG: Logger by lazy { Logger.getInstance(AndroidRunConfigurations::class.java) }

class AndroidRunConfigurations {

  suspend fun createRunConfigurations(project: Project) {
    createAndroidRunConfigurations(project)
    // create the Android run configurations first as we limit the number of wear configurations
    // based on the existing number of configurations.
    createWearConfigurations(project)
  }

  private fun createAndroidRunConfigurations(project: Project) {
    project.getAndroidFacets().filter { it.configuration.isAppProject }.forEach {
      createAndroidRunConfiguration(it)
    }
  }

  private fun createAndroidRunConfiguration(facet: AndroidFacet) {
    LOG.debug { "createAndroidRunConfiguration($facet)" }
    // Android run configuration should always be created with the holder module
    val module = facet.module
    val configurationFactory = AndroidRunConfigurationType.getInstance().factory
    val configurations = RunManager.getInstance(module.project).getConfigurationsList(configurationFactory.type)
    for (configuration in configurations) {
      if (configuration is AndroidRunConfiguration && configuration.configurationModule.module == module) {
        LOG.debug { "There is already a run configuration for module $module: $configuration" }
        return
      }
    }
    if (LaunchUtils.isWatchFeatureRequired(facet) && !hasDefaultLauncherActivity(facet)) {
      LOG.debug { "Don't create Wear Apps Configurations, as the user can launch Wear Surfaces from the gutter" }
      return
    }
    addAndroidRunConfiguration(facet)
  }

  private suspend fun createWearConfigurations(project: Project) {
    if (!StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED.get()) {
      return
    }
    val runManager = runReadAction {
      if (project.isDisposed) return@runReadAction null
      RunManager.getInstance(project)
    } ?: return

    val maxAllowedRunConfigurations = StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_MAX_TOTAL_RUN_CONFIGS.get()
    val existingRunConfigurationCount = runManager.allConfigurationsList.size
    if (existingRunConfigurationCount >= maxAllowedRunConfigurations) {
      // We don't want to breach the maximum number of allowed run configurations
      return
    }

    val wearRunConfigurationsToAdd = mutableListOf<RunnerAndConfigurationSettings>()
    project.getAndroidFacets().filter { it.configuration.isAppProject }.forEach {
      if (!project.isDisposed) {
        wearRunConfigurationsToAdd += createWearConfigurations(it.module)
      }
      if (existingRunConfigurationCount + wearRunConfigurationsToAdd.size > maxAllowedRunConfigurations) {
        // We don't want to breach the maximum number of allowed run configurations
        return
      }
    }

    runReadAction {
      if (!project.isDisposed) {
        wearRunConfigurationsToAdd.forEach {
          runManager.addConfiguration(it)
        }
      }
    }
  }

  private fun addAndroidRunConfiguration(facet: AndroidFacet) {
    LOG.debug { "addAndroidRunConfiguration($facet)" }
    val module = facet.module
    val project = module.project
    val runManager = runReadAction {
      if (project.isDisposed) return@runReadAction null
      RunManager.getInstance(project)
    } ?: return LOG.debug { "addAndroidRunConfiguration: Get RunManager for module $module and project $project - project s already disposed." }

    val projectNameInExternalSystemStyle = PathUtil.suggestFileName(project.name, true, false)
    val moduleName = module.getModuleSystem().getDisplayNameForModuleGroup()
    val configurationName = moduleName.removePrefix("$projectNameInExternalSystemStyle.")
    LOG.debug {
      "addAndroidRunConfiguration: project.name = ${project.name}, " +
      "projectNameInExternalSystemStyle = $projectNameInExternalSystemStyle, " +
      "moduleName = ${moduleName}, " +
      "configurationName = $configurationName"
    }
    val settings = runReadAction {
      if (project.isDisposed) return@runReadAction null
      runManager.createConfiguration(configurationName, AndroidRunConfigurationType::class.java)
    } ?: return LOG.debug { "addAndroidRunConfiguration: Create run configuration $configurationName - project s already disposed." }
    val configuration = settings.configuration as AndroidRunConfiguration
    configuration.setModule(module)
    if (facet.configuration.projectType == AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP) {
      configuration.setLaunchUrl(InstantApps.getDefaultInstantAppUrl(facet))
    }
    else {
      configuration.MODE = AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY
    }

    configuration.deployTargetContext.targetSelectionMode = TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX

    runReadAction {
      if (!project.isDisposed) {
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
      }
      else {
        LOG.debug { "addAndroidRunConfiguration: Add run configuration $settings - project s already disposed." }
      }
    }
  }

  private fun hasDefaultLauncherActivity(facet: AndroidFacet): Boolean {
    val manifest = Manifest.getMainManifest(facet) ?: return false
    return runReadAction { DefaultActivityLocator.hasDefaultLauncherActivity(manifest) }
  }

  private suspend fun createWearConfigurations(module: Module): List<RunnerAndConfigurationSettings> {
    val wearComponents = extractWearComponents(module)
    val wearComponentsUsedInRunConfigurations = wearComponentsUsedInRunConfigurations(module.project)
    return wearComponents
      .filter { it.name !in wearComponentsUsedInRunConfigurations }
      .map { createWearConfiguration(module, it) }
  }

  private fun createWearConfiguration(module: Module, component: WearComponent): RunnerAndConfigurationSettings {
    val runManager = RunManager.getInstance(module.project)
    val configurationAndSettings = runManager.createConfiguration(configurationName(module, component), component.configurationFactory)
    val configuration = configurationAndSettings.configuration as AndroidWearConfiguration
    configuration.setModule(module)
    configuration.componentLaunchOptions.componentName = component.name
    return configurationAndSettings
  }

  private fun configurationName(module: Module, component: WearComponent): String {
    val presentableComponentName = JavaExecutionUtil.getPresentableClassName(component.name)
    val projectNameInExternalSystemStyle = PathUtil.suggestFileName(module.project.name, true, false)
    return "${module.name.removePrefix("$projectNameInExternalSystemStyle.")}.$presentableComponentName"
  }

  private suspend fun extractWearComponents(module: Module): List<WearComponent> {
    val modificationTracker = MergedManifestModificationTracker.getInstance(module)
    val dumbServiceTracker = DumbService.getInstance(module.project)
    val wearComponentsCache =
      module.getUserData(extractWearComponentsCacheKey)
        ?: ChangeTrackerCachedValue.softReference<List<WearComponent>>().also {
          module.putUserData(extractWearComponentsCacheKey, it)
        }
    return ChangeTrackerCachedValue.get(
      wearComponentsCache,
      { extractWearComponentsNonCached(module) },
      ChangeTracker(
        ChangeTracker { modificationTracker.modificationCount },
        ChangeTracker { dumbServiceTracker.modificationTracker.modificationCount },
      ),
    )
  }

  private suspend fun extractWearComponentsNonCached(module: Module): List<WearComponent> {
    return module.project.runReadActionInSmartModeWithIndexes {
      val manifests = module.getModuleSystem()
        .getMergedManifestContributors().let {
          val primaryManifest = it.primaryManifest
            ?.let { file -> AndroidUtils.loadDomElement(module, file, Manifest::class.java) }
            ?: return@runReadActionInSmartModeWithIndexes emptyList()

          if (!isWatchFeatureRequired(primaryManifest)) {
            return@runReadActionInSmartModeWithIndexes emptyList()
          }

          val libraryManifests = it.libraryManifests.mapNotNull { file ->
            AndroidUtils.loadDomElement(module, file, Manifest::class.java)
          }

          listOf(primaryManifest) + libraryManifests
        }

      val servicePsiClasses = manifests.flatMap { it.application.services.mapNotNull { service -> service.serviceClass.value } }
      servicePsiClasses.mapNotNull { psiClass ->
        val qualifiedName = psiClass.qualifiedName ?: return@mapNotNull null
        val configurationFactory = wearConfigurationFactory(psiClass) ?: return@mapNotNull null
        WearComponent(qualifiedName, configurationFactory)
      }
    }
  }

  private fun wearConfigurationFactory(psiClass: PsiClass): ConfigurationFactory? {
    return wearConfigurationProducers.find { it.isValidService(psiClass) }?.configurationFactory
  }

  private fun wearComponentsUsedInRunConfigurations(project: Project): Set<String> {
    return RunManager.getInstance(project)
      .allConfigurationsList
      .filterIsInstance<AndroidWearConfiguration>()
      .mapNotNull { it.componentLaunchOptions.componentName }
      .toSet()
  }

  private fun isWatchFeatureRequired(manifest: Manifest): Boolean {
    return manifest.usesFeatures.any { feature ->
      val isWearFeature = feature.name.value == UsesFeature.HARDWARE_TYPE_WATCH
      val isRequired = feature.required.stringValue == null || feature.required.stringValue == VALUE_TRUE
      isWearFeature && isRequired
    }
  }

  private data class WearComponent(val name: String, val configurationFactory: ConfigurationFactory)

  companion object {
    @JvmStatic
    val instance: AndroidRunConfigurations
      get() = ApplicationManager.getApplication().getService(AndroidRunConfigurations::class.java)

    private val extractWearComponentsCacheKey =
      Key<ChangeTrackerCachedValue<List<WearComponent>>>("extractWearComponents")
  }
}
