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
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.io.ByteSource
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator
import com.google.idea.blaze.base.bazel.BuildSystemProvider
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.section.sections.EnableCodeAnalysisOnSyncSection
import com.google.idea.blaze.base.qsync.ProjectStatsLogger.logSyncStats
import com.google.idea.blaze.base.qsync.artifacts.ProjectArtifactStore
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope
import com.google.idea.blaze.base.scope.scopes.ToolWindowScopeRunner.runTaskWithToolWindow
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.google.idea.blaze.base.sync.SyncListener
import com.google.idea.blaze.base.sync.SyncMode
import com.google.idea.blaze.base.sync.SyncResult
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap
import com.google.idea.blaze.base.util.SaveUtil
import com.google.idea.blaze.common.AtomicFileWriter
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.artifact.BuildArtifactCache
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.SnapshotDeserializer
import com.google.idea.blaze.qsync.project.SnapshotSerializer
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.google.protobuf.CodedOutputStream
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.serviceContainer.NonInjectable
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.Objects
import java.util.Optional
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.Volatile
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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

  val ideProject: Project get() = project
  private val logger = thisLogger()

  @Volatile
  private var loadedProject: QuerySyncProject? = null
  fun getLoadedProject(): Optional<ReadonlyQuerySyncProject> = Optional.ofNullable(loadedProject)
  private var lastQueryInstant: Instant = Instant.DISTANT_PAST

  private var lastProjectUpdateFromSnapshot: QuerySyncProjectSnapshot = QuerySyncProjectSnapshot.EMPTY
  private var lastProjectUpdateFromArtifactState: ArtifactTracker.State = ArtifactTracker.State.EMPTY

  private val syncStatus: QuerySyncStatus = QuerySyncStatus(project)
  val fileListener: QuerySyncAsyncFileListener = QuerySyncAsyncFileListener.createAndListen(project, this)
  private val cacheCleaner: CacheCleaner = CacheCleaner(project)

  @VisibleForTesting
  val artifactStore: ProjectArtifactStore = ProjectArtifactStore(
    Path.of(project.basePath!!),
    project.service<BuildArtifactCache>(),
    FileRefresher(project)
  )

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
    fun execute(context: BlazeContext, querySyncManager: QuerySyncManager)
  }

  fun interface ThrowingScopedOperation {
    @Throws(BuildException::class)
    fun execute(context: BlazeContext)
  }

  private val projectModificationTracker_ = SimpleModificationTracker()
  val projectModificationTracker: ModificationTracker = projectModificationTracker_
  val snapshotHolder: SnapshotHolder = SnapshotHolder()
    .also { snapshotHolder ->
      snapshotHolder.addListener({ _, _, _ -> projectModificationTracker_.incModificationCount() })
      QuerySyncProjectListenerProvider.createListenersFor(this).forEach { snapshotHolder.addListener(it) }
    }

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
      val result= reloadProjectIfDefinitionHasChanged(context)
      syncStatsScope(context) { context ->
        syncQueryData(context, result.existingPostQuerySyncData)
      }
    }

  private class ReloadProjectResult(val project: QuerySyncProject, val existingPostQuerySyncData: PostQuerySyncData?)

  @Throws(BuildException::class)
  private fun reloadProjectIfDefinitionHasChanged(context: BlazeContext): ReloadProjectResult {
    reloadProjectDefinitionIfChanged(context)
    val loadedProject =
      loadedProject
        ?.takeUnless {
          val currentProjectViewSet = BlazeImportSettingsManager.getInstance(ideProject).projectViewSet
          it.projectViewSet != currentProjectViewSet ||
          it.projectDefinition != loader.loadProjectDefinition(currentProjectViewSet).definition
        }
      ?: runCatching { loader.loadProject() }.getOrElse { throw BuildException("Failed to load project", it) }
    val existingQueryData = currentSnapshot.getOrNull()?.queryData()
                            ?: runCatching { readSnapshotFromDisk(context) }
                              .getOrElse {
                                context.output(PrintOutput("Failed to read snapshot from disk. Error: ${it.message}"))
                                logger.error("Failed to read snapshot from disk", it)
                                null
                              }
    this.loadedProject = loadedProject
    projectModificationTracker_.incModificationCount() // Loaded project should be managed by the SnapshotHolder. For now here.
    return ReloadProjectResult(project = loadedProject, existingPostQuerySyncData = existingQueryData)
  }

  val currentSnapshot: Optional<QuerySyncProjectSnapshot>
    get() = snapshotHolder.current

  fun assertProjectLoaded() = checkNotNull(loadedProject) { "Project not loaded yet" }

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
      val result= reloadProjectIfDefinitionHasChanged(context)
      syncStatsScope(context) { context ->
        syncQueryData(context, result.existingPostQuerySyncData)
      }
      autoEnableCodeAnalysis(context)
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
      val result= reloadProjectIfDefinitionHasChanged(context)
      syncStatsScope(context) { context ->
        syncQueryData(context, postQuerySyncData = null)
      }
      autoEnableCodeAnalysis(context)
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
      val result= reloadProjectIfDefinitionHasChanged(context)
      syncStatsScope(context) { context ->
        syncQueryData(context, result.existingPostQuerySyncData)
      }
      autoEnableCodeAnalysis(context)
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
      operationType = OperationType.SYNC,
      applyProjectStructureChanges = false
    ) { context ->
      if (fileListener.hasModifiedBuildFiles() ||
          getTargetsToBuildByPaths(workspaceRelativePaths).any { it.requiresQueryDataRefresh() }) {
        val result = reloadProjectIfDefinitionHasChanged(context)
        syncQueryData(context, result.existingPostQuerySyncData)
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
            runTaskWithToolWindow(
              project,
              operation.title,
              operation.subTitle,
              taskOrigin,
              BlazeUserSettings.getInstance()
            ) { context ->
              withSyncEventsPublished(context) {
                context.push(ProgressIndicatorScope(indicator))
                context.addCancellationHandler { indicator.cancel() }
                statsScope?.let { context.push(it) }
                operation.execute(context, this)
                logSyncStats(context, loadedProject, currentSnapshot.getOrNull()) // Not logging new project stats on exception.
              }
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

  private fun <T> withSyncEventsPublished(context: BlazeContext, block: () -> T): T {
    val originalSnapshot = currentSnapshot.orElse(QuerySyncProjectSnapshot.EMPTY)
    val fileListenerSyncCompleter = fileListener.syncStarted();
    val previousQueryInstant = lastQueryInstant
    try {
      return block()
        .also {
          // On success only.
          val newSnapshot = currentSnapshot.orElse(QuerySyncProjectSnapshot.EMPTY)
          if (lastQueryInstant > previousQueryInstant) {
            // Pending changes remain if sync failed.
            fileListenerSyncCompleter.run()
          }

          val querySyncProject = loadedProject ?: return@also
          // TODO: Revisit SyncListeners once we switch fully to qsync
          if (originalSnapshot != newSnapshot) {
            // Sync that does not change anything is an no-op.
            for (syncListener in SyncListener.EP_NAME.extensions) {
              // A callback shared between the old and query sync implementations.
              syncListener.onSyncComplete(
                project,
                context,
                querySyncProject.importSettings,
                querySyncProject.projectViewSet,
                ImmutableSet.of(),
                querySyncProject.projectData,
                SyncMode.FULL,
                SyncResult.SUCCESS
              )
            }
          }
        }
    } finally {
      // TODO: Revisit SyncListeners once we switch fully to qsync
      // Note: Any sync operation is a query sync operation from the point of view or Android Studio listeners (none of which exist for a
      // valid reason). Various subsystem like Android resources refresh their caches on this event and this is the enableCodeAnalysis event
      // that actually matters to them. Unfortunately those listeners make various assumptions about what normally happens when a project is
      // opened etc. so publishing events on actual change only might not be safe.
      for (syncListener in SyncListener.EP_NAME.extensions) {
        // A query sync specific callback.
        syncListener.afterQuerySync(project, context)
      }
    }
  }

  @Throws(BuildException::class)
  fun syncStatsScope(parentContext: BlazeContext, block: (BlazeContext) -> Unit) {
    BlazeContext.create(parentContext).use { context ->
      context.push(SyncQueryStatsScope())
      block(context)
    }
  }

  private fun syncQueryData(
    context: BlazeContext,
    postQuerySyncData: PostQuerySyncData?
  ) {
    val queryInstant = Clock.System.now()
    val coreSyncResult = assertProjectLoaded().syncCore(context, postQuerySyncData)
    updateCurrentSnapshot(context) {
      it.applySyncResult(coreSyncResult)
    }
    lastQueryInstant = queryInstant
  }

  private fun autoEnableCodeAnalysis(context: BlazeContext) {
    val project = loadedProject ?: return
    if (project.projectViewSet.getScalarValue(EnableCodeAnalysisOnSyncSection.KEY).getOrDefault(false)) {
      project.buildDependencies(context, DependencyTracker.DependencyBuildRequest.wholeProject())
    }
  }

  @Throws(BuildException::class)
  fun updateProjectStructureAndSnapshot(
    context: BlazeContext
  ) {
    val newSnapshot: QuerySyncProjectSnapshot = currentSnapshot.orElse(QuerySyncProjectSnapshot.EMPTY)
    val newArtifactState = loadedProject?.artifactTracker?.stateSnapshot ?: ArtifactTracker.State.EMPTY
    if (lastProjectUpdateFromSnapshot.queryData() == newSnapshot.queryData() &&
        lastProjectUpdateFromSnapshot.graph() == newSnapshot.graph() &&
        lastProjectUpdateFromArtifactState == newArtifactState
    ) {
      context.output(PrintOutput("No changes found. Not updating the project structure."))
      return
    }
    val loadedProject = assertProjectLoaded()
    val snapshot = currentSnapshot.getOrDefault(QuerySyncProjectSnapshot.EMPTY)
    val result = loadedProject.createProjectStructure(context, snapshot.queryData(), snapshot.graph())
    val updatedSnapshot = onNewSnapshot(
      context,
      loadedProject,
      QuerySyncProjectSnapshot
        .builder()
        .artifactState(result.artifactState)
        .queryData(snapshot.queryData())
        .graph(snapshot.graph())
        .project(result.projectStructure)
        .build()
    )
    lastProjectUpdateFromArtifactState = newArtifactState
    lastProjectUpdateFromSnapshot = updatedSnapshot
  }

  @Throws(BuildException::class)
  private fun onNewSnapshot(context: BlazeContext, project: QuerySyncProject, newSnapshot: QuerySyncProjectSnapshot): QuerySyncProjectSnapshot {
    // update the artifact store for the new snapshot
    var newSnapshot = newSnapshot
    val newArtifactDirectoriesSnapshot = newSnapshot.project().artifactDirectories
    val result = artifactStore.update(newArtifactDirectoriesSnapshot, context)
    if (!result.incompleteTargets.isEmpty()) {
      val limit = 20
      logger.warn(
        "${result.incompleteTargets.size} project deps had missing artifacts:\n  ${
          result.incompleteTargets
            .take(limit)
            .joinToString("\n  ") { Objects.toString(it) }
        }"
      )
      if (result.incompleteTargets.size > limit) {
        logger.warn("  (and ${result.incompleteTargets.size - limit} more)")
      }
    }
    // update the snapshot with any missing artifacts:
    newSnapshot = newSnapshot.toBuilder().incompleteTargets(result.incompleteTargets).build()

    snapshotHolder.setCurrent(context, project, newSnapshot)
    try {
      writeToDisk(newSnapshot)
    } catch (e: IOException) {
      throw BuildException("Failed to write snapshot to disk", e)
    }
    return newSnapshot
  }

  @Throws(IOException::class)
  fun readSnapshotFromDisk(context: BlazeContext): PostQuerySyncData? {
    val f = getSnapshotFilePath(ideProject).toFile()
    if (!f.exists()) {
      return null
    }
    GZIPInputStream(FileInputStream(f)).use { `in` ->
      return SnapshotDeserializer()
        .readFrom(`in`, context)
        .getOrNull()
        ?.syncData
    }
  }

  @Throws(IOException::class)
  private fun writeToDisk(snapshot: QuerySyncProjectSnapshot) {
    AtomicFileWriter.create(getSnapshotFilePath(ideProject)).use { writer ->
      GZIPOutputStream(writer.outputStream).use { zip ->
        val message = SnapshotSerializer().visit(snapshot.queryData()).toProto()
        val codedOutput = CodedOutputStream.newInstance(zip, 1024 * 1024)
        message.writeTo(codedOutput)
        codedOutput.flush()
      }
      writer.onWriteComplete()
    }
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
      context.output(
        PrintOutput.output(
          "Building dependencies for:\n  " + Joiner.on("\n  ").join(targets)
        )
      )
      assertProjectLoaded().buildDependencies(context, DependencyTracker.DependencyBuildRequest.multiTarget(targets))
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
      context.output(
        PrintOutput.output(
          "Building reverse dependencies for:\n  " + Joiner.on("\n  ").join(targets)
        )
      )
      if (loadedProject.buildDependencies(context, DependencyTracker.DependencyBuildRequest.multiTarget(
          loadedProject.getTargetsDependingOn(targets)))) {
      }
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
      context.output(PrintOutput.output("Building project dependencies..."))
      assertProjectLoaded().buildDependencies(context, DependencyTracker.DependencyBuildRequest.wholeProject())
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
      val assertProjectLoaded = assertProjectLoaded()
      runCatching { assertProjectLoaded.artifactTracker.clear() }
        .getOrElse { throw BuildException("Failed to clear dependency info", it) }
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
      val loadedProject = assertProjectLoaded()
      loadedProject.runCatching { loadedProject.artifactTracker.clear() }
        .getOrElse<Unit, Unit> { throw BuildException("Failed to clear dependency info", it) }
      updateCurrentSnapshot(context) {
        it.queryData(PostQuerySyncData.EMPTY)
        it.graph(BuildGraphData.EMPTY)
        it.artifactState(ArtifactTracker.State.EMPTY)
        it.project(ProjectProto.Project.getDefaultInstance())
        it.incompleteTargets(ImmutableSet.of())
      }
      syncStatsScope(context) { context ->
        syncQueryData(context, postQuerySyncData = null)
      }
      autoEnableCodeAnalysis(context)
    }

  @Throws(BuildException::class)
  fun invalidateQuerySyncState(context: BlazeContext) {
    loadedProject?.let { loadedProject ->
      loadedProject.runCatching { loadedProject.artifactTracker.clear() }
        .getOrElse { throw BuildException("Failed to clear dependency info", it) }
      updateCurrentSnapshot(context) {
        it.queryData(PostQuerySyncData.EMPTY)
        it.graph(BuildGraphData.EMPTY)
      }
    }
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
  private fun reloadProjectDefinitionIfChanged(context: BlazeContext) {
    // Ensure edits to the project view and any imports have been saved
    SaveUtil.saveAllFiles()
    val projectViewManager = ProjectViewManager.getInstance(project)
    val importSettings = BlazeImportSettingsManager.getInstance(project).importSettings ?: return
    val currentProjectViewSet = projectViewManager.doLoadProjectView(
        BlazeContext.create(),  /* Load silently for comparison*/
        importSettings
      )
    if (BlazeImportSettingsManager.getInstance(project).projectViewSet != currentProjectViewSet) {
      ProjectViewManager.getInstance(project).reloadProjectView(context)
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

  fun getBugreportFiles(): Map<String, ByteSource> {
    return ImmutableMap.builder<String, ByteSource>()
      .putAll(artifactStore.getBugreportFiles())
      .build()
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
      applyProjectStructureChanges: Boolean = true,
      operation: QuerySyncManager.(context: BlazeContext) -> Unit,
    ): QuerySyncOperation {
      return object : QuerySyncOperation {
        override val title: String = title
        override val subTitle: String = subTitle
        override val operationType: OperationType = operationType

        override fun execute(context: BlazeContext, querySyncManager: QuerySyncManager) {
          with(querySyncManager) {
            operation(context)
            if (applyProjectStructureChanges) {
              updateProjectStructureAndSnapshot(context)
            }
          }
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

fun QuerySyncProjectSnapshot.update(mutator: (QuerySyncProjectSnapshot.Builder) -> Unit): QuerySyncProjectSnapshot {
  val builder = this.toBuilder()
  mutator(builder)
  return builder.build()
}

fun QuerySyncManager.updateCurrentSnapshot(context: BlazeContext, mutator: (QuerySyncProjectSnapshot.Builder) -> Unit) {
  snapshotHolder.setCurrent(context, assertProjectLoaded(), currentSnapshot.orElse(QuerySyncProjectSnapshot.EMPTY).update(mutator))
}

fun QuerySyncProjectSnapshot.Builder.applySyncResult(coreSyncResult: QuerySyncProject.CoreSyncResult) {
  queryData(coreSyncResult.postQuerySyncData)
  graph(coreSyncResult.graph)
}
