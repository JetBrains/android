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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.java.JavaFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL
import com.android.tools.idea.gradle.project.sync.setup.post.setUpModules
import com.android.tools.idea.gradle.project.upgrade.maybeRecommendPluginUpgrade
import com.android.tools.idea.gradle.project.upgrade.shouldForcePluginUpgrade
import com.android.tools.idea.gradle.util.AndroidStudioPreferences
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.gradle.variant.conflict.ConflictSet
import com.android.tools.idea.model.AndroidModel
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger
import com.intellij.execution.RunConfigurationProducerService
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
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

      // Opening a project without .idea directory (including a newly created).
      if (gradleProjectInfo.isImportedProject) return true

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

    if (IdeInfo.getInstance().isAndroidStudio) {
      ExternalSystemProjectTrackerSettings.getInstance(project).autoReloadType = ExternalSystemProjectTrackerSettings.AutoReloadType.NONE
      showNeededNotifications(project)
    }
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
    with(moduleManager.modifiableModel) {
      modulesToRemove.forEach {
        LOG.warn(
          "Disposing module '${it.name}' which is empty, not registered with the external system and '${it.moduleFilePath}' does not exist.")
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

  val projectDataNodes: List<DataNode<ProjectData>> =
    GradleSettings.getInstance(project)
      .linkedProjectsSettings
      .mapNotNull { it.externalProjectPath }
      .toSet()
      .map {
        val externalProjectInfo = projectDataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, it)
        if (externalProjectInfo != null && externalProjectInfo.lastImportTimestamp != externalProjectInfo.lastSuccessfulImportTimestamp) {
          requestSync("Sync failed in last import attempt. Path: ${externalProjectInfo.externalProjectPath}")
          return
        }
        externalProjectInfo?.externalProjectStructure ?: run { requestSync("DataNode<ProjectData> not found for $it"); return }
      }

  if (projectDataNodes.isEmpty()) {
    requestSync("No linked projects found")
    return
  }

  val existingGradleModules = moduleManager.modules.filter { ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, it) }

  val facets =
    existingGradleModules
      .flatMap { module ->
        FacetManager.getInstance(module).let {
          it.getFacetsByType(GradleFacet.getFacetTypeId()) +
          it.getFacetsByType(AndroidFacet.ID) +
          it.getFacetsByType(JavaFacet.getFacetTypeId()) +
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
      //
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
          "Cannot find any of:\n ${expectedUrls.joinToString(separator = ",\n") { it }}\n in ${library.name}")
        return
      }
    }


  val modulesById =
    existingGradleModules
      .asSequence()
      .mapNotNull { module ->
        val externalId = ExternalSystemApiUtil.getExternalProjectId(module) ?: return@mapNotNull null
        externalId to module
      }
      .toMap()

  val moduleToModelPairs: Collection<Pair<Module, DataNode<ModuleData>>> =
    projectDataNodes.flatMap { projectData ->
      projectData
        .modules()
        .map { node ->
          val externalId = node.data.id
          val module = modulesById[externalId] ?: run { requestSync("Module $externalId not found"); return }
          module to node
        }
    }

  val attachModelActions = moduleToModelPairs.flatMap { (module, moduleDataNode) ->

    fun AndroidModuleModel.validate() =
      // the use of `project' here might look dubious (since we're in startup) but the operation of shouldForcePluginUpgrade does not
      // depend on the state of the project information.
      !shouldForcePluginUpgrade(project, modelVersion, GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get()))

    /** Returns `null` if validation fails. */
    fun <T, V : Facet<*>> prepare(
      dataKey: Key<T>,
      getFacet: Module.() -> V?,
      attach: V.(T) -> Unit,
      configure: T.(Module) -> Unit = { _ -> },
      validate: T.() -> Boolean = { true }
    ): (() -> Unit)? {
      val model =
        ExternalSystemApiUtil
          .getChildren(moduleDataNode, dataKey)
          .singleOrNull() // None or one node is expected here.
          ?.data
        ?: return { /* Nothing to do if no model present. */ }
      if (!model.validate()) requestSync("invalid model found for $dataKey in ${module.name}")
      val facet = module.getFacet() ?: run {
        requestSync("no facet found for $dataKey in ${module.name} module")
        return null  // Missing facet detected, triggering sync.
      }
      facets.remove(facet)
      model.configure(module)
      return { facet.attach(model) }
    }

    listOf(
      prepare(ANDROID_MODEL, AndroidFacet::getInstance, AndroidModel::set, AndroidModuleModel::setModule,
              validate = AndroidModuleModel::validate) ?: return,
      prepare(JAVA_MODULE_MODEL, JavaFacet::getInstance, JavaFacet::setJavaModuleModel) ?: return,
      prepare(GRADLE_MODULE_MODEL, GradleFacet::getInstance, GradleFacet::setGradleModuleModel) ?: return,
      prepare(NDK_MODEL, { NdkFacet.getInstance(this) }, NdkFacet::setNdkModuleModel) ?: return
    )
  }

  if (facets.isNotEmpty()) {
    requestSync("Cached models not available for:\n" + facets.joinToString(separator = ",\n") { "${it.module.name} : ${it.typeId}" })
    return
  }

  LOG.info("Up-to-date models found in the cache. Not invoking Gradle sync.")
  attachModelActions.forEach { it() }

  additionalProjectSetup(project)

  GradleSyncState.getInstance(project).syncSkipped(null)
}

private fun additionalProjectSetup(project: Project) {
  AndroidPluginInfo.findFromModel(project)?.maybeRecommendPluginUpgrade(project)
  ConflictSet.findConflicts(project).showSelectionConflicts()
  ProjectStructure.getInstance(project).analyzeProjectStructure()
  setUpModules(project)
}

private fun removeGradleProducersFromIgnoredList(project: Project) {
  val producerService = RunConfigurationProducerService.getInstance(project)
  producerService.state.ignoredProducers.remove(AllInPackageGradleConfigurationProducer::class.java.name)
  producerService.state.ignoredProducers.remove(TestClassGradleConfigurationProducer::class.java.name)
  producerService.state.ignoredProducers.remove(TestMethodGradleConfigurationProducer::class.java.name)
}

