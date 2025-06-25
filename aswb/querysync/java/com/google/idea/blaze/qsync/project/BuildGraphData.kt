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
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSetMultimap
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.BuildGraphDataImpl.Companion.builder
import com.google.idea.blaze.qsync.query.PackageSet
import java.nio.file.Path
import java.util.Optional

interface BuildGraphData {
  /**
   * The language classes supported by the query sync.
   */
  enum class LanguageClass {
    JVM, CC
  }

  /** A set of all the BUILD files  */
  fun packages(): PackageSet

  /**
   * Returns a [Label] representing the given path in the workspace with the current build packages. The file does not need to exist.
   */
  @VisibleForTesting
  fun pathToLabel(file: Path): Label?

  /**
   * If the given path represents a currently known source file returns a [Label] representing the given path in the workspace with
   * the current build packages.
   */
  fun sourceFileToLabel(sourceFile: Path): Label?

  /**
   * All loaded targets. This is a superset of all supported targets and should be used for code completion only.
   *
   * Note, this is not the full list of of all targets in the project view.
   */
  fun allLoadedTargets(): Collection<Label>

  /**
   * Returns the project target info for the given label, if it is supported and built (code analysis enabled).
   */
  fun getProjectTarget(label: Label): ProjectTarget?

  /**
   * Calculates the set of direct reverse dependencies for a set of targets (including the targets
   * themselves).
   */
  fun getSameLanguageTargetsDependingOn(targets: Set<Label>): ImmutableSet<Label>

  /**
   * Calculates the first targets of a given set of rule types along any given dependency path for a
   * given source.
   */
  fun getFirstReverseDepsOfType(
    sourcePath: Path,
    ruleKinds: Set<String>
  ): Collection<ProjectTarget>

  /**
   * Returns all in project targets that depend on the source file at `sourcePath` via an
   * in-project dependency chain. Used to determine possible test targets for a given file.
   *
   *
   * If project target A depends on external target B, and external target B depends on project
   * target C, target A is *not* included in `getReverseDeps` for a source file in target C.
   */
  fun getReverseDepsForSource(sourcePath: Path): Collection<ProjectTarget>

  /**
   * Checks whether a given dependency path contains any of a specified set of rule kinds.
   *
   *
   * All dependency paths are considered starting at any target containing {@param sourcePath}
   * and going to any target containing {@param consumingSourcePath}. If any rule on one of these
   * paths is of a kind contained in {@param ruleKinds}, the method will return true.
   */
  fun doesDependencyPathContainRules(
    sourcePath: Path,
    consumingSourcePath: Path,
    ruleKinds: Set<String>
  ): Boolean

  // TODO: b/397649793 - Remove this method when fixed.
  fun dependsOnAnyOf_DO_NOT_USE_BROKEN(projectTarget: Label, deps: Set<Label>): Boolean

  fun getTargetSources(
    target: Label,
    vararg types: ProjectTarget.SourceType
  ): Set<Path>

  fun getSourceFileOwners(path: Path): Set<Label>

  fun getSourceFileOwners(label: Label): Set<Label>

  @Deprecated(
    """Choosing a target based on the number of deps it has is not a good strategy, as we
        could end up selecting one that doesn't build in the current config. Allow the user to
        choose, or require the projects source -> target mapping to be unambiguous instead."""
  )
  fun selectLabelWithLeastDeps(candidates: Collection<Label>): Label?

  /** Returns a list of all the java source files of the project, relative to the workspace root.  */
  fun getJavaSourceFiles(): List<Path>

  fun getSourceFilesByRuleKindAndType(
    ruleKindPredicate: (String) -> Boolean, vararg sourceTypes: ProjectTarget.SourceType
  ): List<Path>

  /**
   * Returns a list of regular (java/kt) source files owned by an Android target, relative to the
   * workspace root.
   */
  fun getAndroidSourceFiles(): List<Path>

  fun getAndroidResourceFiles(): List<Path>

  /** Returns a list of custom_package fields that used by current project.  */
  fun getAllCustomPackages(): Set<String>

  /**
   * Returns the list of project targets related to the given workspace file.
   *
   * @param workspaceRelativePath Workspace relative file path to find targets for. This may be a
   * source file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that
   * file. For a BUILD file, it's the set or targets defined in that file. For a directory, it's
   * the set of all targets defined in all build packages within the directory (recursively).
   */
  fun getProjectTargets(workspaceRelativePath: Path): TargetsToBuild

  /**
   * Returns the set of [target languages][ProjectTarget.languages] for a set of project
   * targets.
   */
  fun getTargetLanguages(targets: Set<Label>): Set<QuerySyncLanguage>

  /**
   * Traverses the dependency graph starting from `projectTargets` and returns the first level of dependencies which are either not in
   * the project scope or must be built as they are not directly supported by the IDE.
   */
  fun getExternalDependencies(projectTargets: Collection<Label>): Set<Label>

  /**
   * Calculates the [RequestedTargets] for a project target.
   */
  fun computeRequestedTargets(projectTargets: Collection<Label>): RequestedTargets

  /**
   * Calculates the [RequestedTargets] for the whole project.
   */
  fun computeWholeProjectTargets(projectDefinition: ProjectDefinition): RequestedTargets

  /** Output stats about the the project to the context (and thus normally to the console).  */
  fun outputStats(context: Context<*>)

  /**
   * Returns the number of external dependencies of the project for the purpose of stats reporting.
   */
  val externalDependencyCount: Int

  /**
   * Returns the number of supported targets of the project for the purpose of stats reporting.
   */
  val projectSupportedTargetCountForStatsOnly: Int

  /**
   * Returns an approximate size of the project's target map for the purpose of stats reporting.
   */
  val targetMapSizeForStatsOnly: Int

  /** Returns the language classes for which code analysis is currently enabled in this project.  */
  fun getActiveLanguages(): Set<QuerySyncLanguage>

  companion object {
    @JvmField
    val EMPTY: BuildGraphData = BuildGraphDataImpl.builder().build(ImmutableSet.of())
  }
}
