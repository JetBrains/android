/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.build

import com.android.tools.compile.fast.CompilationResult
import com.android.tools.compile.fast.isSuccess
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.fastPreviewCompileFlow
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.hasExistingClassFile
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.util.projectStructure.module
import java.util.concurrent.atomic.AtomicInteger

/**
 * This represents the build status of the project without taking into account any file
 * modifications.
 */
private enum class ProjectBuildStatus {
  /** The project is indexing or not synced yet */
  NotReady,

  /** The project has not been built */
  NeedsBuild,

  /** The project is currently building */
  Building,

  /** The project is compiled and up to date */
  Built
}

/** The project status */
sealed class ProjectStatus {
  /** The project is indexing or not synced yet */
  object NotReady : ProjectStatus()

  object Building : ProjectStatus()

  /** The project needs to be built */
  object NeedsBuild : ProjectStatus()

  /**
   * The project is compiled but one or more files are out of date.
   *
   * Not all resource changes require a rebuild but we do not have an easy way for now to
   * differentiate them. For example, a color change might be flagged as "out of date" but the
   * preview should be ok dealing with that. However, adding or removing a resource will always
   * require a rebuild since the R class needs to change.
   *
   * @param areResourcesOutOfDate true if resources might be out of date.
   */
  sealed class OutOfDate
  private constructor(val areResourcesOutOfDate: Boolean) :
    ProjectStatus() {
    object Code : OutOfDate(false)
    object Resources : OutOfDate(true)
  }

  /** The project is compiled and up to date */
  object Ready : ProjectStatus()
}

private val LOG = Logger.getInstance(ProjectStatus::class.java)

/** Interface representing the current build status of the project. */
interface ProjectBuildStatusManager {
  /** True when the project is currently building. */
  val isBuilding: Boolean

  /** The current build [ProjectStatus]. */
  val statusFlow: StateFlow<ProjectStatus>

  val status: ProjectStatus
    get() = statusFlow.value

  companion object {
    /**
     * Creates a new [ProjectBuildStatusManager].
     *
     * @param parentDisposable [Disposable] to track for disposing this manager.
     * @param psiFile the file in the editor to track changes and the build status. If the project
     *   has not been built since it was open, this file is used to find if there are any existing
     *   .class files that indicate that has been built before.
     * @param dispatcher default [CoroutineDispatcher] to process the events of the [ProjectBuildStatusManager].
     * @param scope [CoroutineScope] to run the execution of the initialization of this
     *   ProjectBuildStatusManager.
     * @param onReady called once the [ProjectBuildStatus] transitions from [ProjectStatus.NotReady]
     *   to any other state or immediately if the the status is different from
     *   [ProjectStatus.NotReady]. This wil happen after the project is synced and has been indexed.
     */
    fun create(
      parentDisposable: Disposable,
      psiFile: PsiFile,
      dispatcher: CoroutineDispatcher = workerThread,
      scope: CoroutineScope = AndroidCoroutineScope(parentDisposable, dispatcher),
      hasExistingClassFileFun: suspend (PsiFile?) -> Boolean = { hasExistingClassFile(it) },
      onReady: (ProjectStatus) -> Unit = {},
    ): ProjectBuildStatusManager =
      ProjectBuildStatusManagerImpl(parentDisposable, psiFile, scope, onReady, dispatcher, hasExistingClassFileFun)
  }
}

interface ProjectBuildStatusManagerForTests {
  /** Returns the internal [ProjectSystemBuildManager.BuildListener] to be used by tests. */
  @TestOnly fun getBuildListenerForTest(): ProjectSystemBuildManager.BuildListener

  /** Returns the internal [ResourceChangeListener] to be used by tests. */
  @TestOnly fun getResourcesListenerForTest(): ResourceChangeListener
}

private class ProjectBuildStatusManagerImpl(
  parentDisposable: Disposable,
  psiFile: PsiFile,
  scope: CoroutineScope,
  private val onReady: (ProjectStatus) -> Unit,
  private val dispatcher: CoroutineDispatcher,
  private val hasExistingClassFile: suspend (PsiFile?) -> Boolean,
) : ProjectBuildStatusManager, ProjectBuildStatusManagerForTests {
  private val editorFilePtr: SmartPsiElementPointer<PsiFile> = runReadAction {
    SmartPointerManager.getInstance(psiFile.project).createSmartPsiElementPointer(psiFile)
  }

  private val editorFile: PsiFile?
    get() = runReadAction { editorFilePtr.element }

  private val project: Project = psiFile.project

  private val projectBuildStatusFlow = MutableStateFlow(ProjectBuildStatus.NotReady)
  private val areResourcesOutOfDateFlow = MutableStateFlow(false)
  override val statusFlow = MutableStateFlow<ProjectStatus>(ProjectStatus.NotReady)

  @Suppress("DEPRECATION")
  override val isBuilding: Boolean
    get() =
      ProjectSystemService.getInstance(project).projectSystem.getBuildManager().isBuilding ||
        FastPreviewManager.getInstance(project).isCompiling

  private val myPsiCodeFileUpToDateStatusRecorder = PsiCodeFileUpToDateStatusRecorder.getInstance(project)
  private val buildListener =
    object : ProjectSystemBuildManager.BuildListener {
      private val buildCounter = AtomicInteger(0)

      /**
       * Holds a list of actions that make up-to-date those files that were out-of-date when the build started.
       *
       * Actions obtained from [PsiCodeFileUpToDateStatusRecorder] are added to the list by [buildStarted] method and are removed
       * when the build completes in [buildCompleted]. If the build fails the removed actions are simply discarded without being invoked.
       */
      private val preparedMarkUpToDateActions = mutableListOf<PsiCodeFileUpToDateStatusRecorder.MarkUpToDateAction>()

      override fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {
        val buildCount = buildCounter.incrementAndGet()
        LOG.debug("buildStarted $mode, buildCount = $buildCount")
        preparedMarkUpToDateActions.add(myPsiCodeFileUpToDateStatusRecorder.prepareMarkUpToDate())
        if (mode == ProjectSystemBuildManager.BuildMode.CLEAN) {
          // For a clean build, we know the project will need a build
          projectBuildStatusFlow.value = ProjectBuildStatus.NeedsBuild
        }
        else {
          projectBuildStatusFlow.value = ProjectBuildStatus.Building
        }
      }

      override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
        val buildCount = buildCounter.getAndUpdate { if (it > 0) it - 1 else 0 }
        LOG.debug("buildFinished $result, buildCount = $buildCount")
        if (buildCount > 1) {
          // More builds are still pending
          return
        }
        val outOfDateFilesToClear = preparedMarkUpToDateActions.toSet() // Create a copy
        preparedMarkUpToDateActions.clear()
        if (result.mode == ProjectSystemBuildManager.BuildMode.CLEAN) {
          return
        }
        val newProjectBuildStatus =
          if (result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS) {
            outOfDateFilesToClear.forEach { it.markUpToDate() }
            // Clear the resources out of date flag
            areResourcesOutOfDateFlow.value = false
            ProjectBuildStatus.Built
          } else {
            when (projectBuildStatusFlow.value) {
              // If the project was ready before, we keep it as Ready since it was just the new
              // build
              // that failed.
              ProjectBuildStatus.Built -> ProjectBuildStatus.Built
              // If the project was not ready, then it needs a build since this one failed.
              else -> ProjectBuildStatus.NeedsBuild
            }
          }

        projectBuildStatusFlow.value = newProjectBuildStatus
      }
    }

  private val resourceChangeListener = ResourceChangeListener { reason ->
    LOG.debug("ResourceNotificationManager resourceChange ${reason.joinToString()} ")
    if (reason.contains(ResourceNotificationManager.Reason.RESOURCE_EDIT) ||
        reason.contains(ResourceNotificationManager.Reason.EDIT)) {
      areResourcesOutOfDateFlow.value = true
    }
  }

  init {
    scope.launch(dispatcher) {
      combine(
        PsiCodeFileOutOfDateStatusReporter.getInstance(project).fileUpdatesFlow,
        projectBuildStatusFlow,
        areResourcesOutOfDateFlow,
        fastPreviewCompileFlow(project, parentDisposable)
      ) { outOfDateFiles, currentProjectBuildStatus, areResourcesOutOfDate, isFastPreviewCompiling ->
        val isCodeOutOfDate = outOfDateFiles.isNotEmpty()
        when {
          currentProjectBuildStatus == ProjectBuildStatus.NotReady -> ProjectStatus.NotReady
          currentProjectBuildStatus == ProjectBuildStatus.Building || isFastPreviewCompiling -> ProjectStatus.Building
          currentProjectBuildStatus == ProjectBuildStatus.NeedsBuild -> ProjectStatus.NeedsBuild
          areResourcesOutOfDate -> ProjectStatus.OutOfDate.Resources
          isCodeOutOfDate -> ProjectStatus.OutOfDate.Code
          else -> ProjectStatus.Ready
        }
      }
        .distinctUntilChanged()
        .collect {
          LOG.debug("New status $it ${this@ProjectBuildStatusManagerImpl} ")
          statusFlow.value = it
        }
    }

    LOG.debug("setup build listener")
    ProjectSystemService.getInstance(project)
      .projectSystem
      .getBuildManager()
      .addBuildListener(parentDisposable, buildListener)
    FastPreviewManager.getInstance(project)
      .addListener(
        parentDisposable,
        object : FastPreviewManager.Companion.FastPreviewManagerListener {
          override fun onCompilationStarted(files: Collection<PsiFile>) {}

          override fun onCompilationComplete(
            result: CompilationResult,
            files: Collection<PsiFile>
          ) {
            if (result.isSuccess) myPsiCodeFileUpToDateStatusRecorder.markAsUpToDate(files)
          }
        }
      )

    // Register listener
    LOG.debug("setup notification change listener")
    runReadAction { psiFile.module?.androidFacet }
      ?.let { facet ->
        val resourceNotificationManager = ResourceNotificationManager.getInstance(project)
        val isDisposerRegistered =
          Disposer.tryRegister(parentDisposable) {
            LOG.debug("ResourceNotificationManager.removeListener")
            resourceNotificationManager.removeListener(resourceChangeListener, facet, null, null)
          }
        if (isDisposerRegistered) {
          ResourceNotificationManager.getInstance(project)
            .addListener(resourceChangeListener, facet, null, null)
          LOG.debug("ResourceNotificationManager.addListener")
        }
      }

    LOG.debug("waiting for smart and synced")
    project.runWhenSmartAndSyncedOnEdt(
      parentDisposable,
      {
        scope.launch {
          if (projectBuildStatusFlow.value === ProjectBuildStatus.NotReady) {
            // Check in the background the state of the build (hasBeenBuiltSuccessfully is a slow
            // method).
            val newState =
              if (hasExistingClassFile(editorFile)) ProjectBuildStatus.Built
              else ProjectBuildStatus.NeedsBuild
            // Only update the status if we are still in NotReady.
            if (projectBuildStatusFlow.value === ProjectBuildStatus.NotReady) {
              projectBuildStatusFlow.value = newState
            }
          }

          // Once the initial state has been set, call the onReady callback
          onReady(statusFlow.value)
        }
      }
    )
  }

  @TestOnly
  override fun getBuildListenerForTest(): ProjectSystemBuildManager.BuildListener = buildListener

  override fun getResourcesListenerForTest(): ResourceChangeListener = resourceChangeListener
}
