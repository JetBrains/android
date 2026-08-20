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

import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.io.ByteSource
import com.google.common.io.MoreFiles
import com.google.common.util.concurrent.Uninterruptibles
import com.google.idea.blaze.base.async.executor.BlazeExecutor
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.plugin.BuildSystemVersionChecker
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver
import com.google.idea.blaze.base.util.SaveUtil
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.artifact.BuildArtifactCache
import com.google.idea.blaze.common.vcs.VcsState
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.BlazeQueryParser
import com.google.idea.blaze.qsync.ProjectBuilder
import com.google.idea.blaze.qsync.ProjectStructureReader
import com.google.idea.blaze.qsync.RefreshParameters
import com.google.idea.blaze.qsync.deps.ArtifactTracker
import com.google.idea.blaze.qsync.java.PackageReader
import com.google.idea.blaze.qsync.project.BuildGraphData
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectPath
import com.google.idea.blaze.qsync.project.ProjectProto
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.update.ProjectProtoUpdateOperation
import com.intellij.openapi.project.Project
import java.io.IOException
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ExecutionException
import kotlin.jvm.optionals.getOrNull

/**
 * Encapsulates a loaded query sync project and it's dependencies for readonly consumers.
 *
 * This class also maintains a [QuerySyncProjectData] instance whose job is to expose project state to the rest of the plugin and IDE.
 */
interface ReadonlyQuerySyncProject {
  val buildSystem: BuildSystem
  val projectDefinition: ProjectDefinition
  val languageSettings: QuerySyncLanguageSettings
  val workspaceRoot: WorkspaceRoot
  val projectPathResolver: ProjectPath.Resolver
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
 * This class also maintains a [QuerySyncProjectData] instance whose job is to expose project state to the rest of the plugin and IDE.
 */
class QuerySyncProject(
  val ideProject: Project,
  private val snapshotHolder: SnapshotHolder,
  override val workspaceRoot: WorkspaceRoot,
  val artifactTracker: ArtifactTracker<*>,
  val buildArtifactCache: BuildArtifactCache,
  val dependencyBuilder: DependencyBuilder,
  val dependencyTracker: DependencyTracker,
  private val appInspectorTracker: AppInspectorTracker,
  private val projectQuerier: ProjectQuerier,
  private val projectBuilder: ProjectBuilder,
  override val projectDefinition: ProjectDefinition,
  override val languageSettings: QuerySyncLanguageSettings,
  // TODO(mathewi) only one of these two should strictly be necessary:
  val workspacePathResolver: WorkspacePathResolver,
  override val projectPathResolver: ProjectPath.Resolver,
  val workspaceLanguageSettings: WorkspaceLanguageSettings,
  val sourceToTargetMap: QuerySyncSourceToTargetMap,
  override val buildSystem: BuildSystem,
  val projectProtoUpdateOperations: Collection<ProjectProtoUpdateOperation>,
  val handledRuleKinds: Set<String>,
  val protoRules: BuildGraphData.ProtoRules,
  private val projectStructureReader: ProjectStructureReader,
  val packageReader: PackageReader,
  val parallelPackageReader: PackageReader.ParallelReader,
  val vcsHandler: BlazeVcsHandler?,
  val bazelVersionProvider: BazelVersionHandler,
) : ReadonlyQuerySyncProject {
  override val projectData: QuerySyncProjectData
    get() {
      var projectData = QuerySyncProjectData(workspacePathResolver, workspaceLanguageSettings)
      val snapshot = this.snapshotHolder.current.getOrNull()
      if (snapshot != null) {
        projectData = projectData.withSnapshot(snapshot)
      }
      return projectData
    }

  @JvmRecord data class QueryCoreSyncResult(val postQuerySyncData: PostQuerySyncData, val graph: BuildGraphData)

  @Throws(BuildException::class)
  fun syncQueryCore(context: BlazeContext, postQuerySyncData: PostQuerySyncData): QueryCoreSyncResult {
    val graph = buildGraphData(postQuerySyncData, context)
    return QueryCoreSyncResult(postQuerySyncData, graph)
  }

  fun getBazelVersion(context: BlazeContext): String? {
    return try {
      bazelVersionProvider.getBazelVersion(context).orElse(null)
    } catch (e: BuildException) {
      context.handleExceptionAsWarning("Could not get bazel version", e)
      null
    }
  }

  fun getVcsState(context: BlazeContext): VcsState? {
    val stateFuture = vcsHandler?.getVcsState(context, BlazeExecutor.getInstance().executor) ?: return null
    if (stateFuture.isEmpty()) {
      return null
    }
    return try {
      Uninterruptibles.getUninterruptibly(stateFuture.get())
    } catch (e: ExecutionException) {
      context.handleExceptionAsWarning("WARNING: Could not get VCS state, future updates may be suboptimal", e.cause)
      null
    }
  }

  fun runQueryAndComputePostQuerySyncData(
    context: BlazeContext,
    lastQuery: PostQuerySyncData?,
    vcsState: VcsState?,
    bazelVersion: String?,
    projectStructureData: ProjectStructureData,
  ): PostQuerySyncData {
    val refreshParameters =
      RefreshParameters(
        lastQuery ?: PostQuerySyncData.EMPTY,
        snapshotHolder.current.getOrNull()?.projectDefinition ?: projectDefinition,
        Optional.ofNullable(snapshotHolder.current.getOrNull()?.vcsState),
        Optional.ofNullable(vcsState),
        projectDefinition,
        projectStructureData = projectStructureData,
        requireFullSync = { ctx -> requireFullSync(ctx, lastQuery, vcsState, bazelVersion) },
      )
    val postQuerySyncData = projectQuerier.update(refreshParameters, context)
    return postQuerySyncData
  }

  fun requireFullSync(context: Context<*>, lastQuery: PostQuerySyncData?, latestVcsState: VcsState?, latestBazelVersion: String?): Boolean {
    val currentProject = lastQuery ?: PostQuerySyncData.EMPTY
    val currentSnapshot = snapshotHolder.current.getOrNull()
    val currentProjectDefinition = currentSnapshot?.projectDefinition ?: projectDefinition
    val snapshotVcsState = currentSnapshot?.vcsState
    val snapshotBazelVersion = currentSnapshot?.bazelVersion

    if (!currentProject.querySummary().isCompatibleWithCurrentPluginVersion) {
      context.output(PrintOutput.output("IDE has updated since last sync; performing full query"))
      return true
    }
    if (currentProjectDefinition != projectDefinition) {
      context.output(PrintOutput.output("Project definition has changed; performing full query"))
      return true
    }
    if (snapshotVcsState == null) {
      context.output(PrintOutput.output("No VCS state from last sync: performing full query"))
      return true
    }
    if (latestVcsState == null) {
      context.output(PrintOutput.output("VCS doesn't support delta updates: performing full query"))
      return true
    }
    if (snapshotVcsState.workspaceId != latestVcsState.workspaceId) {
      context.output(
        PrintOutput.output(
          "Workspace has changed %s -> %s: performing full query",
          snapshotVcsState.workspaceId,
          latestVcsState.workspaceId,
        )
      )
      return true
    }
    if (snapshotVcsState.upstreamRevision != latestVcsState.upstreamRevision) {
      context.output(
        PrintOutput.output(
          "Upstream revision has changed %s -> %s: performing full query",
          snapshotVcsState.upstreamRevision,
          latestVcsState.upstreamRevision,
        )
      )
      return true
    }
    if (snapshotBazelVersion != latestBazelVersion) {
      context.output(PrintOutput.output("Bazel version has changed %s -> %s", snapshotBazelVersion, latestBazelVersion))
      return true
    }
    return false
  }

  fun computeProjectStructureData(context: BlazeContext, lastProjectStructureData: ProjectStructureData?): ProjectStructureData {
    return lastProjectStructureData ?: readProjectStructureFromDirectory(context)
  }

  fun readProjectStructureFromDirectory(context: Context<*>): ProjectStructureData =
    projectStructureReader.read(context, workspaceRoot.path(), projectDefinition)

  /** Returns the set of targets with direct dependencies on `targets`. */
  fun getTargetsDependingOn(targets: Set<Label>): Set<Label> {
    val snapshot = snapshotHolder.current.orElseThrow()
    return snapshot.staleGraph.getSameLanguageTargetsDependingOn(targets)
  }

  /** Returns workspace-relative paths of modified files, according to the VCS */
  @Throws(BuildException::class)
  override fun getWorkingSet(context: BlazeContext): Set<Path> {
    SaveUtil.saveAllFiles()
    val vcsState: VcsState
    val computed = getVcsState(context)
    if (computed != null) {
      vcsState = computed
    } else {
      context.output(PrintOutput("Failed to compute working set. Falling back on sync data"))
      val snapshot = snapshotHolder.current.orElseThrow()
      vcsState = snapshot.vcsState ?: throw BuildException("No VCS state, cannot calculate affected targets")
    }
    return vcsState.modifiedFiles()
  }

  fun buildDependencies(context: BlazeContext, request: DependencyTracker.DependencyBuildRequest): Boolean {
    BlazeContext.create(context).use { context ->
      try {
        context.push(BuildDepsStatsScope())
        if (BuildSystemVersionChecker.verifyVersionSupported(ideProject, context, projectData.getBlazeVersionData())) {
          return this.dependencyTracker.buildDependenciesForTargets(context, request)
        }
        throw BuildException(String.format("Failed to build dependencies - %s version not supported", buildSystem.name))
      } catch (e: IOException) {
        throw BuildException("Failed to build dependencies", e)
      }
    }
  }

  private fun buildGraphData(postQuerySyncData: PostQuerySyncData, context: Context<*>): BuildGraphData {
    return BlazeQueryParser(
        projectDefinition.effectiveTargetPatterns,
        postQuerySyncData.querySummary(),
        context,
        ImmutableSet.copyOf(handledRuleKinds),
        HandledRulesProvider.getNotHandledRuleKinds(handledRuleKinds),
        protoRules,
      )
      .parse()
  }

  @Throws(IOException::class, BuildException::class)
  fun buildAppInspector(parentContext: BlazeContext, inspector: Label): ImmutableCollection<Path> {
    BlazeContext.create(parentContext).use { context ->
      context.push(BuildDepsStatsScope())
      return appInspectorTracker.buildAppInspector(context, inspector)
    }
  }

  fun isReadyForAnalysis(path: Path): Boolean {
    if (!path.startsWith(workspaceRoot.path())) {
      // Not in the workspace.
      // p == null can occur if the file is a zip entry.
      return true
    }

    val pendingTargets = snapshotHolder()?.getPendingTargets(workspaceRoot.relativize(path)).orEmpty()
    return pendingTargets.isEmpty()
  }

  class CreateProjectStructureResult(val projectStructure: ProjectProto.Project, val artifactState: ArtifactTracker.State)

  fun createProjectStructure(
    context: BlazeContext,
    graph: BuildGraphData,
    projectStructureData: ProjectStructureData,
  ): CreateProjectStructureResult {
    val artifactTrackerState = artifactTracker.getStateSnapshot()
    val newProjectStructure =
      projectBuilder.createBlazeProjectStructure(
        context,
        projectDefinition,
        graph,
        projectStructureData,
        artifactTrackerState,
        projectProtoUpdateOperations,
      )
    return CreateProjectStructureResult(newProjectStructure, artifactTrackerState)
  }

  /** Returns true if `absolutePath` is in a project include */
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
   * Returns true if the file is in the project and has been added to the workspace since the last IDE sync operation (Sync Project with
   * BUILD files), return false otherwise, or an empty [ ] if this information cannot be determined.
   *
   * Newly added files are determined by the following conditions:
   * * They are in a project source root
   * * They don't exist as a known source file for a target.
   * * They don't exist at the vcs snapshot at the most recent sync
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
    if (snapshotHolder()?.staleGraph?.sourceFileToLabel(workspaceRelative) != null) {
      return Optional.of<Boolean>(false)
    }

    val snapshotPath = snapshotHolder.current.map { it.vcsState }.flatMap { it?.workspaceSnapshotPath }

    return snapshotPath.map { !it.resolve(workspaceRelative).toFile().exists() }
  }

  override fun getBugreportFiles(): Map<String, ByteSource> {
    val snapshotFilePath = getSnapshotFilePath(ideProject)
    return ImmutableMap.builder<String, ByteSource>()
      .put(snapshotFilePath.fileName.toString(), MoreFiles.asByteSource(snapshotFilePath))
      .putAll(artifactTracker.getBugreportFiles())
      .putAll(snapshotHolder.getBugreportFiles())
      .putAll(buildArtifactCache.getBugreportFiles())
      .build()
  }

  // TODO: b/397649793 - Remove this method when fixed.
  override fun dependsOnAnyOf_DO_NOT_USE_BROKEN(target: Label, deps: Set<Label>): Boolean {
    return snapshotHolder.current.map { it.staleGraph }.map { it.dependsOnAnyOf_DO_NOT_USE_BROKEN(target, deps) }.orElse(false)
  }
}

fun getSnapshotFilePath(project: Project): Path {
  return Path.of(project.basePath).resolve("qsyncdata.gz")
}
