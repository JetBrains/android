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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.java.JavaFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncState.Companion.getInstance
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL
import com.android.tools.idea.gradle.project.sync.setup.post.setUpModules
import com.android.tools.idea.gradle.util.AndroidStudioPreferences
import com.android.tools.idea.gradle.variant.conflict.ConflictSet
import com.android.tools.idea.model.AndroidModel
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger
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
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.android.facet.AndroidFacet
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

    if (shouldSyncOrAttachModels()) {
      removeEmptyModules(project)
      attachCachedModelsOrTriggerSync(project, gradleProjectInfo)
    }

    gradleProjectInfo.isSkipStartupActivity = false

    // Disable all settings sections that we don't want to be present in Android Studio.
    // See AndroidStudioPreferences for a full list.
    AndroidStudioPreferences.cleanUpPreferences(project)
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
  val caches = DataNodeCaches.getInstance(project)

  fun findProjectDataNode(externalProjectPath: String): DataNode<ProjectData>? =
    projectDataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, externalProjectPath)?.externalProjectStructure

  fun findModule(internalName: String): Module? =
    moduleManager.findModuleByName(internalName)

  fun DataNode<ProjectData>.modules(): Collection<DataNode<ModuleData>> =
    ExternalSystemApiUtil.findAllRecursively(this, ProjectKeys.MODULE)

  // TODO(b/155467517): Reconsider the way we launch sync when GradleSyncInvoker is deleted. We may want to handle each external project
  //                    path individually.
  fun requestSync(reason: String) {
    LOG.info("Requesting Gradle sync ($reason).")
    val trigger = if (gradleProjectInfo.isNewProject) Trigger.TRIGGER_PROJECT_NEW else Trigger.TRIGGER_PROJECT_REOPEN
    GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(trigger))
  }

  val projectDataNodes: Collection<DataNode<ProjectData>> =
    GradleSettings.getInstance(project)
      .linkedProjectsSettings
      .mapNotNull { it.externalProjectPath }
      .toSet()
      .map {
        findProjectDataNode(it) ?: run { requestSync("DataNode<ProjectData> not found for $it"); return }
      }

  if (projectDataNodes.isEmpty()) {
    requestSync("No linked projects found")
    return
  }

  if (projectDataNodes.any { caches.isCacheMissingModels(it) }) {
    requestSync("Some models are missing")  // TODO(solodkyy): Merge with attaching and report details.
    return
  }

  if (GradleSyncExecutor.areCachedFilesMissing(project)) {
    requestSync("Some .jar files missing")
    return
  }

  val moduleToModelPairs: Collection<Pair<Module, DataNode<ModuleData>>> =
    projectDataNodes.flatMap { projectData ->
      projectData
        .modules()
        .map { node ->
          val internalName = node.data.internalName
          val module = findModule(internalName) ?: run { requestSync("Module $internalName not found"); return }
          module to node
        }
    }

  val attachModelActions = moduleToModelPairs.flatMap { (module, moduleDataNode) ->

    /** Returns `null` if validation fails. */
    fun <T, V> prepare(dataKey: Key<T>, getFacet: Module.() -> V?, attach: V.(T) -> Unit): (() -> Unit)? {
      val model =
        ExternalSystemApiUtil
          .getChildren(moduleDataNode, dataKey)
          .singleOrNull() // None or one node is expected here.
          ?.data
        ?: return { /* Nothing to do if no model present. */ }
      val facet = module.getFacet() ?: run {
        requestSync("no facet found for $dataKey in ${module.name} module")
        return null  // Missing facet detected, triggering sync.
      }
      return { facet.attach(model) }
    }

    listOf(
      prepare(ANDROID_MODEL, AndroidFacet::getInstance, AndroidModel::set) ?: return,
      prepare(JAVA_MODULE_MODEL, JavaFacet::getInstance, JavaFacet::setJavaModuleModel) ?: return,
      prepare(GRADLE_MODULE_MODEL, GradleFacet::getInstance, GradleFacet::setGradleModuleModel) ?: return,
      prepare(NDK_MODEL, NdkFacet::getInstance, NdkFacet::setNdkModuleModel) ?: return
    )
  }

  LOG.info("Up-to-date models found in the cache. Not invoking Gradle sync.")
  attachModelActions.forEach { it() }

  additionalProjectSetup(project)

  getInstance(project).syncSkipped(null)
}

private fun additionalProjectSetup(project: Project) {
  ConflictSet.findConflicts(project).showSelectionConflicts()
  ProjectStructure.getInstance(project).analyzeProjectStructure()
  setUpModules(project)
}
