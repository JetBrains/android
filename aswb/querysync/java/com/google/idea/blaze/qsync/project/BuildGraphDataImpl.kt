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
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.Queues
import com.google.common.graph.Traverser
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.RuleKinds
import com.google.idea.blaze.common.TargetTree
import com.google.idea.blaze.qsync.project.BuildGraphDataImpl.Location.Companion.Location
import com.google.idea.blaze.qsync.project.TargetsToBuild.Companion.forUnknownSourceFile
import com.google.idea.blaze.qsync.project.TargetsToBuild.Companion.targetGroup
import com.google.idea.blaze.qsync.query.PackageSet
import java.nio.file.Path
import java.util.Collections
import java.util.Queue
import java.util.function.Consumer
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
@JvmRecord
data class BuildGraphDataImpl(
  @VisibleForTesting @JvmField val storage: Storage,
  private val sourceOwners: Map<Label, List<Label>>,
  private val rdeps: ImmutableSetMultimap<Label, Label>,
  private val packages: PackageSet
) : BuildGraphData {

  override fun packages(): PackageSet = packages

  override fun getProjectTarget(label: Label): ProjectTarget? = storage.targetMap[label]
  override fun allSupportedTargets(): Collection<Label> = storage.allSupportedTargets.targets
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
      if (this.packages.contains(probe)) {
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
  override fun getSameLanguageTargetsDependingOn(targets: Set<Label>): ImmutableSet<Label> {
    val directRdeps = ImmutableSet.builder<Label>()
    directRdeps.addAll(targets)
    for (target in targets) {
      val targetLanguages = storage.targetMap[target]?.languages().orEmpty()
      // filter the rdeps based on the languages, removing those that don't have a common
      // language. This ensures we don't follow reverse deps of (e.g.) a java target depending on
      // a cc target.
      getRdeps(target)
        .filter {
          !Collections.disjoint(
            storage.targetMap[it]?.languages().orEmpty(),
            targetLanguages
          )
        }
        .forEach { directRdeps.add(it) }
    }
    return directRdeps.build()
  }

  /**
   * Calculates the first targets of a given set of rule types along any given dependency path for a
   * given source.
   */
  override fun getFirstReverseDepsOfType(sourcePath: Path, ruleKinds: Set<String>): Collection<ProjectTarget> {
    val targetOwners = getSourceFileOwners(sourcePath).takeUnless { it.isEmpty() } ?: return emptyList()
    val result =  mutableListOf<ProjectTarget>()

    val toVisit: Queue<Label> = Queues.newArrayDeque(targetOwners)
    val visited: MutableSet<Label> = HashSet()

    while (!toVisit.isEmpty()) {
      val next = toVisit.remove()
      if (visited.add(next)) {
        val target = storage.targetMap[next]
        if (target != null && ruleKinds.contains(target.kind())) {
          result.add(target)
        } else {
          toVisit.addAll(getRdeps(next))
        }
      }
    }
    return result
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

    return Traverser.forGraph<Label> { this.getRdeps(it) }.breadthFirst(targetOwners)
      .asSequence()
      .mapNotNull { storage.targetMap[it] }
      .toSet()
  }

  /**
   * Checks whether a given dependency path contains any of a specified set of rule kinds.
   *
   *
   * All dependency paths are considered starting at any target containing {@param sourcePath}
   * and going to any target containing {@param consumingSourcePath}. If any rule on one of these
   * paths is of a kind contained in {@param ruleKinds}, the method will return true.
   */
  override fun doesDependencyPathContainRules(
    sourcePath: Path, consumingSourcePath: Path, ruleKinds: Set<String>
  ): Boolean {
    val sourceTargets = getSourceFileOwners(sourcePath).takeUnless { it.isEmpty() } ?: return false
    val consumingTargetLabels = getSourceFileOwners(consumingSourcePath).takeUnless { it.isEmpty() } ?: return false
    val targetMap = storage.targetMap

    // Do a BFS up the dependency graph, looking both at the labels and the set of rule kinds
    // we've found so far at any given point.
    val toVisit: Queue<TargetSearchNode> =
      Queues.newArrayDeque(sourceTargets.map { TargetSearchNode(it, false) })
    val visited: MutableSet<TargetSearchNode> = HashSet()

    while (!toVisit.isEmpty()) {
      val current = toVisit.remove()
      if (visited.add(current)) {
        val currentLabel = current.targetLabel
        val currentLabelKind = targetMap[currentLabel]?.kind()

        val hasDesiredRule = current.hasDesiredRule || ruleKinds.contains(currentLabelKind)

        if (hasDesiredRule && consumingTargetLabels.contains(currentLabel)) {
          // We've found one of the consuming targets and the path here contained one of
          // the desired rule types, so we can terminate.
          return true
        } else {
          // Continue searching. Even if this is one of the consuming target labels, it's
          // possible that further up the dependency graph we'll run into a different one
          // of the consuming targets - and potentially have found one of the rules we
          // need along the way.
          for (nextTargetLabel in getRdeps(currentLabel)) {
            toVisit.add(TargetSearchNode(nextTargetLabel, hasDesiredRule))
          }
        }
      }
    }

    // We never found any of the desired rules.
    return false
  }

  // TODO: b/397649793 - Remove this method when fixed.
  override fun dependsOnAnyOf_DO_NOT_USE_BROKEN(
    projectTarget: Label,
    deps: Set<Label>
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

  private data class TargetSearchNode(val targetLabel: Label, val hasDesiredRule: Boolean)

  override fun getTargetSources(
    target: Label,
    vararg types: ProjectTarget.SourceType,
  ): Set<Path> {
    val sourceLabels = storage.targetMap[target]?.sourceLabels() ?: return emptySet()
    return types
      .flatMap { sourceLabels[it] }
      .filter { storage.sourceFileLabels.contains(it) }
      .map { it.toFilePath() }
      .toSet()
  }

  override fun toString(): String {
    return javaClass.getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this))
  }

  /**
   * Build graph data in one place.
   */
  data class Storage(
    val sourceFileLabels: Set<Label>,
    val targetMap: Map<Label, ProjectTarget>,
    val projectDeps: Set<Label>,
    val allSupportedTargets: TargetTree
  ) {
    constructor(
      sourceFileLabels: Set<Label>,
      targetMap: Map<Label, ProjectTarget>,
      projectDeps: Set<Label>,
      allSupportedTargetLabels: Set<Label>
    ) : this(sourceFileLabels, targetMap, projectDeps, TargetTree.create(allSupportedTargetLabels))

    /**
     * Builder for [BuildGraphDataImpl].
     */
    class Builder {
      private val sourceFileLabelsBuilder = ImmutableSet.builder<Label>()
      private val targetMapBuilder = ImmutableMap.builder<Label, ProjectTarget>()
      private val allTargetLabelsBuilder = ImmutableSet.builder<Label>()

      fun build(projectDeps: Set<Label>): BuildGraphDataImpl {
        val storage = Storage(sourceFileLabelsBuilder.build(), targetMapBuilder.build(), projectDeps, allTargetLabelsBuilder.build())
        return BuildGraphDataImpl(
          storage,
          computeSourceOwners(storage),
          computeRdeps(storage),
          computePackages(storage)
        )
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

      companion object {
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

        private fun computeRdeps(storage: Storage): ImmutableSetMultimap<Label, Label> {
          val rdeps = ImmutableSetMultimap.builder<Label, Label>()
          for (target in storage.targetMap.values) {
            for (rdep in target.deps()) {
              rdeps.put(rdep, target.label())
            }
            val testRule = target.testRule()
            testRule.ifPresent(Consumer { rdeps.put(it, target.label()) })
          }
          return rdeps.build()
        }

        private fun computePackages(storage: Storage): PackageSet {
          val packages = PackageSet.Builder()
          for (sourceFile in storage.sourceFileLabels) {
            if (sourceFile.name == Path.of("BUILD") || sourceFile.name == Path.of("BUILD.bazel")) {
              // TODO: b/334110669 - support Bazel workspaces.
              packages.add(sourceFile.getPackage())
            }
          }
          return packages.build()
        }
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
    return this.sourceOwners[label]?.toSet().orEmpty()
  }

  @Deprecated(
    """Choosing a target based on the number of deps it has is not a good strategy, as we
        could end up selecting one that doesn't build in the current config. Allow the user to
        choose, or require the projects source -> target mapping to be unambiguous instead."""
  )
  override fun selectLabelWithLeastDeps(candidates: Collection<Label>): Label? {
    return candidates.minBy { storage.targetMap[it]?.deps()?.size ?: Int.MAX_VALUE }
  }

  /** Returns a list of all the java source files of the project, relative to the workspace root.  */
  override fun getJavaSourceFiles(): List<Path> {
    return getSourceFilesByRuleKindAndType(RuleKinds::isJava, ProjectTarget.SourceType.REGULAR)
  }

  override fun getSourceFilesByRuleKindAndType(
    ruleKindPredicate: (String) -> Boolean, vararg sourceTypes: ProjectTarget.SourceType
  ): List<Path> {
    return pathListFromSourceFileLabelsOnly(sourcesByRuleKindAndType(ruleKindPredicate, *sourceTypes))
  }

  private fun sourcesByRuleKindAndType(
    ruleKindPredicate: (String) -> Boolean, vararg sourceTypes: ProjectTarget.SourceType,
  ): Set<Label> {
    return storage.targetMap.values.asSequence()
      .filter { ruleKindPredicate(it.kind()) }
      .map { it.sourceLabels() }
      .flatMap {
        sourceTypes.flatMap { type -> it[type].orEmpty() }
      }
      .toSet()
  }

  private fun pathListFromSourceFileLabelsOnly(labels: Collection<Label>): List<Path> {
    return labels
      .asSequence()
      .filter { storage.sourceFileLabels.contains(it) }
      .map { it.toFilePath() }
      .toList()
  }

  /**
   * Returns a list of regular (java/kt) source files owned by an Android target, relative to the
   * workspace root.
   */
  override fun getAndroidSourceFiles(): List<Path> =
    getSourceFilesByRuleKindAndType(RuleKinds::isAndroid, ProjectTarget.SourceType.REGULAR)

  override fun getAndroidResourceFiles(): List<Path> =
    getSourceFilesByRuleKindAndType(RuleKinds::isAndroid, ProjectTarget.SourceType.ANDROID_RESOURCES)

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
    context: Context<*>,
    workspaceRelativePath: Path
  ): TargetsToBuild {
    // TODO: relativize here.
    // TODO: support Bazel.
    if (workspaceRelativePath.endsWith("BUILD")) {
      val packagePath = workspaceRelativePath.parent
      return targetGroup(storage.allSupportedTargets.getDirectTargets(packagePath).orEmpty())
    } else {
      val targets = storage.allSupportedTargets.getSubpackages(workspaceRelativePath)
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
      .asSequence()
      .mapNotNull { storage.targetMap[it] }
      .flatMap { it.languages() }
      .toSet()
  }

  /**
   * Traverses the dependency graph starting from `projectTargets` and returns the first level
   * of dependencies which are either not in the project scope or must be built as they are not
   * directly supported by the IDE.
   */
  override fun getExternalDependencies(projectTargets: Collection<Label>): Set<Label> {
    val externalDeps = mutableSetOf<Label>()
    val seen = HashSet<Label>(projectTargets)
    val queue = ArrayDeque(projectTargets)
    while (!queue.isEmpty()) {
      val target = queue.removeFirst()
      val targetInfo = storage.targetMap[target]
      if (targetInfo == null || this.storage.projectDeps.contains(target)) {
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

  override val externalDependencyCount: Int
    get() = storage.projectDeps.size

  override val projectSupportedTargetCountForStatsOnly: Int
    get() = allSupportedTargets().size

  override val targetMapSizeForStatsOnly: Int
    get() = storage.targetMap.size


  /**
   * Calculates the [RequestedTargets] for a project target.
   *
   * @return Requested targets. The [RequestedTargets.buildTargets] will match the parameter
   * given; the [RequestedTargets.expectedDependencyTargets] will be determined by the
   * [.getDependencyTrackingIncludeExternalDependencies] of the targets
   * given.
   */
  override fun computeRequestedTargets(projectTargets: Collection<Label>): RequestedTargets {
    val filteredProjectTargets = filterRedundantTargets(projectTargets)
    val externalDeps = getExternalDependencies(filteredProjectTargets)
    return RequestedTargets(filteredProjectTargets, externalDeps)
  }

  override fun outputStats(context: Context<*>) {
    context.output(
      PrintOutput.log(
        "%-10d Source files",
        storage.sourceFileLabels.size
      )
    )
    context.output(PrintOutput.log("%-10d Java sources", getJavaSourceFiles().size))
    context.output(PrintOutput.log("%-10d Packages", this.packages.size()))
    context.output(
      PrintOutput.log(
        "%-10d External dependencies",
        storage.projectDeps.size
      )
    )
  }

  override fun getActiveLanguages(): Set<QuerySyncLanguage> {
    val activeLanguages = ImmutableSet.builder<QuerySyncLanguage>()
    if (storage.targetMap.values.asSequence()
        .map { it.kind() }
        .any(RuleKinds::isJava)
    ) {
      activeLanguages.add(QuerySyncLanguage.JVM)
    }
    if (storage.targetMap.values.asSequence()
        .map { it.kind() }
        .any(RuleKinds::isCc)
    ) {
      activeLanguages.add(QuerySyncLanguage.CC)
    }
    return activeLanguages.build()
  }

  private fun getRdeps(target: Label): Set<Label> {
    return rdeps[target].orEmpty()
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
      return Storage.Companion.builder()
    }
  }
}
