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
package com.google.idea.blaze.qsync.project

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import com.google.common.graph.Traverser
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.RuleKinds
import com.google.idea.blaze.common.TargetPattern.ScopeStatus.INCLUDED
import com.google.idea.blaze.common.TargetPatternCollection
import com.google.idea.blaze.common.TargetTree
import com.google.idea.blaze.qsync.project.BuildGraphDataImpl.Location.Companion.Location
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType
import com.google.idea.blaze.qsync.project.TargetsToBuild.Companion.forUnknownSourceFile
import com.google.idea.blaze.qsync.project.TargetsToBuild.Companion.targetGroup
import com.google.idea.blaze.qsync.query.PackageSet
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Path
import java.util.Collections
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.jvm.optionals.getOrNull

/**
 * The build graph of all the rules that make up the project.
 *
 *
 * This class is immutable. A new instance of it will be created every time there is any change
 * to the project structure.
 */
@ConsistentCopyVisibility
data class BuildGraphDataImpl private constructor(
  @VisibleForTesting @JvmField val storage: Storage,
) : BuildGraphData {

  private val projectDefinitionTargetPatterns: TargetPatternCollection = storage.projectDefinitionTargetPatterns
  private val alwaysBuildTargets: Set<Label> = computeAlwaysBuildTargets(storage)
  private val sourceOwners: Map<Label, List<Label>> = computeSourceOwners(storage)
  private val nodes: Map<Label, GraphNode> = computeNodes(storage)
  private val packages: PackageSet = computePackages(storage)
  @VisibleForTesting
  val allSupportedTargets: TargetTree = TargetTree.create(storage.allSupportedTargetLabels)
  override val externalDependencyCountForStatsOnly: Int = computeExternalDependencyCount(storage)

  private interface GraphNode {
    val label: Label
    val deps: Collection<GraphNode>
    val rdeps: Collection<GraphNode>
    val data: NodeData
    val protoModes: Set<BuildGraphData.ProtoMode>
    val androidTransitionTarget: Label?
  }

  private sealed interface NodeData
  private class ProjectNodeData(val target: ProjectTarget) : NodeData
  private object ExternalNodeData : NodeData

  private class GraphNodeImpl(
    override val label: Label,
    override val data: NodeData,
  ) : GraphNode {
    override val deps = mutableListOf<GraphNodeImpl>()
    override val rdeps = mutableListOf<GraphNodeImpl>()
    val downwardProtoModes = mutableSetOf<BuildGraphData.ProtoMode>()
    val upwardProtoModes = mutableSetOf<BuildGraphData.ProtoMode>()
    override val protoModes: Set<BuildGraphData.ProtoMode>
      get() = buildSet {
        explicitProtoMode?.let { add(it) }
        addAll(downwardProtoModes)
        addAll(upwardProtoModes)
      }
    var explicitProtoMode: BuildGraphData.ProtoMode? = null
    override var androidTransitionTarget: Label? = null
    override fun toString(): String {
      return "GraphNodeImpl(label=$label, data=$data)"
    }
  }

  override fun packages(): PackageSet = packages
  override fun getProjectTarget(label: Label): ProjectTarget? = storage.targetMap[label]
  override fun allLoadedTargets(): Collection<Label> = storage.targetMap.keys

  /**
   * Returns a [Label] representing the given path in the workspace with the current build packages. The file does not need to exist.
   */
  @VisibleForTesting
  override fun pathToLabel(file: Path): Label? {
    var path: Path? = file
    do {
      path = path?.parent
      val probe = path ?: Path.of("")
      val probeNameCount = path?.nameCount ?: 0
      if (packages.contains(probe)) {
        return Label.of("//$probe:" + file.subpath(probeNameCount, file.nameCount).toString())
      }
    } while (path != null)
    return null
  }

  /**
   * If the given path represents a currently known source file returns a [Label] representing the given path in the workspace with
   * the current build packages.
   */
  override fun sourceFileToLabel(sourceFile: Path): Label? {
    val sourceFileLabel = pathToLabel(sourceFile) ?: return null
    return if (storage.sourceFileLabels.contains(sourceFileLabel)) sourceFileLabel else null
  }

  /**
   * Calculates the set of direct reverse dependencies for a set of targets (including the targets
   * themselves).
   */
  override fun getSameLanguageTargetsDependingOn(targets: Set<Label>): Set<Label> {
    return buildSet {
      addAll(targets)
      for (target in targets) {
        val targetLanguages = storage.targetMap[target]?.languages().orEmpty()
        // filter the rdeps based on the languages, removing those that don't have a common
        // language. This ensures we don't follow reverse deps of (e.g.) a java target depending on
        // a cc target.
        getRdeps(target)
          .filter {
            val depTarget = it.data as? ProjectNodeData ?: return@filter false
            !Collections.disjoint(
              depTarget.target.languages(),
              targetLanguages
            )
          }
          .forEach { add(it.label) }
      }
    }
  }


  /**
   * Returns all in project targets that depend on the source file at `sourcePath` via an
   * in-project dependency chain. Used to determine possible test targets for a given file.
   *
   *
   * If project target A depends on external target B, and external target B depends on project
   * target C, target A is *not* included in `getReverseDeps` for a source file in target C.
   */
  override fun getReverseDepsForSource(sourcePath: Path): Collection<ProjectTarget> {
    val targetOwners = getSourceFileOwners(sourcePath).takeUnless { it.isEmpty() } ?: return emptyList()

    return Traverser.forGraph<Label> { this.getRdeps(it).map { it.label } }.breadthFirst(targetOwners)
      .asSequence()
      .mapNotNull { storage.targetMap[it] }
      .toSet()
  }

  // TODO: b/397649793 - Remove this method when fixed.
  override fun dependsOnAnyOf_DO_NOT_USE_BROKEN(
    projectTarget: Label,
    deps: Set<Label>,
  ): Boolean {
    val projectTargetSingleton = listOf(projectTarget)
    val queue = ArrayDeque(projectTargetSingleton)
    val seen = HashSet<Label>(projectTargetSingleton)
    while (!queue.isEmpty()) {
      val target = queue.removeFirst()
      if (deps.contains(target)) {
        return true
      }
      val targetInfo = storage.targetMap[target] ?: continue
      queue.addAll(targetInfo.deps().filter { seen.add(it) })
    }
    return false
  }

  /**
   * Build graph data in one place.
   */
  data class Storage(
    val sourceFileLabels: Set<Label>,
    val targetMap: Map<Label, ProjectTarget>,
    val allSupportedTargetLabels: Set<Label>,
    val projectDefinitionTargetPatterns: TargetPatternCollection,
    val alwaysBuildRules: Set<String>,
    val supportedBuildRules: Set<String>,
    val protoRules: BuildGraphData.ProtoRules,
  ) {

    /**
     * Builder for [BuildGraphDataImpl].
     */
    class Builder {
      private val sourceFileLabelsBuilder = mutableSetOf<Label>()
      private val targetMapBuilder = mutableMapOf<Label, ProjectTarget>()
      private val allTargetLabelsBuilder = mutableSetOf<Label>()

      fun build(
        projectDefinitionTargetPatterns: TargetPatternCollection,
        alwaysBuildRules: Set<String>,
        supportedBuildRules: Set<String>,
        protoRules: BuildGraphData.ProtoRules,
      ): BuildGraphDataImpl {
        val storage =
          Storage(
            sourceFileLabels = sourceFileLabelsBuilder,
            targetMap = targetMapBuilder,
            allSupportedTargetLabels = allTargetLabelsBuilder,
            projectDefinitionTargetPatterns = projectDefinitionTargetPatterns,
            alwaysBuildRules = alwaysBuildRules,
            supportedBuildRules = supportedBuildRules,
            protoRules = protoRules
          )
        return BuildGraphDataImpl(storage)
      }

      fun addSourceFileLabel(label: Label): Builder {
        sourceFileLabelsBuilder.add(label)
        return this
      }

      fun addTarget(label: Label, target: ProjectTarget): Builder {
        targetMapBuilder.put(label, target)
        return this
      }

      fun addSupportedTargetLabel(label: Label): Builder {
        allTargetLabelsBuilder.add(label)
        return this
      }
    }

    companion object {
      fun builder(): Builder {
        return Builder()
      }
    }
  }

  /** Represents a location on a file.  */
  data class Location(
    val file: Path, // Relative to workspace root
    val row: Int,
    val column: Int,
  ) {

    companion object {
      /**
       * Creates an instance of [Location] from a [location] string in the form as provided by bazel, i.e. `path/to/file:lineno:columnno`
       */
      fun Location(location: String): Location {
        val matcher: Matcher = PATTERN.matcher(location)
        Preconditions.checkArgument(matcher.matches(), "Location not recognized: %s", location)
        val file = Path.of(matcher.group(1))
        Preconditions.checkState(
          !file.startsWith("/"),
          "Filename starts with /: ensure that "
          + "`--relative_locations=true` was specified in the query invocation."
        )
        val row = matcher.group(2).toInt()
        val column = matcher.group(3).toInt()
        return Location(file, row, column)
      }

      private val PATTERN: Pattern = Pattern.compile("(.*):(\\d+):(\\d+)")
    }
  }

  override fun getSourceFileOwners(path: Path): Set<Label> {
    return sourceFileToLabel(path)?.let { getSourceFileOwners(it) }.orEmpty()
  }

  override fun getSourceFileOwners(label: Label): Set<Label> {
    return sourceOwners[label]?.toSet().orEmpty()
  }

  @Deprecated(
    """Choosing a target based on the number of deps it has is not a good strategy, as we
        could end up selecting one that doesn't build in the current config. Allow the user to
        choose, or require the projects source -> target mapping to be unambiguous instead."""
  )
  override fun selectLabelWithLeastDeps(candidates: Collection<Label>): Label {
    return candidates.minBy { storage.targetMap[it]?.deps()?.size ?: Int.MAX_VALUE }
  }

  /** Returns a list of all the java source files of the project, relative to the workspace root.  */
  override fun getJavaSourceFiles(): List<Path> {
    return getSourceFilesByRuleKindAndType(RuleKinds::isJava, SourceType.REGULAR_JVM)
      .values
      .flatten()
  }

  override fun getSourceFilesByRuleKindAndType(
    ruleKindPredicate: (String) -> Boolean, vararg sourceTypes: SourceType,
  ): Map<Label, List<Path>> {
    return storage.targetMap.values.asSequence()
      .filter { ruleKindPredicate(it.kind()) }
      .map { target ->
        target.label() to
          sourceTypes.flatMap { target.sourceLabels()[it] }
            .filter { storage.sourceFileLabels.contains(it) }
            .map { it.toFilePath() }
      }
      .filter { it.second.isNotEmpty() }
      .toMap()
  }

  /**
   * Returns a list of regular (java/kt) source files owned by an Android target, relative to the
   * workspace root.
   */
  override fun getAndroidSourceFiles(): List<Path> =
    getSourceFilesByRuleKindAndType(RuleKinds::isAndroid, SourceType.REGULAR_JVM)
      .values
      .flatten()

  override fun getAndroidResourceFiles(): List<Path> =
    getSourceFilesByRuleKindAndType(RuleKinds::isAndroid, SourceType.ANDROID_RESOURCES)
      .values
      .flatten()

  /** Returns a list of custom_package fields that used by current project.  */
  override fun getAllCustomPackages(): Set<String> {
    return storage.targetMap.values
      .asSequence()
      .mapNotNull { it.customPackage().getOrNull() }
      .toSet()
  }

  private fun getDependencyTrackingIncludeExternalDependencies(target: ProjectTarget): Boolean {
    return target.languages().asSequence()
      .map { it.dependencyTrackingBehavior }
      .any { it.shouldIncludeExternalDependencies }
  }

  /**
   * Returns the list of project targets related to the given workspace file.
   *
   * @param context Context
   * @param workspaceRelativePath Workspace relative file path to find targets for. This may be a
   * source file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that
   * file. For a BUILD file, it's the set or targets defined in that file. For a directory, it's
   * the set of all targets defined in all build packages within the directory (recursively).
   */
  override fun getProjectTargets(
    workspaceRelativePath: Path,
  ): TargetsToBuild {
    // TODO: relativize here.
    // TODO: support Bazel.
    if (workspaceRelativePath.endsWith("BUILD")) {
      val packagePath = workspaceRelativePath.parent
      return targetGroup(allSupportedTargets.getDirectTargets(packagePath).toList())
    } else {
      val targets = allSupportedTargets.getSubpackages(workspaceRelativePath).toList()
      if (targets.isNotEmpty()) {
        // this will only be non-empty for directories
        return targetGroup(targets)
      }
    }
    // Now a build file or a directory containing packages.
    val fileLabel = sourceFileToLabel(workspaceRelativePath)
    val targetOwner = fileLabel?.let { getSourceFileOwners(it) }.orEmpty()
    return when {
      fileLabel == null -> forUnknownSourceFile(workspaceRelativePath)
      targetOwner.isEmpty() -> TargetsToBuild.None
      else -> TargetsToBuild.forSourceFile(targetOwner, workspaceRelativePath)
    }
  }

  /**
   * Returns the set of [target languages][ProjectTarget.languages] for a set of project
   * targets.
   */
  override fun getTargetLanguages(targets: Set<Label>): Set<QuerySyncLanguage> {
    return targets
      .transitiveClosure()
      .flatMap { it.languages() }
      .toSet()
  }

  /**
   * Traverses the dependency graph starting from `projectTargets` and returns the first level
   * of dependencies which are either not in the project scope or must be built as they are not
   * directly supported by the IDE.
   */
  private fun getTargetsRequiredFor(projectTargets: Collection<Label>): Set<Label> {
    val externalDeps = mutableSetOf<Label>()
    val seen = HashSet<Label>(projectTargets)
    val queue = ArrayDeque(projectTargets)
    while (!queue.isEmpty()) {
      val target = queue.removeFirst()
      val targetInfo = storage.targetMap[target]
      if (targetInfo == null || alwaysBuildTargets.contains(target)) {
        // External dependency.
        externalDeps.add(target)
        continue
      }
      val dependencyTracking = getDependencyTrackingIncludeExternalDependencies(targetInfo)
      if (dependencyTracking) {
        queue.addAll(targetInfo.deps().filter { seen.add(it) })
      }
    }
    return externalDeps
  }

  override val projectSupportedTargetCountForStatsOnly: Int
    get() = allSupportedTargets.targetCountForStatsOnly

  override val targetMapSizeForStatsOnly: Int
    get() = storage.targetMap.size


  /**
   * Calculates the [RequestedTargets] for a project target.
   *
   * @return Requested targets. The [RequestedTargets.targetsToBuild] will match the parameter
   * given; the [RequestedTargets.requiredTargets] will be determined by the
   * [.getDependencyTrackingIncludeExternalDependencies] of the targets
   * given.
   */
  override fun computeRequestedTargets(
    projectTargets: Collection<Label>,
    replaceNativeTargetsWithAndroidTransitionTriggeringTargets: Boolean,
  ): RequestedTargets {
    val filteredProjectTargets =
      filterRedundantTargets(collectTargetsToBuildForSourcesIn(projectTargets, replaceNativeTargetsWithAndroidTransitionTriggeringTargets))
    val requiredTargets = getTargetsRequiredFor(filteredProjectTargets)
    return RequestedTargets(filteredProjectTargets, requiredTargets)
  }

  /**
   * Collects project targets that contribute
   */
  private fun collectTargetsToBuildForSourcesIn(
    projectTargets: Collection<Label>,
    replaceNativeTargetsWithAndroidTransitionTriggeringTargets: Boolean,
  ): Collection<Label> {
    return buildSet {
      val seenSources = mutableSetOf<Label>()
      projectTargets.forEach { targetLabel ->
        val target = storage.targetMap[targetLabel]
                     ?: let {
                       add(targetLabel); // Unknown target requested so let's just return it.
                       thisLogger().error("Unknown target: $targetLabel")
                       return@forEach
                     }
        var newSourceFileAdded = false
        var containsCcSources = false
        target.sourceLabels().asMap().entries.forEach { (kind, labels) ->
          if (kind !in SUPPORTED_SOURCE_TYPES) return@forEach
          labels.forEach { label ->
            if (seenSources.add(label)) {
              newSourceFileAdded = true
              if (kind == SourceType.REGULAR_CC) {
                containsCcSources = true
              }
            }
          }
        }
        if (newSourceFileAdded) {
          if (add(targetLabel)) {
            if (containsCcSources && replaceNativeTargetsWithAndroidTransitionTriggeringTargets) {
              nodes[targetLabel]?.androidTransitionTarget?.let { add(it) }
            }
          }
        }
      }
    }
  }

  override fun computeWholeProjectTargets(): RequestedTargets {
    return computeRequestedTargets(
      allSupportedTargets.getTargets().filter { projectDefinitionTargetPatterns.inScope(it).status == INCLUDED }.toList(),
      replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false // storage.allSupportedTargets includes them anyway.
    )
  }

  override fun outputStats(context: Context<*>) {
    context.output(
      PrintOutput.log(
        "%-10d Source files",
        storage.sourceFileLabels.size
      )
    )
    context.output(PrintOutput.log("%-10d Java sources", getJavaSourceFiles().size))
    context.output(PrintOutput.log("%-10d Packages", packages.size()))
    context.output(
      PrintOutput.log(
        "%-10d External dependencies",
        externalDependencyCountForStatsOnly
      )
    )
  }

  override fun getActiveLanguages(): Set<QuerySyncLanguage> {
    return buildSet {
      if (storage.targetMap.values.asSequence()
          .map { it.kind() }
          .any(RuleKinds::isJava)
      ) {
        add(QuerySyncLanguage.JVM)
      }
      if (storage.targetMap.values.asSequence()
          .map { it.kind() }
          .any(RuleKinds::isCc)
      ) {
        add(QuerySyncLanguage.CC)
      }
    }
  }

  private fun getRdeps(target: Label): Collection<GraphNode> {
    return nodes[target]?.rdeps.orEmpty()
  }

  /**
   * Use the direct and transitive dependencies of an initial set of targets to prune the initial
   * set of redundant targets. Redundant targets that are contained in any of the direct/indirect
   * dependencies of the initial set of targets. This improves performance by reducing the targets
   * that are built.
   */
  fun filterRedundantTargets(projectTargets: Collection<Label>): Set<Label> {
    return filterRedundantTargets(graph = { storage.targetMap[it]?.deps().orEmpty() }, starting = projectTargets.toSet())
  }

  private fun Collection<Label>.transitiveClosure(): Sequence<ProjectTarget> {
    return traverseDag(
      valueEmitter = { storage.targetMap[it] },
      edgeSelector = { _, targetInfo ->
        val isKnownTargetWithTrackedDependencies = (targetInfo != null) && getDependencyTrackingIncludeExternalDependencies(targetInfo)
        if (isKnownTargetWithTrackedDependencies) targetInfo.deps() else emptyList()
      }
    )
  }

  companion object {
    /**
     * Filter the initial set of targets to a minimal set that may be reached based on the provided
     * graph by running BFS (breadth-first search) on the direct/transitively linked targets on the
     * map.
     */
    @JvmStatic
    fun <T> filterRedundantTargets(graph: (T) -> Set<T>, starting: Set<T>): Set<T> {
      // Store the direct dependencies of the starting set of targets in a queue and run BFS.
      val queue = ArrayDeque(starting.asSequence().flatMap { graph(it) }.toSet())

      val visited = HashSet<T>()
      while (!queue.isEmpty()) {
        val target = queue.removeFirst()
        if (visited.add(target)) {
          queue.addAll(graph(target))
        }
      }
      return starting
        .asSequence()
        .filter { !visited.contains(it) }
        .toSet()
    }

    @JvmStatic
    fun builder(): Storage.Builder {
      return Storage.builder()
    }

    private fun computeSourceOwners(storage: Storage): Map<Label, List<Label>> {
      return storage.targetMap.values.asSequence()
        .flatMap { target ->
          target.sourceLabels()
            .values()
            .asSequence()
            .map { it to target.label() }
        }
        .groupBy({ it.first }, { it.second })
    }

    private fun computeAlwaysBuildTargets(
      storage: Storage,
    ): Set<Label> {
      val sourceOwners = computeSourceOwners(storage)
      return storage.targetMap.values
        .filter { target ->
          val sourceLabels = target.sourceLabels()
          return@filter (
                          if (storage.supportedBuildRules.isEmpty()) target.kind() in storage.alwaysBuildRules
                          else target.kind() !in storage.supportedBuildRules
                        ) ||
                        sourceLabels[SourceType.AIDL].isNotEmpty() ||
                        sourceLabels.values().any { !sourceOwners.containsKey(it) }
        }
        .map { it.label() }
        .toSet()
    }

    private fun computeNodes(storage: Storage): Map<Label, GraphNode> {
      val nodes = buildGraph(storage)
      propagateProtoModes(nodes.values, storage.protoRules)
      propagateAndroidTransitionTargets(nodes.values)
      return nodes
    }

    private fun buildGraph(storage: Storage): MutableMap<Label, GraphNodeImpl> {
      val nodes: MutableMap<Label, GraphNodeImpl> = hashMapOf()
      for ((label, target) in storage.targetMap) {
        nodes[label] = GraphNodeImpl(label, ProjectNodeData(target))
      }
      for (node in nodes.values.toList()) {
        val target = (node.data as? ProjectNodeData)?.target ?: continue
        for (depLabel in target.allDeps()) {
          val depNode = nodes.getOrPut(depLabel) { GraphNodeImpl(depLabel, ExternalNodeData) }
          depNode.rdeps.add(node)
          node.deps.add(depNode)
        }
      }
      return nodes
    }

    private fun propagateProtoModes(nodes: Collection<GraphNodeImpl>, protoRules: BuildGraphData.ProtoRules) {
      fun initializeExplicitNodes(): List<GraphNodeImpl> {
        return buildList {
          for (node in nodes) {
            val explicitProtoMode = getProtoMode(node.data, protoRules)
            if (explicitProtoMode != null) {
              node.explicitProtoMode = explicitProtoMode
              add(node)
            }
          }
        }
      }

      fun propagateDownward(explicitNodes: List<GraphNodeImpl>) {
        val queue = ArrayDeque(explicitNodes)
        while (queue.isNotEmpty()) {
          val u = queue.removeFirst()
          for (v in u.deps) {
            if (v.explicitProtoMode == null) {
              val modesToPropagate = buildSet {
                u.explicitProtoMode?.let { add(it) }
                addAll(u.downwardProtoModes)
              }
              if (v.downwardProtoModes.addAll(modesToPropagate)) {
                queue.addLast(v)
              }
            }
          }
        }
      }

      fun propagateUpward(explicitNodes: List<GraphNodeImpl>) {
        val queue = ArrayDeque(explicitNodes)
        while (queue.isNotEmpty()) {
          val u = queue.removeFirst()
          for (v in u.rdeps) {
            val modesToPropagate = buildSet {
              u.explicitProtoMode?.let { add(it) }
              addAll(u.upwardProtoModes)
            }
            if (v.upwardProtoModes.addAll(modesToPropagate)) {
              queue.addLast(v)
            }
          }
        }
      }

      val explicitNodes = initializeExplicitNodes()
      propagateDownward(explicitNodes)
      propagateUpward(explicitNodes)
    }

    private fun propagateAndroidTransitionTargets(nodes: Collection<GraphNodeImpl>) {
      fun initializeExplicitNodes(): List<GraphNodeImpl> {
        return buildList {
          for (node in nodes) {
            val target = (node.data as? ProjectNodeData)?.target ?: continue
            if (ANDROID_TRANSITION_RULES.contains(target.kind())) {
              node.androidTransitionTarget = node.label
              add(node)
            }
          }
        }
      }

      fun propagateDownward(explicitNodes: List<GraphNodeImpl>) {
        val queue = ArrayDeque(explicitNodes)
        while (queue.isNotEmpty()) {
          val u = queue.removeFirst()
          for (v in u.deps) {
            if (v.androidTransitionTarget == null) {
              v.androidTransitionTarget = u.androidTransitionTarget
              queue.addLast(v)
            }
          }
        }
      }

      val explicitNodes = initializeExplicitNodes()
      // We are looking for the nearest ancestor that is an Android transition target.
      // Since Android transition targets are ancestors of CC targets, we propagate downwards.
      propagateDownward(explicitNodes)
    }

    private fun getProtoMode(data: NodeData, protoRules: BuildGraphData.ProtoRules): BuildGraphData.ProtoMode? {
      if (data is ProjectNodeData) {
        return when (data.target.kind()) {
          in protoRules.fullModeRuleNames -> BuildGraphData.ProtoMode.FULL
          in protoRules.liteModeRuleNames -> BuildGraphData.ProtoMode.LITE
          else -> null
        }
      }
      return null
    }

    private fun computePackages(storage: Storage): PackageSet {
      val packages = PackageSet.Builder()
      for (sourceFile in storage.sourceFileLabels) {
        if (sourceFile.name == "BUILD" || sourceFile.name == "BUILD.bazel") {
          // TODO: b/334110669 - support Bazel workspaces.
          packages.add(sourceFile.getBuildPackagePath())
        }
      }
      return packages.build()
    }

    private fun computeExternalDependencyCount(storage: Storage): Int {
      return storage.targetMap.values.asSequence()
        .flatMap { target -> target.deps().asSequence().filter { !storage.targetMap.containsKey(it) } }
        .distinct()
        .count()
    }
  }

  override fun getProtoModes(label: Label): Set<BuildGraphData.ProtoMode> {
    return nodes[label]?.protoModes ?: emptySet()
  }
}

private val SUPPORTED_SOURCE_TYPES = setOf(
  SourceType.REGULAR_JVM,
  SourceType.REGULAR_CC,
  SourceType.REGULAR_PROTO,
  SourceType.ANDROID_RESOURCES,
  SourceType.ANDROID_MANIFEST
)

/**
 * Traverse the graph defined by [edgeSelector] and return a sequence of values produced by [valueEmitter].
 */
@VisibleForTesting
inline fun <N, V> Collection<N>.traverseDag(
  crossinline valueEmitter: (N) -> V?,
  crossinline edgeSelector: (node: N, emittedValue: V?) -> Collection<N>,
): Sequence<V> {
  val seen = HashSet<N>(this)
  val queue = ArrayDeque(this)
  return sequence {
    while (!queue.isEmpty()) {
      val node = queue.removeFirst()
      val value = valueEmitter(node)
      if (value != null) {
        yield(value)
      }
      queue.addAll(edgeSelector(node, value).filter { seen.add(it) })
    }
  }
}

private fun ProjectTarget.allDeps(): Sequence<Label> =
  sequence {
    yieldAll(deps())
    testRule().getOrNull()?.let { yield(it) }
  }
// TODO: b/465698133 - find a way to move such configuration to _deps.bzl files.
private val ANDROID_TRANSITION_RULES = setOf("android_binary", "ndk_cc_dynamic_library_force_android_rule", "_android_binary")
