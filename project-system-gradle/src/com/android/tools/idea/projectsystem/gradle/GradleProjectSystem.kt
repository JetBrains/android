/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.builder.model.AndroidProject.PROJECT_TYPE_APP
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystemProvider
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.GradleFilePsiMerger
import com.android.tools.idea.templates.GradleFileSimpleMerger
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Contract
import java.nio.file.Path

class GradleProjectSystem(val project: Project) : AndroidProjectSystem, AndroidProjectSystemProvider {
  val ID = "com.android.tools.idea.GradleProjectSystem"

  override val id: String
    get() = ID

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(GradleProjectSystem::class.java))
  }

  override fun isApplicable(): Boolean {
    return GradleProjectInfo.getInstance(project).isBuildWithGradle
  }

  override fun allowsFileCreation() = true

  override fun getDefaultApkFile(): VirtualFile? {
    return ModuleManager.getInstance(project).modules.asSequence()
        .mapNotNull { AndroidModuleModel.get(it) }
        .filter { it.androidProject.projectType == PROJECT_TYPE_APP }
        .flatMap { it.selectedVariant.mainArtifact.outputs.asSequence() }
        .map { it.mainOutputFile.outputFile }
        .find { it.exists() }
        ?.let { VfsUtil.findFileByIoFile(it, true) }
  }

  override fun buildProject() {
    GradleProjectBuilder.getInstance(project).compileJava()
  }

  override fun syncProject(reason: AndroidProjectSystem.SyncReason, requireSourceGeneration: Boolean): ListenableFuture<AndroidProjectSystem.SyncResult> {
    return syncProject(project, reason, requireSourceGeneration)
  }

  /**
   * This method will add the ":+" to the given dependency.
   * For Guava, for example: the dependency coordinate will not include the version:
   * com.google.guava:guava
   * and this method will add "+" as the version of the dependency to add.
   * @param dependency The dependency dependency without version.
   */
  override fun addDependency(module: Module, dependency: String) {
    val manager = GradleDependencyManager.getInstance(module.project)
    val coordinate = GradleCoordinate.parseCoordinateString(dependency + ":+")
    if (coordinate != null) {
      manager.ensureLibraryIsIncluded(module, listOf(coordinate), null)
    }
  }

  override fun mergeBuildFiles(dependencies: String,
                      destinationContents: String,
                      supportLibVersionFilter: String?): String {
    return if (project.isInitialized) {
      GradleFilePsiMerger.mergeGradleFiles(dependencies, destinationContents, project, supportLibVersionFilter)
    }
    else {
      GradleFileSimpleMerger.mergeGradleFiles(dependencies, destinationContents, project, supportLibVersionFilter)
    }
  }

  override val projectSystem = this

  override fun getModuleTemplates(module: Module, targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    return GradleAndroidModuleTemplate.getModuleTemplates(module, targetDirectory)
  }

  override fun canGeneratePngFromVectorGraphics(module: Module): CapabilityStatus {
    return supportsPngGeneration(module)
  }
}
