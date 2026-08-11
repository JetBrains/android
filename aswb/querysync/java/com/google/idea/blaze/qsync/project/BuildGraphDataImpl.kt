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
import com.google.common.graph.Traverser
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.RuleKinds
import com.google.idea.blaze.common.TargetPattern.ScopeStatus.INCLUDED
import com.google.idea.blaze.common.TargetPatternCollection
import com.google.idea.blaze.common.TargetTree
import com.google.idea.blaze.qsync.project.ProjectTarget.SourceType
import com.google.idea.blaze.qsync.project.TargetsToBuild.Companion.forUnknownSourceFile
import com.google.idea.blaze.qsync.project.TargetsToBuild.Companion.targetGroup
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Path
import java.util.Collections
import kotlin.jvm.optionals.getOrNull

/**
 * The build graph of all the rules that make up the project.
 *
 * This class is immutable. A new instance of it will be created every time there is any change to the project structure.
 */
@ConsistentCopyVisibility
data class BuildGraphDataImpl private constructor(@VisibleForTesting @JvmField val storage: Storage) : BuildGraphData {

  private val projectDefinitionTargetPatterns: TargetPatternCollection = storage.projectDefinitionTargetPatterns
  private val alwaysBuildTargets: Set<Label> = computeAlwaysBuildTargets(storage)
  private val sourceOwners: Map<Label, List<Label>> = computeSourceOwners(storage)
  private val nodes: Map<Label, GraphNode> = computeNodes(storage)

  @VisibleForTesting
  val allSupportedTargets: TargetTree =
    TargetTree.create(
      storage.buildPackages.values.flatMap { pkg -> pkg.allSupportedTargetNames.map { name -> pkg.packageLabel.siblingWithName(name) } }
    )
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

  private class GraphNodeImpl(override val label: Label, override val data: NodeData) : GraphNode {
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

  override val allLoadedBuildPackages: Collection<BuildGraphData.BuildPackage>
    get() = storage.buildPackages.values

  override fun getBuildPackage(packageLabel: Label): BuildGraphData.BuildPackage? {
    return storage.buildPackages[packageLabel.getPackageLabel()]
  }

  override fun getProjectTarget(label: Label): ProjectTarget? = storage.buildPackages[label.getPackageLabel()]?.targetMap?.get(label.name)

  override fun allLoadedTargets(): Sequence<ProjectTarget> = storage.buildPackages.values.asSequence().flatMap { it.targetMap.values }

  override fun isAlwaysBuild(label: Label): Boolean = alwaysBuildTargets.contains(label)

  /** Returns a [Label] representing the given path in the workspace with the current build packages. The file does not need to exist. */
  @VisibleForTesting
  fun pathToLabel(file: Path): Label? {
    var path: Path? = file
    do {
      path = path?.parent
      val probe = path ?: Path.of("")
      val probeNameCount = path?.nameCount ?: 0
      if (getBuildPackage(probe) != null) {
        return Label.of("//$probe:" + file.subpath(probeNameCount, file.nameCount).toString())
      }
    } while (path != null)
    return null
  }

  /**
   * If the given path represents a currently known source file returns a [Label] representing the given path in the workspace with the
   * current build packages.
   */
  override fun sourceFileToLabel(sourceFile: Path): Label? {
    val sourceFileLabel = pathToLabel(sourceFile) ?: return null
    val pkgStorage = storage.buildPackages[sourceFileLabel.getPackageLabel()] ?: return null
    return if (pkgStorage.sourceFileNames.contains(sourceFileLabel.name)) sourceFileLabel else null
  }

  /** Calculates the set of direct reverse dependencies for a set of targets (including the targets themselves). */
  override fun getSameLanguageTargetsDependingOn(targets: Set<Label>): Set<Label> {
    return buildSet {
      addAll(targets)
      for (target in targets) {
        val pkgStorage = storage.buildPackages[target.getPackageLabel()]
        val targetLanguages = pkgStorage?.targetMap?.get(target.name)?.languages().orEmpty()
        // filter the rdeps based on the languages, removing those that don't have a common
        // language. This ensures we don't follow reverse deps of (e.g.) a java target depending on
        // a cc target.
        getRdeps(target)
          .filter {
            val depTarget = it.data as? ProjectNodeData ?: return@filter false
            !Collections.disjoint(depTarget.target.languages(), targetLanguages)
          }
          .forEach { add(it.label) }
      }
    }
  }

  /**
   * Returns all in project targets that depend on the source file at `sourcePath` via an in-project dependency chain. Used to determine
   * possible test targets for a given file.
   *
   * If project target A depends on external target B, and external target B depends on project target C, target A is *not* included in
   * `getReverseDeps` for a source file in target C.
   */
  override fun getReverseDepsForSource(sourceLabel: Label): Collection<ProjectTarget> {
    val targetOwners = getSourceFileOwners(sourceLabel).takeUnless { it.isEmpty() } ?: return emptyList()

    return Traverser.forGraph<Label> { this.getRdeps(it).map { it.label } }
      .breadthFirst(targetOwners)
      .asSequence()
      .mapNotNull { label -> storage.buildPackages[label.getPackageLabel()]?.targetMap?.get(label.name) }
      .toSet()
  }

  // TODO: b/397649793 - Remove this method when fixed.
  override fun dependsOnAnyOf_DO_NOT_USE_BROKEN(projectTarget: Label, deps: Set<Label>): Boolean {
    val projectTargetSingleton = listOf(projectTarget)
    val queue = ArrayDeque(projectTargetSingleton)
    val seen = HashSet<Label>(projectTargetSingleton)
    while (!queue.isEmpty()) {
      val target = queue.removeFirst()
      if (deps.contains(target)) {
        return true
      }
      val targetInfo = storage.buildPackages[target.getPackageLabel()]?.targetMap?.get(target.name) ?: continue
      queue.addAll(targetInfo.deps().filter { seen.add(it) })
    }
    return false
  }

  data class BuildPackageStorage(
    override val packageLabel: Label,
    override val sourceFileNames: Set<String> = emptySet(),
    val targetMap: Map<String, ProjectTarget> = emptyMap(),
    override val allSupportedTargetNames: Set<String> = emptySet(),
  ) : BuildGraphData.BuildPackage

  /** Build graph data in one place. */
  data class Storage(
    val buildPackages: Map<Label, BuildPackageStorage>,
    val projectDefinitionTargetPatterns: TargetPatternCollection,
    val alwaysBuildRules: Set<String>,
    val supportedBuildRules: Set<String>,
    val protoRules: BuildGraphData.ProtoRules,
  ) {

    /** Builder for [BuildGraphDataImpl]. */
    class Builder {
      private val buildPackagesBuilder = mutableMapOf<Label, BuildPackageBuilder>()

      class BuildPackageBuilder(val packageLabel: Label) {
        val sourceFileNames = mutableSetOf<String>()
        val targetMap = mutableMapOf<String, ProjectTarget>()
        val allSupportedTargetNames = mutableSetOf<String>()

        fun build(): BuildPackageStorage = BuildPackageStorage(packageLabel, sourceFileNames, targetMap, allSupportedTargetNames)
      }

      private fun getOrCreatePackageBuilder(packageLabel: Label): BuildPackageBuilder =
        buildPackagesBuilder.getOrPut(packageLabel) { BuildPackageBuilder(packageLabel) }

      fun addSourceFileLabel(label: Label): Builder {
        getOrCreatePackageBuilder(label.getPackageLabel()).sourceFileNames.add(label.name)
        return this
      }

      fun addTarget(label: Label, target: ProjectTarget): Builder {
        getOrCreatePackageBuilder(label.getPackageLabel()).targetMap.put(label.name, target)
        return this
      }

      fun addSupportedTargetLabel(label: Label): Builder {
        getOrCreatePackageBuilder(label.getPackageLabel()).allSupportedTargetNames.add(label.name)
        return this
      }

      fun build(
        projectDefinitionTargetPatterns: TargetPatternCollection,
        alwaysBuildRules: Set<String>,
        supportedBuildRules: Set<String>,
        protoRules: BuildGraphData.ProtoRules,
      ): BuildGraphDataImpl {
        val buildPackages = buildPackagesBuilder.mapValues { it.value.build() }
        val storage =
          Storage(
            buildPackages = buildPackages,
            projectDefinitionTargetPatterns = projectDefinitionTargetPatterns,
            alwaysBuildRules = alwaysBuildRules,
            supportedBuildRules = supportedBuildRules,
            protoRules = protoRules,
          )
        return BuildGraphDataImpl(storage)
      }
    }

    companion object {
      fun builder(): Builder {
        return Builder()
      }
    }
  }

  override fun getSourceFileOwners(label: Label): Set<Label> {
    return sourceOwners[label]?.toSet().orEmpty()
  }

  override fun getSourceFilesByRuleKindAndType(
    ruleKindPredicate: (String) -> Boolean,
    vararg sourceTypes: SourceType,
  ): Map<Label, List<Path>> {
    val allTargets = storage.buildPackages.values.flatMap { it.targetMap.values }
    return allTargets
      .asSequence()
      .filter { ruleKindPredicate(it.kind()) }
      .map { target ->
        target.label() to
          sourceTypes
            .flatMap { target.sourceLabels()[it] }
            .filter { depLabel ->
              val pkgStorage = storage.buildPackages[depLabel.getPackageLabel()]
              pkgStorage != null && pkgStorage.sourceFileNames.contains(depLabel.name)
            }
            .map { it.toFilePath() }
      }
      .filter { it.second.isNotEmpty() }
      .toMap()
  }

  /** Returns a list of custom_package fields that used by current project. */
  override fun getProjectTargetsForBuildPackage(packageLabel: Label): TargetsToBuild {
    val packagePath = packageLabel.getBuildPackagePath()
    return targetGroup(allSupportedTargets.getDirectTargets(packagePath).toList())
  }

  override fun getProjectTargetsForBuildPackageWithSubpackages(packageLabel: Label): TargetsToBuild {
    val packagePath = packageLabel.getBuildPackagePath()
    return targetGroup(allSupportedTargets.getSubpackages(packagePath).toList())
  }

  override fun getProjectTargetsForSourceFile(sourceFileLabel: Label): TargetsToBuild {
    val pkgStorage = storage.buildPackages[sourceFileLabel.getPackageLabel()]
    val exists = pkgStorage?.sourceFileNames?.contains(sourceFileLabel.name) ?: false
    if (!exists) {
      return forUnknownSourceFile(sourceFileLabel.toFilePath())
    }
    val targetOwner = getSourceFileOwners(sourceFileLabel)
    return when {
      targetOwner.isEmpty() -> TargetsToBuild.None
      else -> TargetsToBuild.forSourceFile(targetOwner, sourceFileLabel.toFilePath())
    }
  }

  override val projectSupportedTargetCountForStatsOnly: Int
    get() = allSupportedTargets.targetCountForStatsOnly

  override val targetMapSizeForStatsOnly: Int
    get() = storage.buildPackages.values.sumOf { it.targetMap.size }

  /**
   * Calculates the [RequestedTargets] for a project target.
   *
   * @return Requested targets. The [RequestedTargets.targetsToBuild] will match the parameter given.
   */
  /** Collects project targets that contribute */
  override fun collectTargetsToBuildForSourcesIn(
    projectTargets: Collection<Label>,
    replaceNativeTargetsWithAndroidTransitionTriggeringTargets: Boolean,
  ): Collection<Label> {
    return buildSet {
      val seenSources = mutableSetOf<Label>()
      projectTargets.forEach { targetLabel ->
        val target =
          getProjectTarget(targetLabel)
            ?: let {
              add(targetLabel)
              // Unknown target requested so let's just return it.
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

  override fun computeWholeProjectTargets(): Set<Label> {
    return computeSufficientTargets(
      allSupportedTargets.getTargets().filter { projectDefinitionTargetPatterns.inScope(it).status == INCLUDED }.toList(),
      replaceNativeTargetsWithAndroidTransitionTriggeringTargets = false, // storage.allSupportedTargets includes them anyway.
    )
  }

  override fun outputStats(context: Context<*>) {
    context.output(PrintOutput.log("%-10d Source files", storage.buildPackages.values.sumOf { it.sourceFileNames.size }))
    context.output(PrintOutput.log("%-10d Java sources", getJavaSourceFiles().size))
    context.output(PrintOutput.log("%-10d Packages", storage.buildPackages.size))
    context.output(PrintOutput.log("%-10d External dependencies", externalDependencyCountForStatsOnly))
  }

  override fun getActiveLanguages(): Set<QuerySyncLanguage> {
    return buildSet {
      if (allLoadedTargets().map { it.kind() }.any(RuleKinds::isJava)) {
        add(QuerySyncLanguage.JVM)
      }
      if (allLoadedTargets().map { it.kind() }.any(RuleKinds::isCc)) {
        add(QuerySyncLanguage.CC)
      }
    }
  }

  private fun getRdeps(target: Label): Collection<GraphNode> {
    return nodes[target]?.rdeps.orEmpty()
  }

  companion object {
    @JvmStatic
    fun builder(): Storage.Builder {
      return Storage.builder()
    }

    private fun computeSourceOwners(storage: Storage): Map<Label, List<Label>> {
      val allTargets = storage.buildPackages.values.asSequence().flatMap { it.targetMap.values }
      return allTargets
        .flatMap { target -> target.sourceLabels().values().asSequence().map { it to target.label() } }
        .groupBy({ it.first }, { it.second })
    }

    private fun computeAlwaysBuildTargets(storage: Storage): Set<Label> {
      val sourceOwners = computeSourceOwners(storage)
      val allTargets = storage.buildPackages.values.flatMap { it.targetMap.values }
      return allTargets
        .filter { target ->
          val sourceLabels = target.sourceLabels()
          return@filter (if (storage.supportedBuildRules.isEmpty()) target.kind() in storage.alwaysBuildRules
          else target.kind() !in storage.supportedBuildRules) ||
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
      for (pkgStorage in storage.buildPackages.values) {
        for ((name, target) in pkgStorage.targetMap) {
          val label = pkgStorage.packageLabel.siblingWithName(name)
          nodes[label] = GraphNodeImpl(label, ProjectNodeData(target))
        }
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

    private fun computeExternalDependencyCount(storage: Storage): Int {
      val allTargets = storage.buildPackages.values.flatMap { it.targetMap.values }
      return allTargets
        .asSequence()
        .flatMap { target ->
          target.deps().asSequence().filter { depLabel ->
            val pkgLabel = depLabel.getPackageLabel()
            val pkgStorage = storage.buildPackages[pkgLabel]
            pkgStorage == null || !pkgStorage.targetMap.containsKey(depLabel.name)
          }
        }
        .distinct()
        .count()
    }
  }

  override fun getProtoModes(label: Label): Set<BuildGraphData.ProtoMode> {
    return nodes[label]?.protoModes ?: emptySet()
  }
}

private val SUPPORTED_SOURCE_TYPES =
  setOf(SourceType.REGULAR_JVM, SourceType.REGULAR_CC, SourceType.REGULAR_PROTO, SourceType.ANDROID_RESOURCES, SourceType.ANDROID_MANIFEST)

/** Traverse the graph defined by [edgeSelector] and return a sequence of values produced by [valueEmitter]. */
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

/**
 * Returns all direct forward dependency labels for this project target, including compile dependencies, runtime dependencies, and special
 * test/library associations, used to construct bidirectional graph edges.
 */
private fun ProjectTarget.allDeps(): Sequence<Label> =
  (deps().asSequence() +
      runtimeDeps().asSequence() +
      listOfNotNull(testRule().getOrNull(), testApp().getOrNull(), instruments().getOrNull(), library().getOrNull()))
    .distinct()

// TODO: b/465698133 - find a way to move such configuration to _deps.bzl files.
private val ANDROID_TRANSITION_RULES = setOf("android_binary", "ndk_cc_dynamic_library_force_android_rule", "_android_binary")
