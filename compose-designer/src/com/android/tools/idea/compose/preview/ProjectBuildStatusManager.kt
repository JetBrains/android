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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.util.PsiFileChangeDetector
import com.android.tools.idea.compose.preview.util.hasBeenBuiltSuccessfully
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This represents the build status of the project without taking into account any file modifications.
 */
private sealed class ProjectBuildStatus {
  /** The project is indexing or not synced yet */
  object NotReady : ProjectBuildStatus()

  /** The project has not been built */
  object NeedsBuild : ProjectBuildStatus()

  /** The project is compiled and up to date */
  object Built : ProjectBuildStatus()
}

/** The project status */
sealed class ProjectStatus {
  /** The project is indexing or not synced yet */
  object NotReady : ProjectStatus()

  /** The project needs to be built */
  object NeedsBuild : ProjectStatus()

  /** The project is compiled but one or more files are out of date **/
  object OutOfDate : ProjectStatus()

  /** The project is compiled and up to date */
  object Ready : ProjectStatus()
}

private val LOG = Logger.getInstance(ProjectStatus::class.java)

/**
 * Class managing the build status of a project and its state.
 *
 * @param parentDisposable [Disposable] to track for disposing this manager.
 * @param editorFile the file in the editor to track changes and the build status. If the project has not been
 *  built since it was open, this file is used to find if there are any existing .class files that indicate that has
 *  been built before.
 */
class ProjectBuildStatusManager(parentDisposable: Disposable, private val editorFile: PsiFile) {
  private val project: Project = editorFile.project
  private val fileChangeDetector = PsiFileChangeDetector.getInstance {
    !LiveLiteralsService.getInstance(project).isElementManaged(it)
  }
  private val _isBuilding = AtomicBoolean(false)
  private var projectBuildStatus: ProjectBuildStatus = ProjectBuildStatus.NotReady
    set(value) {
      if (field != value) {
        LOG.debug("Status change old = $field, new = $value")
        field = value
      }
    }
  val status: ProjectStatus
    get() = when {
      projectBuildStatus == ProjectBuildStatus.NotReady -> ProjectStatus.NotReady
      isBuildOutOfDate() -> ProjectStatus.OutOfDate
      projectBuildStatus == ProjectBuildStatus.NeedsBuild -> ProjectStatus.NeedsBuild
      else -> ProjectStatus.Ready
    }

  val isBuilding: Boolean get() = _isBuilding.get()

  init {
    Disposer.register(parentDisposable) {
      fileChangeDetector.clearMarks(editorFile)
    }
    ProjectSystemService.getInstance(project).projectSystem.getBuildManager().addBuildListener(parentDisposable, object : ProjectSystemBuildManager.BuildListener {
      override fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {
        _isBuilding.set(true)
        LOG.debug("buildStarted $mode")
        if (mode == ProjectSystemBuildManager.BuildMode.CLEAN) {
          projectBuildStatus = ProjectBuildStatus.NeedsBuild
        }
      }

      override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
        _isBuilding.set(false)
        LOG.debug("buildFinished $result")
        if (result.mode == ProjectSystemBuildManager.BuildMode.CLEAN) {
          fileChangeDetector.markFileAsUpToDate(editorFile)
          return
        }
        projectBuildStatus = if (result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS) {
          fileChangeDetector.markFileAsUpToDate(editorFile)
          ProjectBuildStatus.Built
        }
        else {
          when (projectBuildStatus) {
            // If the project was ready before, we keep it as Ready since it was just the new build
            // that failed.
            ProjectBuildStatus.Built -> ProjectBuildStatus.Built
            // If the project was not ready, then it needs a build since this one failed.
            else -> ProjectBuildStatus.NeedsBuild
          }
        }
      }
    })

    LiveLiteralsService.getInstance(project).addOnManagedElementsUpdatedListener(parentDisposable, object: LiveLiteralsService.ManagedElementsUpdatedListener {
      override fun onChange(file: PsiFile) {
        // The managed elements for a file have changed. If it's for the file we are monitoring, we need to mark the file as updated
        // to ensure the new PsiElement filter is applied.
        if (file.isEquivalentTo(editorFile) && projectBuildStatus !is ProjectBuildStatus.NotReady) {
          fileChangeDetector.forceMarkFileAsUpToDate(editorFile)
        }
      }
    })

    project.runWhenSmartAndSyncedOnEdt(parentDisposable, {
      fileChangeDetector.markFileAsUpToDate(editorFile)

      if (projectBuildStatus === ProjectBuildStatus.NotReady) {
        // Set the initial state of the project and initialize the modification count.
        projectBuildStatus = if (hasBeenBuiltSuccessfully(project) { editorFile }) ProjectBuildStatus.Built else ProjectBuildStatus.NeedsBuild
      }
    })
  }

  private fun isBuildOutOfDate() = fileChangeDetector.hasFileChanged(editorFile)
}