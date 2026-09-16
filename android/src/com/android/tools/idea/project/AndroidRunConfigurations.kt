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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProductionAndroidModule
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.TargetSelectionMode
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.android.tools.idea.run.configuration.AndroidComplicationRunConfigurationProducer
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfiguration
import com.android.tools.idea.run.configuration.AndroidDeclarativeWatchFaceConfigurationType
import com.android.tools.idea.run.configuration.AndroidTileRunConfigurationProducer
import com.android.tools.idea.run.configuration.AndroidWatchFaceRunConfigurationProducer
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PathUtil
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

private val wearConfigurationProducers =
  listOf(AndroidTileRunConfigurationProducer(), AndroidComplicationRunConfigurationProducer(), AndroidWatchFaceRunConfigurationProducer())

private val LOG: Logger by lazy { Logger.getInstance(AndroidRunConfigurations::class.java) }

class AndroidRunConfigurations {

  suspend fun createRunConfigurations(project: Project) {
    createAndroidRunConfigurations(project)
    createDeclarativeWatchFaceConfigurations(project)
    // create the Android run and declarative watch face configurations first as
    // we limit the number of wear configurations based on the existing number of configurations.
    createWearConfigurations(project)
  }

  private fun createAndroidRunConfigurations(project: Project) {
    project.getAndroidFacets().filter { it.configuration.isAppProject }.forEach { createAndroidRunConfiguration(it) }
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

  private fun createDeclarativeWatchFaceConfigurations(project: Project) {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_RUN_CONFIGURATION.get()) {
      return
    }
    project.getAndroidFacets().filter { it.configuration.isAppProject }.forEach { facet -> createDeclarativeWatchFaceConfiguration(facet) }
  }

  /**
   * Creates an [AndroidDeclarativeWatchFaceConfiguration] run configuration if the module is a Declarative Watch Face module. The module
   * must have a `res/xml/watch_face_info.xml` file and shouldn't have any activities or services.
   *
   * The run configuration is only added if there are no existing run configurations based on the [facet]'s module. If an existing
   * declarative watch face run configuration already exists for the module, we don't want to create duplicates.
   */
  private fun createDeclarativeWatchFaceConfiguration(facet: AndroidFacet) {
    if (!LaunchUtils.isWatchFeatureRequired(facet)) {
      return
    }
    val module = facet.module
    val configurations = RunManager.getInstance(module.project).getConfigurationsList(AndroidDeclarativeWatchFaceConfigurationType())
    for (configuration in configurations) {
      if (configuration is AndroidDeclarativeWatchFaceConfiguration && configuration.configurationModule.module == module) {
        // There is already a run configuration for this module.
        return
      }
    }

    val hasActivitiesOrServices = runReadAction {
      val application = Manifest.getMainManifest(facet)?.application
      application?.activities?.isNotEmpty() == true ||
        application?.activityAliases?.isNotEmpty() == true ||
        application?.services?.isNotEmpty() == true
    }
    if (hasActivitiesOrServices) {
      return
    }

    val watchFaceInfo =
      StudioResourceRepositoryManager.getInstance(module)
        ?.appResources
        ?.getResources(ResourceNamespace.RES_AUTO, ResourceType.XML, "watch_face_info")

    if (watchFaceInfo.isNullOrEmpty()) {
      return
    }

    addDeclarativeWatchFaceConfiguration(facet)
  }

  private fun addDeclarativeWatchFaceConfiguration(facet: AndroidFacet) {
    val module = facet.module
    val project = module.project
    val runManager =
      runReadAction {
        if (project.isDisposed) return@runReadAction null
        RunManager.getInstance(project)
      } ?: return

    val projectNameInExternalSystemStyle = PathUtil.suggestFileName(project.name, true, false)
    val moduleName = module.getModuleSystem().getDisplayNameForModuleGroup()
    val configurationName = resolveWatchFaceName(facet) ?: moduleName.removePrefix("$projectNameInExternalSystemStyle.")
    val settings =
      runReadAction {
        if (project.isDisposed) return@runReadAction null
        runManager.createConfiguration(configurationName, AndroidDeclarativeWatchFaceConfigurationType::class.java)
      } ?: return

    val configuration = settings.configuration as AndroidDeclarativeWatchFaceConfiguration
    configuration.setModule(module)

    runReadAction {
      if (!project.isDisposed) {
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
      }
    }
  }

  private fun resolveWatchFaceName(facet: AndroidFacet): String? {
    return runReadAction {
      if (facet.isDisposed) {
        return@runReadAction null
      }
      val manifest = Manifest.getMainManifest(facet) ?: return@runReadAction null
      val label = manifest.application.label
      val labelPsiFile = label.xmlElement?.containingFile ?: return@runReadAction null
      val resourceName = runReadAction { label.value?.resourceName } ?: return@runReadAction null
      val resourceReference = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, resourceName)
      val resolver = ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(labelPsiFile.virtualFile).resourceResolver
      resolver.getResolvedResource(resourceReference)?.value
    }
  }

  /** Creates component-based [AndroidWearConfiguration]s. Components are Wear Tiles, Complications and WatchFace Services. */
  private suspend fun createWearConfigurations(project: Project) {
    if (!StudioFlags.WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED.get()) {
      return
    }
    val runManager =
      runReadAction {
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
    project
      .getAndroidFacets()
      .filter { it.configuration.isAppProject }
      .filter { LaunchUtils.isWatchFeatureRequired(it) }
      .forEach {
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
        wearRunConfigurationsToAdd.forEach { runManager.addConfiguration(it) }
      }
    }
  }

  private fun addAndroidRunConfiguration(facet: AndroidFacet) {
    LOG.debug { "addAndroidRunConfiguration($facet)" }
    val module = facet.module
    val project = module.project
    val runManager =
      runReadAction {
        if (project.isDisposed) return@runReadAction null
        RunManager.getInstance(project)
      }
        ?: return LOG.debug {
          "addAndroidRunConfiguration: Get RunManager for module $module and project $project - project s already disposed."
        }

    val projectNameInExternalSystemStyle = PathUtil.suggestFileName(project.name, true, false)
    val moduleName = module.getModuleSystem().getDisplayNameForModuleGroup()
    val configurationName = moduleName.removePrefix("$projectNameInExternalSystemStyle.")
    LOG.debug {
      "addAndroidRunConfiguration: project.name = ${project.name}, " +
        "projectNameInExternalSystemStyle = $projectNameInExternalSystemStyle, " +
        "moduleName = ${moduleName}, " +
        "configurationName = $configurationName"
    }
    val settings =
      runReadAction {
        if (project.isDisposed) return@runReadAction null
        runManager.createConfiguration(configurationName, AndroidRunConfigurationType::class.java)
      } ?: return LOG.debug { "addAndroidRunConfiguration: Create run configuration $configurationName - project s already disposed." }
    val configuration = settings.configuration as AndroidRunConfiguration
    configuration.setModule(module)
    configuration.MODE = AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY

    configuration.deployTargetContext.targetSelectionMode = TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX

    runReadAction {
      if (!project.isDisposed) {
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
      } else {
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
    return wearComponents.filter { it.name !in wearComponentsUsedInRunConfigurations }.map { createWearConfiguration(module, it) }
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
    val facet = module.androidFacet ?: return emptyList()
    return smartReadAction(project = module.project) {
      val overrides = facet.getModuleSystem().getManifestOverrides()
      val packageName = facet.getModuleSystem().getPackageName()
      val psiFacade = JavaPsiFacade.getInstance(module.project)
      val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(facet.getProductionAndroidModule())

      val servicePsiClasses =
        AndroidManifestIndex.getDataForMergedManifestContributors(facet)
          .map { manifest ->
            ProgressManager.checkCanceled()
            manifest.services.mapNotNull {
              val name = it.name ?: return@mapNotNull null
              val resolvedName = overrides.resolvePlaceholders(name)
              val qualifiedName =
                when {
                  packageName == null -> resolvedName
                  resolvedName.startsWith('.') -> packageName + resolvedName
                  resolvedName.contains('.') -> resolvedName
                  else -> "$packageName.$resolvedName"
                }
              psiFacade.findClass(qualifiedName, scope)
            }
          }
          .toList()
          .flatten()
          .toSet()

      servicePsiClasses.mapNotNull { psiClass ->
        ProgressManager.checkCanceled()
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

  private data class WearComponent(val name: String, val configurationFactory: ConfigurationFactory)

  companion object {
    @JvmStatic
    val instance: AndroidRunConfigurations
      get() = ApplicationManager.getApplication().getService(AndroidRunConfigurations::class.java)
  }
}
