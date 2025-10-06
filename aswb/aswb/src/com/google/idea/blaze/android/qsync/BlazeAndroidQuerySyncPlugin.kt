/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.qsync

import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder.Companion.create
import com.android.tools.idea.projectsystem.ScopeType
import com.google.common.util.concurrent.Futures
import com.google.idea.blaze.android.projectsystem.BazelModuleSystem
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService
import com.google.idea.blaze.android.sdk.BlazeSdkProvider
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel
import com.google.idea.blaze.android.sync.sdk.AndroidSdkFromProjectView
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.model.primitives.WorkspaceType
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.qsync.BlazeQuerySyncPlugin
import com.google.idea.blaze.base.sync.projectview.LanguageSupport
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import java.io.File

/** ASwB sync plugin.  */
class BlazeAndroidQuerySyncPlugin : BlazeQuerySyncPlugin {
  override fun updateProjectSettingsForQuerySync(
    project: Project, context: Context<*>, projectViewSet: ProjectViewSet
  ) {
    if (!isAndroidWorkspace(LanguageSupport.createWorkspaceLanguageSettings(projectViewSet))) {
      return
    }
    val androidSdkPlatform =
      AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet) ?: error("Android SDK platform not found. See build output.")
    val sdk = BlazeSdkProvider.getInstance().findSdk(androidSdkPlatform.androidSdk)
              ?: error("Cannot find SDK entry for ${androidSdkPlatform.androidSdk}")
    val javaLanguageLevel = JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_21)
    val rootManager = ProjectRootManagerEx.getInstanceEx(project)
    rootManager.setProjectSdk(sdk)
    val ext = LanguageLevelProjectExtension.getInstance(project)
    ext.setLanguageLevel(javaLanguageLevel)
  }

  override fun updateProjectStructureForQuerySync(
    project: Project,
    context: Context<*>,
    workspaceRoot: WorkspaceRoot,
    workspaceModule: Module,
    androidResourceDirectories: Set<String>,
    androidSourcePackages: Set<String>,
    workspaceLanguageSettings: WorkspaceLanguageSettings
  ) {
    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return
    }

    val androidResourceDirectoryFiles =
      androidResourceDirectories
        .map { File(workspaceRoot.directory(), it).getAbsoluteFile() }
        .toSet()
    val sourceProvider =
      create(workspaceModule.name, VfsUtilCore.fileToUrl(File("MissingManifest.xml")))
        .withScopeType(ScopeType.MAIN)
        .withResDirectoryUrls(androidResourceDirectoryFiles.map { VfsUtilCore.fileToUrl(it) })
        .build()

    // Set AndroidModel for this AndroidFacet
    val projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet()
    val androidSdkPlatform = AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet)

    val androidModel =
      BlazeAndroidModel(
        project,
        workspaceRoot.directory(),
        sourceProvider,
        Futures.immediateFuture<String>(":workspace"),
        androidSdkPlatform?.androidMinSdkLevel ?: 1
      )
    workspaceModule.service<BazelModuleSystem>().setAndroidModel(androidModel)

    // Register all source java packages as workspace packages
    val rClassBuilder = BlazeLightResourceClassService.Builder(project)
    rClassBuilder.addWorkspacePackages(androidSourcePackages)
    // TODO(b/283282438): Make Preview work with resources in project's res folder in Query Sync
    BlazeLightResourceClassService.getInstance(project).installRClasses(rClassBuilder)
  }

  companion object {
    private fun isAndroidWorkspace(workspaceLanguageSettings: WorkspaceLanguageSettings): Boolean {
      return workspaceLanguageSettings.isWorkspaceType(WorkspaceType.ANDROID)
    }
  }
}
