/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver.Companion.shouldDisableForceUpgrades
import com.android.tools.idea.gradle.project.sync.idea.ModuleUtil.linkAndroidModuleGroup
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.IDE_LIBRARY_TABLE
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL
import com.android.tools.idea.gradle.project.sync.idea.findAndSetupSelectedCachedVariantData
import com.android.tools.idea.gradle.project.sync.idea.getSelectedVariantAndAbis
import com.android.tools.idea.gradle.project.upgrade.AgpVersionChecker
import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker
import com.android.tools.idea.gradle.util.AndroidStudioPreferences
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.model.AndroidModel
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger
import com.intellij.execution.RunConfigurationProducerService
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * Syncs Android Gradle project with the persisted project data on startup.
 */
class AndroidGradleProjectStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    val gradleProjectInfo = GradleProjectInfo.getInstance(project)

    fun shouldSyncOrAttachModels(): Boolean {
      if (gradleProjectInfo.isSkipStartupActivity) return false

      // Opening an IDEA project with Android modules (AS and IDEA - i.e. previously synced).
      if (gradleProjectInfo.androidModules.isNotEmpty()) return true

      // Opening a Gradle project with .idea but no .iml files or facets (Typical for AS but not in IDEA)
      if (IdeInfo.getInstance().isAndroidStudio && gradleProjectInfo.isBuildWithGradle) return true

      return false
    }

    // Make sure we remove Gradle producers from the ignoredProducers list for old projects that used to run tests through AndroidJunit.
    // This would allow running unit tests through Gradle for existing projects where Gradle producers where disabled in favor of AndroidJunit.
    removeGradleProducersFromIgnoredList(project)

    if (shouldSyncOrAttachModels()) {
      removeEmptyModules(project)
      attachCachedModelsOrTriggerSync(project, gradleProjectInfo)
    }

    gradleProjectInfo.isSkipStartupActivity = false

    // Disable all settings sections that we don't want to be present in Android Studio.
    // See AndroidStudioPreferences for a full list.
    AndroidStudioPreferences.cleanUpPreferences(project)

    showNeededNotifications(project)
  }
}

private val LOG = Logger.getInstance(AndroidGradleProjectStartupActivity::class.java)

private fun removeEmptyModules(project: Project) {
  val moduleManager = ModuleManager.getInstance(project)
  val modulesToRemove =
    moduleManager
      .modules
      .filter { module ->
        module.isLoaded &&
          module.moduleFile == null &&
          ExternalSystemModulePropertyManager.getInstance(module).getExternalSystemId().isNullOrEmpty() &&
          module.rootManager.let { roots -> roots.contentEntries.isEmpty() && roots.orderEntries.all { it is ModuleSourceOrderEntry } }
      }
      .takeUnless { it.isEmpty() }
      ?: return

  runWriteAction {
    with(moduleManager.getModifiableModel()) {
      modulesToRemove.forEach {
        LOG.warn(
          "Disposing module '${it.name}' which is empty, not registered with the external system and '${it.moduleFilePath}' does not exist."
        )
        disposeModule(it)
      }
      commit()
    }
  }
}

/**
 * Attempts to see if the models cached by IDEAs external system are valid, if they are then we attach them to the facet,
 * if they are not then we request a project sync in order to ensure that the IDE has access to all of models it needs to function.
 */
private fun attachCachedModelsOrTriggerSync(project: Project, gradleProjectInfo: GradleProjectInfo) {

  val moduleManager = ModuleManager.getInstance(project)
  val projectDataManager = ProjectDataManager.getInstance()

  fun DataNode<ProjectData>.modules(): Collection<DataNode<ModuleData>> =
    ExternalSystemApiUtil.findAllRecursively(this, ProjectKeys.MODULE)

  // TODO(b/155467517): Reconsider the way we launch sync when GradleSyncInvoker is deleted. We may want to handle each external project
  //                    path individually.
  fun requestSync(reason: String) {
    LOG.info("Requesting Gradle sync ($reason).")
    val trigger = if (gradleProjectInfo.isNewProject) Trigger.TRIGGER_PROJECT_NEW else Trigger.TRIGGER_PROJECT_REOPEN
    GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(trigger))
  }

  val existingGradleModules = moduleManager.modules.filter { ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, it) }

  val modulesById =
    existingGradleModules
      .asSequence()
      .mapNotNull { module ->
        val externalId = ExternalSystemApiUtil.getExternalProjectId(module) ?: return@mapNotNull null
        externalId to module
      }
      .toMap()

  val projectDataNodes: List<DataNode<ProjectData>> =
    GradleSettings.getInstance(project)
      .linkedProjectsSettings
      .mapNotNull { it.externalProjectPath }
      .toSet()
      .map { externalProjectPath ->
        val externalProjectInfo = projectDataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, externalProjectPath)
        if (externalProjectInfo != null && externalProjectInfo.lastImportTimestamp != externalProjectInfo.lastSuccessfulImportTimestamp) {
          requestSync("Sync failed in last import attempt. Path: ${externalProjectInfo.externalProjectPath}")
          return
        }
        externalProjectInfo?.externalProjectStructure?.modules()?.forEach { moduleDataNode ->
          if (ExternalSystemApiUtil.getChildren(moduleDataNode, ANDROID_MODEL).singleOrNull() != null) {
            moduleDataNode.linkAndroidModuleGroup { data -> modulesById[data.id] }
          }
        }
        val moduleVariants = project.getSelectedVariantAndAbis()
        externalProjectInfo?.findAndSetupSelectedCachedVariantData(moduleVariants)
          ?: run { requestSync("DataNode<ProjectData> not found for $externalProjectPath. Variants: $moduleVariants"); return }
      }


  if (projectDataNodes.isEmpty()) {
    requestSync("No linked projects found")
    return
  }

  val facets =
    existingGradleModules
      .flatMap { module ->
        FacetManager.getInstance(module).let {
          it.getFacetsByType(GradleFacet.getFacetTypeId()) +
            it.getFacetsByType(AndroidFacet.ID) +
            it.getFacetsByType(NdkFacet.facetTypeId)
        }
      }
      .toMutableSet()

  existingGradleModules.asSequence().flatMap { module ->
    ModuleRootManager.getInstance(module)
      .orderEntries.filterIsInstance<LibraryOrderEntry>().asSequence()
      .mapNotNull { it.library }
      .filter { it.name?.startsWith("Gradle: ") ?: false }
      // Module level libraries and libraries not listed in any library table usually represent special kinds of artifacts like local
      // libraries in `lib` folders, generated code, etc. We are interested in libraries with JAR files in the shared Gradle cache.
      .filter { it.table?.tableLevel == LibraryTablesRegistrar.PROJECT_LEVEL }
  }
    .distinct()
    .forEach { library ->
      // CLASSES root contains jar file and res folder, and none of them are guaranteed to exist. Fail validation only if
      // all files are missing. If the library lifetime in the Gradle cache has expired there will be none that exists.
      // TODO(b/160088430): Review when the platform is fixed and not existing entries are correctly removed.
      // For other types of root we do not perform any validations since urls are intentionally or unintentionally not removed
      // from libraries if the location changes. See TODO: b/160088430.
      val expectedUrls = library.getUrls(OrderRootType.CLASSES)
      if (expectedUrls.none { url: String -> VirtualFileManager.getInstance().findFileByUrl(url) != null }) {
        requestSync(
          "Cannot find any of:\n ${expectedUrls.joinToString(separator = ",\n") { it }}\n in ${library.name}"
        )
        return
      }
    }

  class ModuleSetupData(
    val module: Module,
    val dataNode: DataNode<out ModuleData>,
    val gradleAndroidModelFactory: (GradleAndroidModelData) -> GradleAndroidModel
  )

  val moduleSetupData: Collection<ModuleSetupData> =
    projectDataNodes.flatMap { projectData ->
      val libraries =
        ExternalSystemApiUtil.find(projectData, IDE_LIBRARY_TABLE)?.data ?: run { requestSync("IDE library table not found"); return }
      val libraryResolver = IdeLibraryModelResolverImpl.fromLibraryTable(libraries)
      val modelFactory = GradleAndroidModel.createFactory(project, libraryResolver)
      projectData
        .modules()
        .flatMap inner@{ node ->
          val sourceSets = ExternalSystemApiUtil.findAll(node, GradleSourceSetData.KEY)

          val externalId = node.data.id
          val module = modulesById[externalId] ?: run { requestSync("Module $externalId not found"); return }

          if (sourceSets.isEmpty()) {
            listOf(ModuleSetupData(module, node, modelFactory))
          } else {
            sourceSets.map {
              val moduleId = modulesById[it.data.id] ?: run { requestSync("Module $externalId not found"); return }
              ModuleSetupData(moduleId, it, modelFactory)
            } + ModuleSetupData(module, node, modelFactory)
          }
        }
    }

  val attachModelActions = moduleSetupData.flatMap { data ->

    fun GradleAndroidModelData.validate() =
      shouldDisableForceUpgrades() ||
      AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()).let { latestKnown ->
          !ApplicationManager.getApplication().getService(AgpVersionChecker::class.java).versionsAreIncompatible(agpVersion, latestKnown)
        }

    /** Returns `null` if validation fails. */
    fun <T, V : Facet<*>> prepare(
      dataKey: Key<T>,
      getModel: (DataNode<*>, Key<T>) -> T?,
      getFacet: (Module) -> V?,
      attach: V.(T) -> Unit,
      validate: T.() -> Boolean = { true }
    ): (() -> Unit)? {
      val model = getModel(data.dataNode, dataKey) ?: return { /* No model for datanode/datakey pair */ }
      if (!model.validate()) {
        requestSync("invalid model found for $dataKey in ${data.module.name}")
        return null
      }
      val facet = getFacet(data.module) ?: run {
        requestSync("no facet found for $dataKey in ${data.module.name} module")
        return null  // Missing facet detected, triggering sync.
      }
      facets.remove(facet)
      return { facet.attach(model) }
    }

    // For models that can be broken into source sets we need to check the parent datanode for the model
    // For now we check both the current and parent node for code simplicity, once we finalize the layout for NDK and switch to
    // module per source set we should replace this code with were we know the model will be living.
    fun <T> getModelForMaybeSourceSetDataNode(): (DataNode<*>, Key<T>) -> T? {
      return { n, k -> getModelFromDataNode(n, k) ?: n.parent?.let { getModelFromDataNode(it, k) } }
    }
    listOf(
      prepare(
        ANDROID_MODEL,
        getModelForMaybeSourceSetDataNode(),
        AndroidFacet::getInstance,
        { AndroidModel.set(this, data.gradleAndroidModelFactory(it)) },
        validate = GradleAndroidModelData::validate
      ) ?: return,
      prepare(GRADLE_MODULE_MODEL, ::getModelFromDataNode, GradleFacet::getInstance, GradleFacet::setGradleModuleModel) ?: return,
      prepare(NDK_MODEL, ::getModelFromDataNode, NdkFacet::getInstance, NdkFacet::setNdkModuleModel) ?: return
    )
  }

  if (facets.isNotEmpty()) {
    requestSync("Cached models not available for:\n" + facets.joinToString(separator = ",\n") { "${it.module.name} : ${it.typeId}" })
    return
  }

  LOG.info("Up-to-date models found in the cache. Not invoking Gradle sync.")
  attachModelActions.forEach { it() }

  additionalProjectSetup(project)

  GradleSyncStateHolder.getInstance(project).syncSkipped(null)
}

private fun <T> getModelFromDataNode(moduleDataNode: DataNode<*>, dataKey: Key<T>) =
  ExternalSystemApiUtil
    .getChildren(moduleDataNode, dataKey)
    .singleOrNull() // None or one node is expected here.
    ?.data

private fun additionalProjectSetup(project: Project) {
  AndroidPluginInfo.findFromModel(project)?.let { info ->
    project.getService(AssistantInvoker::class.java).maybeRecommendPluginUpgrade(project, info)
  }
  ProjectStructure.getInstance(project).analyzeProjectStructure()
}

private fun removeGradleProducersFromIgnoredList(project: Project) {
  val producerService = RunConfigurationProducerService.getInstance(project)
  producerService.state.ignoredProducers.remove(AllInPackageGradleConfigurationProducer::class.java.name)
  producerService.state.ignoredProducers.remove(TestClassGradleConfigurationProducer::class.java.name)
  producerService.state.ignoredProducers.remove(TestMethodGradleConfigurationProducer::class.java.name)
}
