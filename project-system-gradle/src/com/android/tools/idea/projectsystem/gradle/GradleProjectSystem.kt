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
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.GradleFilePsiMerger
import com.android.tools.idea.templates.GradleFileSimpleMerger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

class GradleProjectSystem(val project: Project) : AndroidProjectSystem {
  private val mySyncManager: ProjectSystemSyncManager = GradleProjectSystemSyncManager(project)

  override fun getSyncManager(): ProjectSystemSyncManager = mySyncManager

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(GradleProjectSystem::class.java))
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

  override fun upgradeProjectToSupportInstantRun(): Boolean {
    return updateProjectToInstantRunTools(project)
  }

  override fun getModuleSystem(module: Module): AndroidModuleSystem {
    return GradleModuleSystem(module)
  }
}
