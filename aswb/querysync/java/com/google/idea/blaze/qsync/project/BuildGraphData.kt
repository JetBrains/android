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
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.RuleKinds
import com.google.idea.blaze.common.TargetPatternCollection
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull

interface BuildGraphData {
  /** The language classes supported by the query sync. */
  enum class LanguageClass {
    JVM,
    CC,
  }

  data class ProtoRules(val fullModeRuleNames: Set<String>, val liteModeRuleNames: Set<String>) {
    companion object {
      @VisibleForTesting @JvmStatic fun forTests(): ProtoRules = ProtoRules(setOf("java_proto_library"), setOf("java_lite_proto_library"))
    }
  }

  /** A protobuf runtime variant. */
  enum class ProtoMode {
    FULL,
    LITE,
  }

  /**
   * The protobuf runtime modes the target is used in.
   *
   * For java targets it is the variant of the runtime it depends on and for proto_library-like targets it is the set of modes of tergets
   * that depend on it.
   */
  fun getProtoModes(label: Label): Set<ProtoMode>

  interface BuildPackage

  /** A set of all the BUILD files */
  fun getBuildPackage(packageLabel: Label): BuildPackage?

  /**
   * If the given path represents a currently known source file returns a [Label] representing the given path in the workspace with the
   * current build packages.
   */
  fun sourceFileToLabel(sourceFile: Path): Label?

  /**
   * All loaded targets. This is a superset of all supported targets and should be used for code completion only.
   *
   * Note, this is not the full list of of all targets in the project view.
   */
  fun allLoadedTargets(): Sequence<ProjectTarget>

  /** Returns the project target info for the given label, if it is supported and built (code analysis enabled). */
  fun getProjectTarget(label: Label): ProjectTarget?

  /** Calculates the set of direct reverse dependencies for a set of targets (including the targets themselves). */
  fun getSameLanguageTargetsDependingOn(targets: Set<Label>): Set<Label>

  /**
   * Returns all in project targets that depend on the source file at `sourcePath` via an in-project dependency chain. Used to determine
   * possible test targets for a given file.
   *
   * If project target A depends on external target B, and external target B depends on project target C, target A is *not* included in
   * `getReverseDeps` for a source file in target C.
   */
  fun getReverseDepsForSource(sourceLabel: Label): Collection<ProjectTarget>

  // TODO: b/397649793 - Remove this method when fixed.
  fun dependsOnAnyOf_DO_NOT_USE_BROKEN(projectTarget: Label, deps: Set<Label>): Boolean

  fun getSourceFileOwners(label: Label): Set<Label>

  /** Returns targets matching the given predicate that have any source files of the given types. */
  fun getSourceFilesByRuleKindAndType(
    ruleKindPredicate: (String) -> Boolean,
    vararg sourceTypes: ProjectTarget.SourceType,
  ): Map<Label, List<Path>>

  /**
   * Returns the list of project targets related to the given workspace file.
   *
   * @param workspaceRelativePath Workspace relative file path to find targets for. This may be a source file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that file. For a BUILD file, it's the set or
   *   targets defined in that file. For a directory, it's the set of all targets defined in all build packages within the directory
   *   (recursively).
   */
  fun getProjectTargets(workspaceRelativePath: Path): TargetsToBuild

  /** Calculates a sufficient set of targets to build for the given project targets. */
  fun computeSufficientTargets(
    projectTargets: Collection<Label>,
    replaceNativeTargetsWithAndroidTransitionTriggeringTargets: Boolean,
  ): Set<Label>

  /** Calculates a sufficient set of targets to build for the whole project. */
  fun computeWholeProjectTargets(): Set<Label>

  /** Output stats about the the project to the context (and thus normally to the console). */
  fun outputStats(context: Context<*>)

  /** Returns the number of external dependencies of the project for the purpose of stats reporting. */
  val externalDependencyCountForStatsOnly: Int

  /** Returns the number of supported targets of the project for the purpose of stats reporting. */
  val projectSupportedTargetCountForStatsOnly: Int

  /** Returns an approximate size of the project's target map for the purpose of stats reporting. */
  val targetMapSizeForStatsOnly: Int

  /** Returns the language classes for which code analysis is currently enabled in this project. */
  fun getActiveLanguages(): Set<QuerySyncLanguage>

  fun isAlwaysBuild(label: Label): Boolean

  companion object {
    @JvmField
    val EMPTY: BuildGraphData =
      BuildGraphDataImpl.builder()
        .build(
          projectDefinitionTargetPatterns = TargetPatternCollection.create(emptyList()),
          alwaysBuildRules = emptySet(),
          supportedBuildRules = emptySet(),
          protoRules = ProtoRules(emptySet(), emptySet()),
        )
  }
}

fun BuildGraphData.getJavaSourceFiles(): List<Path> {
  return getSourceFilesByRuleKindAndType(RuleKinds::isJava, ProjectTarget.SourceType.REGULAR_JVM).values.flatten()
}

fun BuildGraphData.getAndroidResourceFiles(): List<Path> {
  return getSourceFilesByRuleKindAndType(RuleKinds::isAndroid, ProjectTarget.SourceType.ANDROID_RESOURCES).values.flatten()
}

fun BuildGraphData.getAllCustomPackages(): Set<String> {
  return allLoadedTargets().mapNotNull { it.customPackage().getOrNull() }.toSet()
}

fun BuildGraphData.getBuildPackage(path: Path): BuildGraphData.BuildPackage? {
  return this.getBuildPackage(Label.fromWorkspacePackageAndName("", path, Label.PACKAGE_TARGET_NAME))
}
