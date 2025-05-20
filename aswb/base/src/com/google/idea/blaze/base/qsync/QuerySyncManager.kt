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
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator
import com.google.idea.blaze.base.bazel.BuildSystemProvider
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.scopes.SyncActionScopes.runTaskInSyncRootScope
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap
import com.google.idea.blaze.base.util.SaveUtil
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
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
  fun getLoadedProject(): Optional<ReadonlyQuerySyncProject> = Optional.ofNullable(loadedProject)

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

  interface QuerySyncOperation {
    val title: String
    val subTitle: String
    val operationType: OperationType

    @Throws(BuildException::class)
    fun execute(context: BlazeContext)
  }

  fun interface ThrowingScopedOperation {
    @Throws(BuildException::class)
    fun execute(context: BlazeContext)
  }

  val projectModificationTracker: ModificationTracker
    get() = loader.projectModificationTracker

  @CanIgnoreReturnValue
  fun reloadProject(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    return runOperation(
      querySyncActionStats,
      taskOrigin,
      reloadProjectOperation()
    )
  }

  private fun reloadProjectOperation(): QuerySyncOperation =
    operation(
      title = "Loading project",
      subTitle = "Re-loading project",
      operationType = OperationType.SYNC
    ) { context ->
      val result= reloadProjectIfDefinitionHasChanged(loadedProject, context)
      result.project.sync(context, result.existingPostQuerySyncData)
    }

  private class ReloadProjectResult(val project: QuerySyncProject, val existingPostQuerySyncData: PostQuerySyncData?)

  @Throws(BuildException::class)
  private fun reloadProjectIfDefinitionHasChanged(project: QuerySyncProject?, context: BlazeContext): ReloadProjectResult {
    val loadedProject = loadedProjectUnlessDefinitionHasChanged(context)
                      ?: runCatching { loader.loadProject() }.getOrElse { throw BuildException("Failed to load project", it) }
    val existingQueryData = project?.currentSnapshot?.getOrNull()?.queryData()
                            ?: runCatching { loadedProject.readSnapshotFromDisk(context) }
                              .getOrElse {
                                context.output(PrintOutput("Failed to read snapshot from disk. Error: ${it.message}"))
                                logger.error("Failed to read snapshot from disk", it)
                                null
                              }
    this.loadedProject = loadedProject
    return ReloadProjectResult(project = loadedProject, existingPostQuerySyncData = existingQueryData)
  }

  val currentSnapshot: Optional<QuerySyncProjectSnapshot>
    get() = getLoadedProject().flatMap { it.currentSnapshot }

  private fun assertProjectLoaded() = checkNotNull(loadedProject) { "Project not loaded yet" }

  val renderJarArtifactTracker: RenderJarArtifactTracker
    get() = assertProjectLoaded().renderJarArtifactTracker

  val sourceToTargetMap: SourceToTargetMap
    get() = assertProjectLoaded().sourceToTargetMap

  @CanIgnoreReturnValue
  fun onStartup(querySyncActionStats: QuerySyncActionStatsScope): ListenableFuture<Boolean> {
    return runOperation(
      querySyncActionStats,
      TaskOrigin.STARTUP,
      startupOperation()
    )
  }

  private fun startupOperation(): QuerySyncOperation =
    operation(
      title = "Loading project",
      subTitle = "Initializing project structure",
      operationType = OperationType.SYNC
    ) { context ->
      val result= reloadProjectIfDefinitionHasChanged(project = null, context)
      result.project.sync(context, result.existingPostQuerySyncData)
    }

  @CanIgnoreReturnValue
  fun fullSync(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    return runOperation(
      querySyncActionStats,
      taskOrigin,
      fullSyncOperation()
    )
  }

  private fun fullSyncOperation(): QuerySyncOperation =
    operation(
      title = "Updating project structure",
      subTitle = "Re-importing project",
      operationType = OperationType.SYNC
    ) { context ->
      val result= reloadProjectIfDefinitionHasChanged(loadedProject, context)
      result.project.sync(context, lastQuery = null)
    }

  @CanIgnoreReturnValue
  fun deltaSync(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    return runOperation(
      querySyncActionStats,
      taskOrigin,
      deltaSyncOperation()
    )
  }

  private fun deltaSyncOperation(): QuerySyncOperation =
    operation(
      title = "Updating project structure",
      subTitle = "Refreshing project",
      operationType = OperationType.SYNC
    ) { context ->
      val result= reloadProjectIfDefinitionHasChanged(loadedProject, context)
      result.project.sync(context, result.existingPostQuerySyncData)
    }

  fun syncQueryDataIfNeeded(
    workspaceRelativePaths: Collection<Path>,
    querySyncActionStats: QuerySyncActionStatsScope,
    taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    assertProjectLoaded()
    return runOperation(
      querySyncActionStats, taskOrigin,
      syncQueryDataIfNeededOperation(workspaceRelativePaths)
    )
  }

  private fun syncQueryDataIfNeededOperation(workspaceRelativePaths: Collection<Path>): QuerySyncOperation =
    operation(
      title = "Updating build structure",
      subTitle = "Refreshing build structure",
      operationType = OperationType.SYNC
    ) { context ->
      if (fileListener.hasModifiedBuildFiles() ||
          getTargetsToBuildByPaths(workspaceRelativePaths).any { it.requiresQueryDataRefresh() }) {
        val result = reloadProjectIfDefinitionHasChanged(loadedProject, context)
        result.project.syncQueryData(context, result.existingPostQuerySyncData)
      }
    }

  fun runOperation(
    statsScope: QuerySyncActionStatsScope?,
    taskOrigin: TaskOrigin,
    operation: QuerySyncOperation,
  ): ListenableFuture<Boolean> {
    val result = SettableFuture.create<Boolean>()
    syncStatus.operationStarted(operation.operationType)
    Futures.addCallback(
      result,
      object : FutureCallback<Boolean> {
        override fun onSuccess(success: Boolean) {
          if (success) {
            syncStatus.operationEnded()
          }
          else {
            syncStatus.operationFailed()
          }
        }

        override fun onFailure(throwable: Throwable) {
          if (result.isCancelled) {
            syncStatus.operationCancelled()
          }
          else {
            syncStatus.operationFailed()
            logger.error("Sync failed", throwable)
          }
        }
      },
      MoreExecutors.directExecutor()
    )
    try {
      statsScope?.builder?.setTaskOrigin(taskOrigin)
      val innerResultFuture =
        ProgressiveTaskWithProgressIndicator.builder(project, operation.title)
          .submitTaskWithResult { indicator ->
            runTaskInSyncRootScope(
              project,
              operation.title,
              operation.subTitle,
              Optional.ofNullable(statsScope),
              taskOrigin,
              indicator,
              BlazeUserSettings.getInstance()
            ) { context ->
              operation.execute(context)
            }
          }

      result.setFuture(innerResultFuture)
    }
    catch (t: Throwable) {
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
    targets: Set<Label>, querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    assertProjectLoaded()
    if (targets.isEmpty()) {
      return Futures.immediateFuture(true)
    }
    return runOperation(
      querySyncActionStats,
      taskOrigin,
      enableAnalysisOperation(targets)
    )
  }

  private fun enableAnalysisOperation(targets: Set<Label>): QuerySyncOperation =
    operation(
      title = "Building dependencies",
      subTitle = "Building...",
      operationType = OperationType.BUILD_DEPS
    ) { context ->
      assertProjectLoaded().enableAnalysis(context, targets)
    }

  @CanIgnoreReturnValue
  fun enableAnalysisForReverseDeps(
    targets: Set<Label>, querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    assertProjectLoaded()
    if (targets.isEmpty()) {
      return Futures.immediateFuture(true)
    }
    return runOperation(
      querySyncActionStats,
      taskOrigin,
      enableAnalysisForReverseDependenciesOperation(targets)
    )
  }

  private fun enableAnalysisForReverseDependenciesOperation(targets: Set<Label>): QuerySyncOperation =
    operation(
      title = "Building dependencies for affected targets",
      subTitle = "Building...",
      operationType = OperationType.BUILD_DEPS
    ) { context ->
      val loadedProject = assertProjectLoaded()
      loadedProject.enableAnalysis(context, loadedProject.getTargetsDependingOn(targets))
    }

  @CanIgnoreReturnValue
  fun enableAnalysisForWholeProject(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    assertProjectLoaded()
    return runOperation(
      querySyncActionStats,
      taskOrigin,
      enableAnalysisForWholeProjectOperation()
    )
  }

  private fun enableAnalysisForWholeProjectOperation(): QuerySyncOperation =
    operation(
      title = "Enabling analysis for all project targets",
      subTitle = "Building dependencies",
      operationType = OperationType.BUILD_DEPS
    ) { context ->
      assertProjectLoaded().enableAnalysis(context)
    }

  @CanIgnoreReturnValue
  fun clearAllDependencies(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    return runOperation(
      querySyncActionStats,
      taskOrigin,
      clearAllDependenciesOperation()
    )
  }

  private fun clearAllDependenciesOperation(): QuerySyncOperation =
    operation(
      title = "Clearing dependencies",
      subTitle = "Removing all built dependencies",
      operationType = OperationType.OTHER
    ) { context ->
      assertProjectLoaded().cleanDependencies(context)
    }

  @CanIgnoreReturnValue
  fun resetQuerySyncState(
    querySyncActionStats: QuerySyncActionStatsScope, taskOrigin: TaskOrigin,
  ): ListenableFuture<Boolean> {
    return runOperation(
      querySyncActionStats,
      taskOrigin,
      resetQuerySyncOperation()
    )
  }

  private fun resetQuerySyncOperation(): QuerySyncOperation =
    operation(
      title = "Resetting query sync",
      subTitle = "Clearing artifacts and running full query",
      operationType = OperationType.OTHER
    ) { context ->
      assertProjectLoaded().resetQuerySyncState(context)
    }

  @Throws(BuildException::class)
  fun invalidateQuerySyncState(context: BlazeContext) {
    loadedProject?.invalidateQuerySyncState(context)
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
    runOperation(
      actionScope,
      TaskOrigin.USER_ACTION,
      purgeBuildCacheOperation()
    )
  }

  private fun purgeBuildCacheOperation(): QuerySyncOperation =
    operation(
      title = "Purging build cache",
      subTitle = "Deleting all cached build artifacts",
      operationType = OperationType.OTHER
    ) { context ->
      assertProjectLoaded().buildArtifactCache.purge()
    }

  val querySyncUrl: Optional<String>
    get() = BuildSystemProvider.getBuildSystemProvider(Blaze.getBuildSystemName(project))?.querySyncDocumentationUrl ?: Optional.empty()

  fun getDependencyTracker(): DependencyTracker? = loadedProject?.dependencyTracker

  fun buildAppInspector(context: BlazeContext, inspectorTarget: Label): List<Path> {
    return loadedProject?.buildAppInspector(context, inspectorTarget)?.toList().orEmpty()
  }

  override fun dispose() = Unit

  companion object {
    const val NOTIFICATION_GROUP: String = "QuerySyncBuild"

    @JvmStatic
    fun getInstance(project: Project): QuerySyncManager = project.getService(QuerySyncManager::class.java)

    @JvmStatic
    fun createOperation(
      title: String,
      subTitle: String,
      operationType: OperationType,
      operation: ThrowingScopedOperation,
    ): QuerySyncOperation {
      return operation(title, subTitle, operationType) { context -> operation.execute(context) }
    }

    private fun operation(
      title: String,
      subTitle: String,
      operationType: OperationType,
      operation: (context: BlazeContext) -> Unit,
    ): QuerySyncOperation {
      return object : QuerySyncOperation {
        override val title: String = title
        override val subTitle: String = subTitle
        override val operationType: OperationType = operationType

        override fun execute(context: BlazeContext) {
          operation(context)
        }
      }
    }

    private fun createProjectLoader(project: Project): ProjectLoader {
      val buildSystemName = Blaze.getBuildSystemName(project) ?: error("Cannot determine the build system")
      val buildSystemProvider = BuildSystemProvider.getBuildSystemProvider(buildSystemName)
                                ?: error("Cannot get BuildSystemProvider for $buildSystemName")
      return buildSystemProvider.createProjectLoader(project)
    }
  }
}
