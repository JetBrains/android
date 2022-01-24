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

import com.android.tools.idea.compose.preview.util.NopPsiFileChangeDetector
import com.android.tools.idea.compose.preview.util.PsiFileChangeDetector
import com.android.tools.idea.compose.preview.util.hasExistingClassFile
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.editors.literals.LiveLiteralsApplicationConfiguration
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.util.application.runReadAction
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
 * A filter to be applied to the file change detection in [ProjectBuildStatusManager]. This allows to ignore some [PsiElement]s when
 * comparing two snapshots of a file, for example, ignore literals.
 * If the filtering set changes, the modification count MUST change.
 */
interface PsiFileSnapshotFilter : ModificationTracker {
  fun accepts(element: PsiElement): Boolean
}

/**
 * A [PsiFileSnapshotFilter] that does not do filtering.
 */
object NopPsiFileSnapshotFilter : PsiFileSnapshotFilter, ModificationTracker by ModificationTracker.NEVER_CHANGED {
  override fun accepts(element: PsiElement): Boolean = true
}

/**
 * Interface representing the current build status of the project.
 */
interface ProjectBuildStatusManager {
  /** True when the project is currently building. */
  val isBuilding: Boolean

  /** The current build [ProjectStatus]. */
  val status: ProjectStatus

  companion object {
    /**
     * Creates a new [ProjectBuildStatusManager].
     *
     * @param parentDisposable [Disposable] to track for disposing this manager.
     * @param psiFile the file in the editor to track changes and the build status. If the project has not been
     *  built since it was open, this file is used to find if there are any existing .class files that indicate that has
     *  been built before.
     * @param psiFilter the filter to apply while detecting the changes in the [psiFile].
     * @param scope [CoroutineScope] to run the execution of the initialization of this ProjectBuildStatusManager.
     */
    fun create(parentDisposable: Disposable,
               psiFile: PsiFile,
               psiFilter: PsiFileSnapshotFilter = NopPsiFileSnapshotFilter,
               scope: CoroutineScope = AndroidCoroutineScope(parentDisposable)): ProjectBuildStatusManager =
      ProjectBuildStatusManagerImpl(parentDisposable, psiFile, psiFilter, scope)
  }
}

private class ProjectBuildStatusManagerImpl(parentDisposable: Disposable,
                                            psiFile: PsiFile,
                                            private val psiFilter: PsiFileSnapshotFilter = NopPsiFileSnapshotFilter,
                                            scope: CoroutineScope = AndroidCoroutineScope(parentDisposable)) : ProjectBuildStatusManager {
  private val editorFile: SmartPsiElementPointer<PsiFile> = runReadAction {
    SmartPointerManager.getInstance(psiFile.project).createSmartPsiElementPointer(psiFile)
  }
  private val project: Project = editorFile.project
  private val fileChangeDetector =
    // If Live Literals is disabled or Live Edit is enabled, disable the PsiFileChangeDetector since
    // we are not looking for literal changes anymore.
    if (LiveLiteralsApplicationConfiguration.getInstance().isEnabled)
      PsiFileChangeDetector.getInstance { psiFilter.accepts(it) }
    else
      NopPsiFileChangeDetector
  private val _isBuilding = AtomicBoolean(false)
  private var projectBuildStatus: ProjectBuildStatus = ProjectBuildStatus.NotReady
    set(value) {
      if (field != value) {
        LOG.debug("Status change old = $field, new = $value")
        field = value
      }
    }
  private var lastFilterModificationCount = psiFilter.modificationCount
  override val status: ProjectStatus
    get() {
      if (psiFilter.modificationCount != lastFilterModificationCount) {
        lastFilterModificationCount = psiFilter.modificationCount

        if (projectBuildStatus !is ProjectBuildStatus.NotReady) {
          fileChangeDetector.forceMarkFileAsUpToDate(editorFile.element)
        }
      }
      return when {
        projectBuildStatus == ProjectBuildStatus.NotReady -> ProjectStatus.NotReady
        projectBuildStatus == ProjectBuildStatus.NeedsBuild -> ProjectStatus.NeedsBuild
        isBuildOutOfDate() -> ProjectStatus.OutOfDate
        else -> ProjectStatus.Ready
      }
    }

  override val isBuilding: Boolean get() = _isBuilding.get()

  init {
    Disposer.register(parentDisposable) {
      fileChangeDetector.clearMarks(editorFile.element)
    }
    ProjectSystemService.getInstance(project).projectSystem.getBuildManager()
      .addBuildListener(parentDisposable,
                        object : ProjectSystemBuildManager.BuildListener {
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
                              fileChangeDetector.markFileAsUpToDate(editorFile.element)
                              return
                            }
                            projectBuildStatus = if (result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS) {
                              fileChangeDetector.markFileAsUpToDate(editorFile.element)
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

    project.runWhenSmartAndSyncedOnEdt(parentDisposable, {
      fileChangeDetector.markFileAsUpToDate(editorFile.element)

      if (projectBuildStatus === ProjectBuildStatus.NotReady) {
        // Check in the background the state of the build (hasBeenBuiltSuccessfully is a slow method).
        scope.launch {
          val newState = if (hasExistingClassFile(editorFile.element)) ProjectBuildStatus.Built else ProjectBuildStatus.NeedsBuild
          // Only update the status if we are still in NotReady.
          if (projectBuildStatus === ProjectBuildStatus.NotReady) {
            // Set the initial state of the project and initialize the modification count.
            projectBuildStatus = newState
          }
        }
      }
    })
  }

  private fun isBuildOutOfDate() = fileChangeDetector.hasFileChanged(editorFile.element)
}