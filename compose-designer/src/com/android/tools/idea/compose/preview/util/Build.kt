/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer

internal fun requestBuild(project: Project, file: VirtualFile, requestByUser: Boolean) {
  requestBuild(project, listOf(file), requestByUser)
}

internal fun requestBuild(project: Project, files: Collection<VirtualFile>, requestByUser: Boolean) {
  if (project.isDisposed) {
    return
  }

  ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileFilesAndDependencies(files.map { it.getSourceFile() })
}

fun hasExistingClassFile(psiFile: PsiFile?) = if (psiFile is PsiClassOwner) {
  val androidModuleSystem by lazy {
    ReadAction.compute<AndroidModuleSystem?, Throwable> {
      psiFile.getModuleSystem()
    }
  }
  runReadAction { psiFile.classes.mapNotNull { it.qualifiedName } }
    .mapNotNull { androidModuleSystem?.getClassFileFinderForSourceFile(runReadAction { psiFile.virtualFile })?.findClassFile(it) }
    .firstOrNull() != null
}
else false

/**
 * Returns whether the [PsiFile] has been built. It does this by checking the build status of the module if available.
 * If not available, this method will look for the compiled classes and check if they exist.
 *
 * @param project the [Project] the [PsiFile] belongs to.
 * @param lazyFileProvider a lazy provider for the [PsiFile]. It will only be called if needed to obtain the status
 *  of the build.
 */
@Slow
fun hasBeenBuiltSuccessfully(project: Project, lazyFileProvider: () -> PsiFile?): Boolean {
  val result = ProjectSystemService.getInstance(project).projectSystem.getBuildManager().getLastBuildResult()

  if (result.status != ProjectSystemBuildManager.BuildStatus.UNKNOWN) {
    return result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS &&
           result.mode != ProjectSystemBuildManager.BuildMode.CLEAN

  }

  // We do not have information from the last build, try to find if the class file exists
  return hasExistingClassFile(lazyFileProvider())
}

/**
 * Returns whether the [PsiFile] has been built. It does this by checking the build status of the module if available.
 * If not available, this method will look for the compiled classes and check if they exist.
 */
@Slow
fun hasBeenBuiltSuccessfully(psiFilePointer: SmartPsiElementPointer<PsiFile>): Boolean =
  hasBeenBuiltSuccessfully(psiFilePointer.project) { ReadAction.compute<PsiFile, Throwable> { psiFilePointer.element } }

@Suppress("UnstableApiUsage")
private fun VirtualFile.getSourceFile(): VirtualFile = if (!this.isInLocalFileSystem && this is BackedVirtualFile) {
  this.originFile
}
else this