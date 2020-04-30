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
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.project.sync.setup.post.setUpModules
import com.android.tools.idea.gradle.util.AndroidStudioPreferences
import com.android.tools.idea.gradle.variant.conflict.ConflictSet
import com.android.tools.idea.model.AndroidModel
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.diagnostic.Logger
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
    if (!gradleProjectInfo.isSkipStartupActivity &&

        // We only request sync if we know this is an Android project.
        (
          // Opening an IDEA project with Android modules (AS and IDEA - i.e. previously synced).
          gradleProjectInfo.androidModules.isNotEmpty()

          // Opening a Gradle project with .idea but no .iml files or facets (Typical for AS but not in IDEA)
          || IdeInfo.getInstance().isAndroidStudio && gradleProjectInfo.isBuildWithGradle

          // Opening a project without .idea directory (including a newly created).
          || gradleProjectInfo.isImportedProject)) {
      attachCachedModelsOrTriggerSync(project, gradleProjectInfo)
    }
    gradleProjectInfo.isSkipStartupActivity = false

    // Disable all settings sections that we don't want to be present in Android Studio.
    // See AndroidStudioPreferences for a full list.
    AndroidStudioPreferences.cleanUpPreferences(project)
  }
}

/**
 * Attempts to see if the models cached by IDEAs external system are valid, if they are then we attach them to the facet,
 * if they are not then we request a project sync in order to ensure that the IDE has access to all of models it needs to function.
 */
private fun attachCachedModelsOrTriggerSync(project: Project, gradleProjectInfo: GradleProjectInfo) {
  val linkedProjectPaths = GradleSettings.getInstance(project).linkedProjectsSettings.mapNotNull { it.externalProjectPath }.toSet()
  val projectDataManager = ProjectDataManager.getInstance()
  val projectsData: Collection<DataNode<ProjectData>> =
    linkedProjectPaths.mapNotNull {
      projectDataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, it)?.externalProjectStructure
    }
  val trigger =
    if (gradleProjectInfo.isNewProject) GradleSyncStats.Trigger.TRIGGER_PROJECT_NEW else GradleSyncStats.Trigger.TRIGGER_PROJECT_REOPEN
  val request = GradleSyncInvoker.Request(trigger)
  val caches = DataNodeCaches.getInstance(project)
  // If we don't have any cached data then do a full re-sync
  if (projectsData.isEmpty() ||
      projectsData.any { caches.isCacheMissingModels(it) } ||
      GradleSyncExecutor.areCachedFilesMissing(project)) {
    GradleSyncInvoker.getInstance().requestProjectSync(project, request)
  }
  else {
    // Otherwise attach the models we found from the cache.
    Logger.getInstance(AndroidGradleProjectStartupActivity::class.java)
      .info("Up-to-date models found in the cache. Not invoking Gradle sync.")
    projectsData.forEach { attachModelsToFacets(project, it) }

    ConflictSet.findConflicts(project).showSelectionConflicts()
    ProjectStructure.getInstance(project).analyzeProjectStructure()
    setUpModules(project)

    getInstance(project).syncSkipped(null)
  }
}

private fun attachModelsToFacets(project: Project, projectData: DataNode<ProjectData>) {
  val moduleManager = ModuleManager.getInstance(project)
  val moduleDataNodes = ExternalSystemApiUtil.findAllRecursively(projectData, ProjectKeys.MODULE)

  moduleDataNodes.forEach { moduleDataNode: DataNode<ModuleData> ->
    val module = moduleManager.findModuleByName(moduleDataNode.data.internalName)
    if (module != null) {
      attachModelsToFacets(module, moduleDataNode)
    }
  }
}

private fun attachModelsToFacets(module: Module, moduleDataNode: DataNode<ModuleData>) {
  fun <T, V> attachModelToFacet(dataKey: Key<T>, facet: Module.() -> V?, attach: V.(T) -> Unit) {
    val model =
      ExternalSystemApiUtil
        .getChildren(moduleDataNode, dataKey)
        .singleOrNull() // None or one node is expected here.
        ?.data
      ?: return
    module.facet()?.attach(model)
  }

  attachModelToFacet(AndroidProjectKeys.ANDROID_MODEL, AndroidFacet::getInstance, AndroidModel::set)
  attachModelToFacet(AndroidProjectKeys.JAVA_MODULE_MODEL, JavaFacet::getInstance, JavaFacet::setJavaModuleModel)
  attachModelToFacet(AndroidProjectKeys.GRADLE_MODULE_MODEL, GradleFacet::getInstance, GradleFacet::setGradleModuleModel)
  attachModelToFacet(AndroidProjectKeys.NDK_MODEL, NdkFacet::getInstance, NdkFacet::setNdkModuleModel)
}

