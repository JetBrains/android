/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync

import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.idea.blaze.base.bazel.BuildSystemProvider
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.scopes.SyncActionScopes
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap
import com.google.idea.blaze.base.util.SaveUtil
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.NonInjectable
import java.io.IOException
import java.nio.file.Path
import java.util.Optional
import kotlin.concurrent.Volatile
import kotlin.jvm.optionals.getOrNull

/**
 * The project component for a query based sync.
 *
 *
 * This class manages sync'ing the intelliJ project state to the state of the Bazel project in
 * the workspace, as well as building dependencies of the project.
 *
 *
 * The sync'd state of a project is represented by [QuerySyncProjectSnapshot]. During the
 * sync process, different parts of that are available at different phases:
 *
 *
 *  * [ProjectDefinition]: the input to the sync process that can be created from the
 * project configuration. This class remained unchanged throughout sync.
 *  * [PostQuerySyncData]: the state after the query invocation has been made, or after a
 * delta has been applied to that. This class is the input and output to the partial update
 * operation, and also contains the data that will be persisted to disk over an IDE restart.
 *  * [QuerySyncProjectSnapshot]: the full project state, created in the last phase of sync
 * from [PostQuerySyncData].
 *
 */
class QuerySyncManager @VisibleForTesting @NonInjectable constructor(
  private val project: Project,
  private val loader: ProjectLoader,
) : Disposable {
  constructor(project: Project) : this(project, createProjectLoader(project))

  private val logger = thisLogger()

  @Volatile
  private var loadedProject: QuerySyncProject? = null
  fun getLoadedProject(): Optional<QuerySyncProject> = Optional.ofNullable(loadedProject)

  private val syncStatus: QuerySyncStatus = QuerySyncStatus(project)
  val fileListener: QuerySyncAsyncFileListener = QuerySyncAsyncFileListener.createAndListen(project, this)
  private val cacheCleaner: CacheCleaner = CacheCleaner(project)

  /** An enum represent the origin of a task performed by the [QuerySyncManager]  */
  enum class TaskOrigin {
    /** Tasks run when opening a project  */
    STARTUP,

    /** User-initiated tasks  */
    USER_ACTION,

    /** Tasks run automatically  */
    AUTOMATIC,
    UNKNOWN
  }

  /** An enum represent the kinds of operations initiated by the sync manager  */
  enum class OperationType {
    SYNC,
    BUILD_DEPS,
    OTHER,
  }

  fun interface ThrowingScopedOperation {
    @Throws(BuildException::class)
    fun execute(context: BlazeContext)
  }

  val projectModificationTracker: ModificationTracker
    get() = loader.projectModificationTracker

  @CanIgnoreReturnValue
  fun reloadProject(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    return run(
      "Loading project",
      "Re-loading project",
      querySyncActionStats,
      { context -> loadProject(context) },
      taskOrigin,
      OperationType.SYNC
    )
  }

  @Throws(BuildException::class)
  fun loadProject(context: BlazeContext) {
    try {
      val newProject = loader.loadProject(context)
      if (!context.hasErrors()) {
        newProject ?: error("Failed to load the project")
        val projectData = newProject.readSnapshotFromDisk(context)
        loadedProject = newProject
        newProject.sync(context, projectData)
      }
    } catch (e: IOException) {
      throw BuildException("Failed to load project", e)
    }
  }

  val currentSnapshot: Optional<QuerySyncProjectSnapshot>
    get() = getLoadedProject()
      .map { it.snapshotHolder }
      .flatMap { it.current }

  private fun assertProjectLoaded() = checkNotNull(loadedProject) { "Project not loaded yet" }

  val renderJarArtifactTracker: RenderJarArtifactTracker
    get() = assertProjectLoaded().renderJarArtifactTracker

  val sourceToTargetMap: SourceToTargetMap
    get() = assertProjectLoaded().sourceToTargetMap

  @CanIgnoreReturnValue
  fun onStartup(querySyncActionStats: QuerySyncActionStatsScope): ListenableFuture<Boolean> {
    return run(
      "Loading project",
      "Initializing project structure",
      querySyncActionStats,
      ThrowingScopedOperation { context -> this.loadProject(context) },
      TaskOrigin.STARTUP,
      OperationType.SYNC
    )
  }

  @CanIgnoreReturnValue
  fun fullSync(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    return run(
      "Updating project structure",
      "Re-importing project",
      querySyncActionStats,
      ThrowingScopedOperation { context ->
        val loadedProject = loadedProjectUnlessDefinitionHasChanged(context)
        if (loadedProject == null) {
          loadProject(context)
        } else {
          loadedProject.fullSync(context)
        }
      },
      taskOrigin,
      OperationType.SYNC
    )
  }

  @CanIgnoreReturnValue
  fun deltaSync(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    return run(
      "Updating project structure",
      "Refreshing project",
      querySyncActionStats,
      ThrowingScopedOperation { context ->
        val loadedProject = loadedProjectUnlessDefinitionHasChanged(context)
        if (loadedProject == null) {
          loadProject(context)
        } else {
          loadedProject.deltaSync(context)
        }
      },
      taskOrigin,
      OperationType.SYNC
    )
  }

  fun syncQueryDataIfNeeded(
    workspaceRelativePaths: Collection<Path>,
    querySyncActionStats: QuerySyncActionStatsScope,
    taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    assertProjectLoaded()
    return run(
      "Updating build structure",
      "Refreshing build structure",
      querySyncActionStats,
      ThrowingScopedOperation { context ->
        if (fileListener.hasModifiedBuildFiles() ||
            getTargetsToBuildByPaths(workspaceRelativePaths).any { it.requiresQueryDataRefresh() }) {
          val originallyLoadedProject = loadedProject
          val loadedProject = loadedProjectUnlessDefinitionHasChanged(context) ?: let {
            val newlyLoadedProject = loader.loadProject(context)
            if (!context.hasErrors()) {
              this.loadedProject = newlyLoadedProject
            }
            newlyLoadedProject
          } ?: return@ThrowingScopedOperation // Context should have the error.
          val lastQuery = originallyLoadedProject?.snapshotHolder()?.queryData()
          loadedProject.syncQueryData(context, lastQuery)
        }
      },
      taskOrigin,
      OperationType.SYNC
    )
  }

  fun run(
    title: String,
    subTitle: String,
    querySyncActionStatsScope: QuerySyncActionStatsScope,
    operation: ThrowingScopedOperation,
    taskOrigin: TaskOrigin,
    operationType: OperationType
  ): ListenableFuture<Boolean> {
    val result = SettableFuture.create<Boolean>()
    syncStatus.operationStarted(operationType)
    Futures.addCallback(
      result,
      object : FutureCallback<Boolean> {
        override fun onSuccess(success: Boolean) {
          if (success) {
            syncStatus.operationEnded()
          } else {
            syncStatus.operationFailed()
          }
        }

        override fun onFailure(throwable: Throwable) {
          if (result.isCancelled) {
            syncStatus.operationCancelled()
          } else {
            syncStatus.operationFailed()
            logger.error("Sync failed", throwable)
          }
        }
      },
      MoreExecutors.directExecutor()
    )
    try {
      querySyncActionStatsScope.builder.setTaskOrigin(taskOrigin)
      val innerResultFuture =
        SyncActionScopes.createAndSubmitRunTask(
          project,
          title,
          subTitle,
          Optional.of(querySyncActionStatsScope),
          operation,
          taskOrigin
        )
      result.setFuture(innerResultFuture)
    } catch (t: Throwable) {
      result.setException(t)
      throw t
    }
    return result
  }

  fun getTargetsToBuildByPaths(workspaceRelativePaths: Collection<Path>): Set<TargetsToBuild> {
    // TODO(mathewi) passing an empty BlazeContext here means that messages generated by
    //   DependencyTracker.getProjectTargets are now lost. They should probably be reported via
    //   an exception, or inside TargetsToBuild, so that the UI layer can decide how to display
    //   the messages.
    return loadedProject!!.getProjectTargets(BlazeContext.create(), workspaceRelativePaths)
  }

  @CanIgnoreReturnValue
  fun enableAnalysis(
    targets: Set<Label>, querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    assertProjectLoaded()
    if (targets.isEmpty()) {
      return Futures.immediateFuture(true)
    }
    return run(
      "Building dependencies",
      "Building...",
      querySyncActionStats,
      { context -> assertProjectLoaded().enableAnalysis(context, targets) },
      taskOrigin,
      OperationType.BUILD_DEPS
    )
  }

  @CanIgnoreReturnValue
  fun enableAnalysisForReverseDeps(
    targets: Set<Label>, querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    assertProjectLoaded()
    if (targets.isEmpty()) {
      return Futures.immediateFuture(true)
    }
    return run(
      "Building dependencies for affected targets",
      "Building...",
      querySyncActionStats,
      { context ->
        val loadedProject = assertProjectLoaded()
        loadedProject.enableAnalysis(context, loadedProject.getTargetsDependingOn(targets))
      },
      taskOrigin,
      OperationType.BUILD_DEPS
    )
  }

  @CanIgnoreReturnValue
  fun enableAnalysisForWholeProject(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    assertProjectLoaded()
    return run(
      "Enabling analysis for all project targets",
      "Building dependencies",
      querySyncActionStats,
      { context -> assertProjectLoaded().enableAnalysis(context) },
      taskOrigin,
      OperationType.BUILD_DEPS
    )
  }

  @CanIgnoreReturnValue
  fun clearAllDependencies(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    return run(
      "Clearing dependencies",
      "Removing all built dependencies",
      querySyncActionStats,
      { context -> assertProjectLoaded().cleanDependencies(context) },
      taskOrigin,
      OperationType.OTHER
    )
  }

  @CanIgnoreReturnValue
  fun resetQuerySyncState(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin
  ): ListenableFuture<Boolean> {
    return run(
      "Resetting query sync",
      "Clearing artifacts and running full query",
      querySyncActionStats,
      { context -> assertProjectLoaded().resetQuerySyncState(context) },
      taskOrigin,
      OperationType.OTHER
    )
  }

  fun canEnableAnalysisFor(workspaceRelativePath: Path): Boolean {
    return loadedProject?.canEnableAnalysisFor(workspaceRelativePath) ?: false
  }

  fun operationInProgress(): Boolean = syncStatus.operationInProgress()
  fun currentOperation(): Optional<OperationType> = syncStatus.currentOperation()

  fun isProjectFileAddedSinceSync(absolutePath: Path): Optional<Boolean> {
    return loadedProject?.projectFileAddedSinceSync(absolutePath) ?: Optional.empty()
  }

  fun isReadyForAnalysis(psiFile: PsiFile): Boolean {
    val virtualFile = psiFile.virtualFile ?: return true
    val nioPath = virtualFile.fileSystem.getNioPath(virtualFile) ?: return true
    return CachedValuesManager.getCachedValue(psiFile) {
      val result = loadedProject?.isReadyForAnalysis(nioPath) ?: false
      CachedValueProvider.Result.create(result, projectModificationTracker)
    }
  }

  /**
   * Loads the [ProjectViewSet] and checks if the [ProjectDefinition] for the project
   * has changed.
   *
   * @return true if the [ProjectDefinition] has changed.
   */
  @Throws(BuildException::class)
  private fun loadedProjectUnlessDefinitionHasChanged(context: BlazeContext): QuerySyncProject? {
    val loadedProject = loadedProject ?: return null
    // Ensure edits to the project view and any imports have been saved
    SaveUtil.saveAllFiles()
    val projectViewManager = ProjectViewManager.getInstance(project)
    val importSettings = BlazeImportSettingsManager.getInstance(project).importSettings ?: return null
    val projectToLoadDefinition = loader.loadProjectDefinition(
      projectViewManager.doLoadProjectView(
        BlazeContext.create(),  /* Load silently for comparison*/
        importSettings
      )
    )
    val projectDefinition = projectToLoadDefinition.definition
    return loadedProject.takeUnless {
      loadedProject.projectDefinition != projectDefinition ||
      importSettings.workspaceRoot != projectToLoadDefinition.workspaceRoot.directory().toString()
    }
  }

  /** Displays error notification popup balloon in IDE.  */
  fun notifyError(title: String, content: String) = notifyInternal(title, content, NotificationType.ERROR)

  /** Displays warning notification popup balloon in IDE.  */
  fun notifyWarning(title: String, content: String) = notifyInternal(title, content, NotificationType.WARNING)

  private fun notifyInternal(title: String, content: String, notificationType: NotificationType) {
    Notifications.Bus.notify(Notification(NOTIFICATION_GROUP, title, content, notificationType), project)
  }

  fun cleanCacheNow() = cacheCleaner.cleanNow()

  fun purgeBuildCache(actionScope: QuerySyncActionStatsScope) {
    run(
      "Purging build cache",
      "Deleting all cached build artifacts",
      actionScope,
      ThrowingScopedOperation { c -> assertProjectLoaded().buildArtifactCache.purge() },
      TaskOrigin.USER_ACTION,
      OperationType.OTHER
    )
  }

  val querySyncUrl: Optional<String>
    get() = BuildSystemProvider.getBuildSystemProvider(Blaze.getBuildSystemName(project))?.querySyncDocumentationUrl ?: Optional.empty()

  override fun dispose() = Unit

  companion object {
    const val NOTIFICATION_GROUP: String = "QuerySyncBuild"

    @JvmStatic
    fun getInstance(project: Project): QuerySyncManager = project.getService(QuerySyncManager::class.java)

    private fun createProjectLoader(project: Project): ProjectLoader {
      val buildSystemName = Blaze.getBuildSystemName(project) ?: error("Cannot determine the build system")
      val buildSystemProvider = BuildSystemProvider.getBuildSystemProvider(buildSystemName)
                                ?: error("Cannot get BuildSystemProvider for $buildSystemName")
      return buildSystemProvider.createProjectLoader(project)
    }
  }
}
