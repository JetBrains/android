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

import com.android.annotations.concurrency.UiThread
import com.android.tools.compile.fast.CompilationResult
import com.android.tools.compile.fast.isSuccess
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.fastPreviewCompileFlow
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.hasExistingClassFile
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener.BuildMode
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.Companion.getBuildSystemFilePreviewServices
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.SlowOperations
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.util.projectStructure.module

/**
 * This represents the build status of the project artifacts used to render previews without taking into account any file
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

/** The status of the project artifacts used to render previews*/
sealed class RenderingBuildStatus {
  /** The project is indexing or not synced yet */
  object NotReady : RenderingBuildStatus()

  object Building : RenderingBuildStatus()

  /** The project needs to be built */
  object NeedsBuild : RenderingBuildStatus()

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
    RenderingBuildStatus() {
    object Code : OutOfDate(false)
    object Resources : OutOfDate(true)
  }

  /** The project is compiled and up to date */
  object Ready : RenderingBuildStatus()
}

private val LOG = Logger.getInstance(RenderingBuildStatus::class.java)

/** Interface representing the current build status of the project. */
interface RenderingBuildStatusManager {
  /** True when the project is currently building. */
  val isBuilding: Boolean

  /** The current build [RenderingBuildStatus]. */
  val statusFlow: StateFlow<RenderingBuildStatus>

  val status: RenderingBuildStatus
    get() = statusFlow.value

  companion object {
    /**
     * Creates a new [RenderingBuildStatusManager].
     *
     * @param parentDisposable [Disposable] to track for disposing this manager.
     * @param psiFile the file in the editor to track changes and the build status. If the project
     *   has not been built since it was open, this file is used to find if there are any existing
     *   .class files that indicate that has been built before.
     * @param dispatcher default [CoroutineDispatcher] to process the events of the [RenderingBuildStatusManager].
     * @param scope [CoroutineScope] to run the execution of the initialization of this
     *   ProjectBuildStatusManager.
     * @param onReady called once the [ProjectBuildStatus] transitions from [RenderingBuildStatus.NotReady]
     *   to any other state or immediately if the the status is different from
     *   [RenderingBuildStatus.NotReady]. This wil happen after the project is synced and has been indexed.
     */
    fun create(
      parentDisposable: Disposable,
      psiFile: PsiFile,
      dispatcher: CoroutineDispatcher = workerThread,
      scope: CoroutineScope = AndroidCoroutineScope(parentDisposable, dispatcher),
      onReady: (RenderingBuildStatus) -> Unit = {},
    ): RenderingBuildStatusManager =
      RenderingBuildStatusManagerImpl(parentDisposable, psiFile, scope, onReady, dispatcher)
  }
}

interface RenderingBuildStatusManagerForTests {
  /** Returns the internal [ResourceChangeListener] to be used by tests. */
  @TestOnly
  fun getResourcesListenerForTest(): ResourceChangeListener
}

private class RenderingBuildStatusManagerImpl(
  parentDisposable: Disposable,
  psiFile: PsiFile,
  scope: CoroutineScope,
  private val onReady: (RenderingBuildStatus) -> Unit,
  private val dispatcher: CoroutineDispatcher,
) : RenderingBuildStatusManager, RenderingBuildStatusManagerForTests {
  private val editorFilePtr: SmartPsiElementPointer<PsiFile> = runReadAction {
    SmartPointerManager.getInstance(psiFile.project).createSmartPsiElementPointer(psiFile)
  }

  private val editorFile: PsiFile?
    get() = runReadAction { editorFilePtr.element }

  private val project: Project = psiFile.project
  private val buildSystemFilePreviewServices: BuildSystemFilePreviewServices<*, *> = project
    .getProjectSystem()
    .getBuildSystemFilePreviewServices()


  private val projectBuildStatusFlow = MutableStateFlow(ProjectBuildStatus.NotReady)
  private val areResourcesOutOfDateFlow = MutableStateFlow(false)
  override val statusFlow = MutableStateFlow<RenderingBuildStatus>(RenderingBuildStatus.NotReady)

  override val isBuilding: Boolean
    get() =
      ProjectSystemService.getInstance(project).projectSystem.getBuildManager().isBuilding ||
        FastPreviewManager.getInstance(project).isCompiling

  private val myPsiCodeFileUpToDateStatusRecorder = PsiCodeFileUpToDateStatusRecorder.getInstance(project)
  private val buildListener =
    object : BuildListener {
      @UiThread
      override fun buildStarted(
        buildMode: BuildMode,
        buildResult: ListenableFuture<BuildListener.BuildResult>
      ) {
        val preparedMarkUpToDateAction = myPsiCodeFileUpToDateStatusRecorder.prepareMarkUpToDate()

        projectBuildStatusFlow.value = when (buildMode) {
          // For a clean build, we know the project will need a build
          BuildMode.CLEAN -> ProjectBuildStatus.NeedsBuild
          BuildMode.COMPILE -> {
            val previousState = projectBuildStatusFlow.value
            fun handleFailure(): ProjectBuildStatus = when (previousState) {
              // If the project was ready before, we keep it as Ready since it was just the new
              // build
              // that failed.
              ProjectBuildStatus.Built -> ProjectBuildStatus.Built
              // If the project was not ready, then it needs a build since this one failed.
              else -> ProjectBuildStatus.NeedsBuild
            }


            fun handleSuccess(scope: GlobalSearchScope): ProjectBuildStatus {
              SlowOperations.allowSlowOperations(ThrowableComputable {
                preparedMarkUpToDateAction.markUpToDate(scope)
              })
              // Clear the resources out of date flag
              areResourcesOutOfDateFlow.value = false
              return ProjectBuildStatus.Built
            }

            fun handleBuildResult(result: BuildListener.BuildResult): ProjectBuildStatus =
              when (result.status) {
                BuildStatus.SUCCESS -> handleSuccess(result.scope)
                BuildStatus.FAILED -> handleFailure()
                BuildStatus.UNKNOWN -> previousState
                BuildStatus.CANCELLED -> previousState
              }

            scope.launch {
              val result = runCatching { buildResult.await() }
                .getOrElse { BuildListener.BuildResult(BuildStatus.FAILED, EverythingGlobalScope()) }
              withContext(AndroidDispatchers.uiThread) {
                projectBuildStatusFlow.value = handleBuildResult(result)
              }
            }
            ProjectBuildStatus.Building
          }
        }
      }
    }

  private val resourceChangeListener = ResourceChangeListener { reason ->
    LOG.debug("ResourceNotificationManager resourceChange ${reason.joinToString()} ")
    if (reason.contains(ResourceNotificationManager.Reason.RESOURCE_EDIT) ||
      reason.contains(ResourceNotificationManager.Reason.EDIT)
    ) {
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
          currentProjectBuildStatus == ProjectBuildStatus.NotReady -> RenderingBuildStatus.NotReady
          currentProjectBuildStatus == ProjectBuildStatus.Building || isFastPreviewCompiling -> RenderingBuildStatus.Building
          currentProjectBuildStatus == ProjectBuildStatus.NeedsBuild -> RenderingBuildStatus.NeedsBuild
          areResourcesOutOfDate -> RenderingBuildStatus.OutOfDate.Resources
          isCodeOutOfDate -> RenderingBuildStatus.OutOfDate.Code
          else -> RenderingBuildStatus.Ready
        }
      }
        .distinctUntilChanged()
        .collect {
          LOG.debug("New status $it ${this@RenderingBuildStatusManagerImpl} ")
          statusFlow.value = it
        }
    }

    LOG.debug("setup build listener")
    buildSystemFilePreviewServices.subscribeBuildListener(project, parentDisposable, buildListener)

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

  override fun getResourcesListenerForTest(): ResourceChangeListener = resourceChangeListener
}
