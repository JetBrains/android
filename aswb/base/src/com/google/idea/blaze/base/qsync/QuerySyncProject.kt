/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.io.ByteSource
import com.google.common.io.MoreFiles
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.qsync.artifacts.ProjectArtifactStore
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap
import com.google.idea.blaze.base.util.SaveUtil
import com.google.idea.blaze.common.AtomicFileWriter
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.artifact.BuildArtifactCache
import com.google.idea.blaze.common.vcs.VcsState
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.BlazeQueryParser
import com.google.idea.blaze.qsync.ProjectProtoTransform
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot
import com.google.idea.blaze.qsync.SnapshotBuilder
import com.google.idea.blaze.qsync.SnapshotHolder
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.SnapshotDeserializer
import com.google.idea.blaze.qsync.project.SnapshotSerializer
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.google.protobuf.CodedOutputStream
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.Objects
import java.util.Optional
import java.util.function.Supplier
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.jvm.optionals.getOrNull

/**
 * Encapsulates a loaded query sync project and it's dependencies  for readonly consumers.
 *
 *
 * This class also maintains a [QuerySyncProjectData] instance whose job is to expose
 * project state to the rest of the plugin and IDE.
 */
interface ReadonlyQuerySyncProject {
  val buildSystem: BuildSystem
  val projectDefinition: ProjectDefinition
  val workspaceRoot: WorkspaceRoot
  val currentSnapshot: Optional<QuerySyncProjectSnapshot>
  val projectData: QuerySyncProjectData
  fun getWorkingSet(create: BlazeContext): Set<Path>
  fun dependsOnAnyOf_DO_NOT_USE_BROKEN(target: Label, deps: Set<Label>): Boolean
  fun containsPath(absolutePath: Path): Boolean
  fun explicitlyExcludesPath(absolutePath: Path): Boolean
  fun getBugreportFiles(): Map<String, ByteSource>
}

/**
 * Encapsulates a loaded querysync project and it's dependencies.
 *
 *
 * This class also maintains a [QuerySyncProjectData] instance whose job is to expose
 * project state to the rest of the plugin and IDE.
 */
class QuerySyncProject(
  val ideProject: Project,
  private val snapshotFilePath: Path,
  val snapshotHolder: SnapshotHolder,
  val importSettings: BlazeImportSettings,
  override val workspaceRoot: WorkspaceRoot,
  val artifactTracker: ArtifactTracker<*>,
  val buildArtifactCache: BuildArtifactCache,
  val artifactStore: ProjectArtifactStore,
  val renderJarArtifactTracker: RenderJarArtifactTracker, // TODO: delete
  val dependencyTracker: DependencyTracker,
  private val appInspectorTracker: AppInspectorTracker,
  private val projectQuerier: ProjectQuerier,
  private val snapshotBuilder: SnapshotBuilder,
  override val projectDefinition: ProjectDefinition,
  val projectViewSet: ProjectViewSet,
// TODO(mathewi) only one of these two should strictly be necessary:
  val workspacePathResolver: WorkspacePathResolver,
  val projectPathResolver: ProjectPath.Resolver,
  val workspaceLanguageSettings: WorkspaceLanguageSettings,
  val sourceToTargetMap: QuerySyncSourceToTargetMap,
  override val buildSystem: BuildSystem,
  val projectProtoTransforms: ProjectProtoTransform.Registry,
  val handledRuleKinds: Set<String>
) : ReadonlyQuerySyncProject {
  override val currentSnapshot: Optional<QuerySyncProjectSnapshot>
    get() = snapshotHolder.current

  override val projectData: QuerySyncProjectData
    get() {
      var projectData = QuerySyncProjectData(workspacePathResolver, workspaceLanguageSettings)
      val snapshot = this.snapshotHolder.current.getOrNull()
      if (snapshot != null) {
        projectData = projectData.withSnapshot(snapshot)
      }
      return projectData
    }

  fun getSourceToTargetMap(): SourceToTargetMap = sourceToTargetMap

  fun sync(
    context: BlazeContext,
    lastQuery: PostQuerySyncData?,
  ): QuerySyncProjectSnapshot {
    val coreSyncResult = syncCore(context, lastQuery)
    return updateProjectStructureAndSnapshot(context, coreSyncResult.postQuerySyncData, coreSyncResult.graph)
  }

  @Throws(BuildException::class)
  fun syncQueryData(parentContext: BlazeContext, lastQuery: PostQuerySyncData?) {
    BlazeContext.create(parentContext).use { context ->
      context.push(SyncQueryStatsScope())
      val coreSyncResult = syncCore(context, lastQuery)
      snapshotHolder.setCurrent(
        context,
        this.snapshotHolder.current.orElseThrow().toBuilder()
          .queryData(coreSyncResult.postQuerySyncData)
          .graph(coreSyncResult.graph)
          .build()
      )
    }
  }

  @JvmRecord
  private data class CoreSyncResult(
    val postQuerySyncData: PostQuerySyncData,
    val graph: BuildGraphData
  )

  @Throws(BuildException::class)
  private fun syncCore(
    context: BlazeContext,
    lastQuery: PostQuerySyncData?
  ): CoreSyncResult {
    try {
      SaveUtil.saveAllFiles()
      val postQuerySyncData =
        if (lastQuery == null)
          projectQuerier.fullQuery(projectDefinition, context)
        else
          projectQuerier.update(projectDefinition, lastQuery, context)
      val graph = buildGraphData(postQuerySyncData, context)
      return CoreSyncResult(postQuerySyncData, graph)
    } catch (e: IOException) {
      throw BuildException(e)
    }
  }

  /**
   * Returns the list of project targets related to the given workspace file.
   *
   * @param context               Context
   * @param workspaceRelativePaths Workspace relative file paths to find targets for. A path may be a
   * path to a source file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that
   * file. For a BUILD file, it's the set or targets defined in that file. For a directory, it's
   * the set of all targets defined in all build packages within the directory (recursively).
   */
  fun getProjectTargets(
    context: BlazeContext,
    workspaceRelativePaths: Collection<Path>
  ): Set<TargetsToBuild> {
    return snapshotHolder()
      ?.let { snapshot ->
        workspaceRelativePaths
          .map { path -> snapshot.graph().getProjectTargets(context, path) }
          .toSet()
      }.orEmpty()
  }

  /** Returns the set of targets with direct dependencies on `targets`.  */
  fun getTargetsDependingOn(targets: Set<Label>): Set<Label> {
    val snapshot = snapshotHolder.current.orElseThrow()
    return snapshot.graph().getSameLanguageTargetsDependingOn(targets)
  }

  /** Returns workspace-relative paths of modified files, according to the VCS  */
  @Throws(BuildException::class)
  override fun getWorkingSet(context: BlazeContext): Set<Path> {
    SaveUtil.saveAllFiles()
    val vcsState: VcsState
    val computed = projectQuerier.getVcsState(context)
    if (computed.isPresent) {
      vcsState = computed.get()
    } else {
      context.output(PrintOutput("Failed to compute working set. Falling back on sync data"))
      val snapshot = snapshotHolder.current.orElseThrow()
      vcsState =
        snapshot
          .queryData()
          .vcsState()
          .orElseThrow(
            Supplier { BuildException("No VCS state, cannot calculate affected targets") })
    }
    return vcsState.modifiedFiles()
  }

  @Throws(BuildException::class)
  fun cleanDependencies(context: BlazeContext) {
    try {
      artifactTracker.clear()
    } catch (e: IOException) {
      throw BuildException("Failed to clear dependency info", e)
    }
    val postQuerySyncData = currentSnapshot.orElseThrow().queryData()
    val graph = currentSnapshot.orElseThrow().graph()
    updateProjectStructureAndSnapshot(context, postQuerySyncData, graph)
  }

  @Throws(BuildException::class)
  fun invalidateQuerySyncState(context: BlazeContext) {
    try {
      artifactTracker.clear()
    } catch (e: IOException) {
      throw BuildException("Failed to clear dependency info", e)
    }
    onNewSnapshot(context, QuerySyncProjectSnapshot.EMPTY)
  }

  @Throws(IOException::class, BuildException::class)
  fun build(parentContext: BlazeContext, request: DependencyTracker.DependencyBuildRequest) {
    BlazeContext.create(parentContext).use { context ->
      context.push(BuildDepsStatsScope())
      if (this.dependencyTracker.buildDependenciesForTargets(context, request)) {
        val postQuerySyncData = currentSnapshot.orElseThrow().queryData()
        val graph = currentSnapshot.orElseThrow().graph()
        updateProjectStructureAndSnapshot(context, postQuerySyncData, graph)
      }
    }
  }

  private fun buildGraphData(
    postQuerySyncData: PostQuerySyncData,
    context: Context<*>
  ): BuildGraphData {
    return BlazeQueryParser(postQuerySyncData.querySummary(), context, ImmutableSet.copyOf(handledRuleKinds)).parse()
  }

  @Throws(BuildException::class)
  private fun updateProjectStructureAndSnapshot(
    context: BlazeContext,
    queryData: PostQuerySyncData,
    graph: BuildGraphData
  ): QuerySyncProjectSnapshot {
    val newSnapshot =
      snapshotBuilder.createBlazeProjectSnapshot(
        context,
        queryData,
        graph,
        artifactTracker.getStateSnapshot(),
        projectProtoTransforms.composedTransform
      )
    onNewSnapshot(context, newSnapshot)
    return newSnapshot
  }

  @Throws(IOException::class, BuildException::class)
  fun buildAppInspector(
    parentContext: BlazeContext, inspector: Label
  ): ImmutableCollection<Path> {
    BlazeContext.create(parentContext).use { context ->
      context.push(BuildDepsStatsScope())
      return appInspectorTracker.buildAppInspector(context, inspector)
    }
  }

  @Throws(BuildException::class)
  fun enableAnalysis(context: BlazeContext, projectTargets: Set<Label>) {
    try {
      context.output(
        PrintOutput.output(
          "Building dependencies for:\n  " + Joiner.on("\n  ").join(projectTargets)
        )
      )
      build(context, DependencyTracker.DependencyBuildRequest.multiTarget(projectTargets))
    } catch (e: IOException) {
      throw BuildException("Failed to build dependencies", e)
    }
  }

  @Throws(BuildException::class)
  fun enableAnalysis(context: BlazeContext) {
    try {
      context.output(PrintOutput.output("Building dependencies for project"))
      build(context, DependencyTracker.DependencyBuildRequest.wholeProject())
    } catch (e: IOException) {
      throw BuildException("Failed to build dependencies", e)
    }
  }

  fun canEnableAnalysisFor(workspacePath: Path): Boolean {
    return getProjectTargets(BlazeContext.create(), listOf(workspacePath)).isNotEmpty()
  }

  fun isReadyForAnalysis(path: Path): Boolean {
    if (!path.startsWith(workspaceRoot.path())) {
      // Not in the workspace.
      // p == null can occur if the file is a zip entry.
      return true
    }

    val pendingTargets =
      snapshotHolder()
        ?.getPendingTargets(workspaceRoot.relativize(path))
        .orEmpty()
    return pendingTargets.isEmpty()
  }

  @Throws(IOException::class)
  fun readSnapshotFromDisk(context: BlazeContext): PostQuerySyncData? {
    val f = snapshotFilePath.toFile()
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

  /** Returns true if `absolutePath` is in a project include  */
  override fun containsPath(absolutePath: Path): Boolean {
    if (!workspaceRoot.isInWorkspace(absolutePath.toFile())) {
      return false
    }
    val workspaceRelative = workspaceRoot.path().relativize(absolutePath)
    return projectDefinition.isIncluded(workspaceRelative)
  }

  /**
   * Returns true if `absolutePath` is specified in a project exclude.
   *
   *
   * A path not added or excluded the project definition will return false for both `containsPath` and `explicitlyExcludesPath`
   */
  override fun explicitlyExcludesPath(absolutePath: Path): Boolean {
    if (!workspaceRoot.isInWorkspace(absolutePath.toFile())) {
      return false
    }
    val workspaceRelative = workspaceRoot.path().relativize(absolutePath)
    return projectDefinition.isExcluded(workspaceRelative)
  }

  /**
   * Returns true if the file is in the project and has been added to the workspace since the last
   * IDE sync operation (Sync Project with BUILD files), return false otherwise, or an empty [ ] if this information cannot be determined.
   *
   *
   * Newly added files are determined by the following conditions:
   *  * They are in a project source root
   *  * They don't exist as a known source file for a target.
   *  * They don't exist at the vcs snapshot at the most recent sync
   */
  fun projectFileAddedSinceSync(absolutePath: Path): Optional<Boolean> {
    if (!workspaceRoot.isInWorkspace(absolutePath.toFile())) {
      return Optional.of<Boolean>(false)
    }

    if (!containsPath(absolutePath)) {
      return Optional.of<Boolean>(false)
    }

    // Check known source files.
    val workspaceRelative = workspaceRoot.path().relativize(absolutePath)
    if (snapshotHolder
        .current
        .map { it.graph().sourceFileToLabel(workspaceRelative).isPresent }.orElse(false)) {
      return Optional.of<Boolean>(false)
    }

    val snapshotPath =
      snapshotHolder
        .current
        .flatMap { it.queryData().vcsState() }
        .flatMap { it.workspaceSnapshotPath }

    return snapshotPath.map { !it.resolve(workspaceRelative).toFile().exists() }
  }

  @Throws(IOException::class)
  private fun writeToDisk(snapshot: QuerySyncProjectSnapshot) {
    AtomicFileWriter.create(snapshotFilePath).use { writer ->
      GZIPOutputStream(writer.outputStream).use { zip ->
        val message = SnapshotSerializer().visit(snapshot.queryData()).toProto()
        val codedOutput = CodedOutputStream.newInstance(zip, 1024 * 1024)
        message.writeTo(codedOutput)
        codedOutput.flush()
      }
      writer.onWriteComplete()
    }
  }

  @Throws(BuildException::class)
  private fun onNewSnapshot(context: BlazeContext, newSnapshot: QuerySyncProjectSnapshot) {
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

    snapshotHolder.setCurrent(context, newSnapshot)
    try {
      writeToDisk(newSnapshot)
    } catch (e: IOException) {
      throw BuildException("Failed to write snapshot to disk", e)
    }
  }

  override fun getBugreportFiles(): Map<String, ByteSource> =
    ImmutableMap.builder<String, ByteSource>()
      .put(snapshotFilePath.fileName.toString(), MoreFiles.asByteSource(snapshotFilePath))
      .putAll(artifactTracker.getBugreportFiles())
      .putAll(snapshotHolder.getBugreportFiles())
      .putAll(artifactStore.getBugreportFiles())
      .putAll(buildArtifactCache.getBugreportFiles())
      .build()

  // TODO: b/397649793 - Remove this method when fixed.
  override fun dependsOnAnyOf_DO_NOT_USE_BROKEN(target: Label, deps: Set<Label>): Boolean {
    return snapshotHolder
      .current
      .map { it.graph() }
      .map { it.dependsOnAnyOf_DO_NOT_USE_BROKEN(target, deps) }
      .orElse(false)
  }

  companion object {
    private val logger = Logger.getInstance(QuerySyncProject::class.java)
  }
}
