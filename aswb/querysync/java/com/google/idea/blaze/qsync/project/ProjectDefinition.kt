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
package com.google.idea.blaze.qsync.project

import com.google.auto.value.AutoValue
import com.google.auto.value.extension.memoized.Memoized
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ListMultimap
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.TargetPattern
import com.google.idea.blaze.common.TargetPatternCollection
import com.google.idea.blaze.qsync.query.QuerySpec
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.function.Consumer
import kotlin.streams.asSequence

/**
 * Represents input the the query sync process. This class contains data that is derived from the
 * user project config and is constructed before the sync begins.
 */
data class ProjectDefinition(
  /**
   * Project includes, also know as root directories. Taken from the users `.blazeproject`
   * file. Paths are relative to the workspace root.
   */
  val projectIncludes: Set<Path>,
  /**
   * Project excludes. Taken from the users `.blazeproject` file. Paths are relative to the
   * workspace root, and indicate sub-paths from within [.projectIncludes] that are not part
   * of the project.
   */
  val projectExcludes: Set<Path>,


  /**
   * If set to true, the main scope of the project includes all targets within the whole-project scope, unless modified by `targets:`
   * exclusions.
   */
  val deriveTargetsFromDirectories: Boolean,

  /**
   * Target patterns in the main project scope, where the main project scope is a scope to which many query sync operations apply
   * by default. The empty list means all targets are included.
   */
  val targetPatterns: List<TargetPattern>,

  /**
   * Indicates whether Android support should be activated in the IDE.
   */
  val isAndroidWorkspace: Boolean,

  /**
   * The languages this workspace supports.
    */
  val languageClasses: Set<QuerySyncLanguage>,

  /**
   * Test sources. Taken from the user's `.blazeproject` file. Paths are relative to the
   * workspace root, and indicate directories that are considered test sources.
   */
  val testSources: Set<String>,

  /**
   * System Excludes. Only available for Bazel projects to avoid scanning the system directories
   * like bazel-bin, bazel-out, ... for BUILD files before ignoring them in the query invocation.
   */
  val systemExcludes: Set<Path>,
) {

  val effectiveTargetPatterns: TargetPatternCollection by lazy (LazyThreadSafetyMode.PUBLICATION) {
    TargetPatternCollection.create(
      if (deriveTargetsFromDirectories) listOf(TargetPattern.parse("//...")) + targetPatterns else targetPatterns
    )
  }

  /**
   * Constructs a query spec from a sync spec. Filters the import roots to those that can be safely
   * queried.
   */
  fun deriveQuerySpec(
    context: Context<*>, queryStrategy: QuerySpec.QueryStrategy, workspaceRoot: Path
  ): QuerySpec.Builder {
    val result = QuerySpec.builder(queryStrategy)
    for (include in projectIncludes) {
      if (isValidPathForQuery(context, workspaceRoot.resolve(include))) {
        result.includePath(include)
      }
    }
    for (exclude in projectExcludes) {
      if (systemExcludes.contains(exclude)) {
        // We don't have to check if these directories are valid for queries
        continue
      }
      if (isValidPathForQuery(context, workspaceRoot.resolve(exclude))) {
        result.excludePath(exclude)
      }
    }
    return result
  }

  /** Returns the exclude paths by the include path that they fall within.  */
  val excludesByRootDirectory: Map<Path, List<Path>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
      projectExcludes
        .mapNotNull { exclude ->
          val rootInclude = projectIncludes.firstOrNull { rootDirectory -> isUnderRootDirectory(rootDirectory, exclude) }
                            ?: return@mapNotNull null
          exclude to rootInclude
        }
        .groupBy({ it.second }, { it.first })
    }

  fun isIncluded(target: Label): Boolean {
    return isIncluded(target.getBuildPackagePath())
  }

  fun isIncluded(workspacePath: Path): Boolean {
    return getIncludingContentRoot(workspacePath) != null
  }

  fun isExcluded(workspacePath: Path): Boolean {
    return projectExcludes.any { workspacePath.startsWith(it) }
  }

  /**
   * Returns the content root containing a workspace-relative path
   *
   * @param workspacePath [Path] relative to the workspace
   * @return [<] of the content root that contains `workspacePath`. Returns
   * an empty Optional if no content entry contains `workspacePath` or if `workspacePath` is contained in an excluded directory.
   */
  fun getIncludingContentRoot(workspacePath: Path): Path? {
    val contentRoot =
      projectIncludes.firstOrNull { isUnderRootDirectory(it, workspacePath) } ?: return null

    if (isExcluded(workspacePath)) {
      // Path is excluded
      return null
    }

    return contentRoot
  }

  companion object {
    @JvmField
    val EMPTY: ProjectDefinition = ProjectDefinition(
      projectIncludes = emptySet(),
      projectExcludes = emptySet(),
      deriveTargetsFromDirectories = false,
      targetPatterns = emptyList(),
      isAndroidWorkspace = false,
      languageClasses = emptySet(),
      testSources = emptySet(),
      systemExcludes = emptySet()
    )

    /**
     * Determines if a given absolute path is a valid path to query. A path is valid if it contains a
     * BUILD file somewhere within it.
     *
     *
     * Emits warnings via context if any issues are found with the path.
     */
    private fun isValidPathForQuery(context: Context<*>, candidate: Path): Boolean {
      return when {
        Files.exists(candidate.resolve("BUILD")) ||
        Files.exists(candidate.resolve("BUILD.bazel")) -> true

        !Files.isDirectory(candidate) -> {
          context.output(
            PrintOutput.output("Directory specified in project does not exist or is not a directory: $candidate")
          )
          false
        }

        else -> {
          var valid = false
          try {
            Files.list(candidate).use { stream ->
              for (child in stream.asSequence()) {
                if (Files.isDirectory(child)) {
                  val validChild: Boolean = isValidPathForQuery(context, child)
                  valid = valid || validChild
                } else {
                  if (child.toString().endsWith(".java") || child.toString().endsWith(".kt")) {
                    context.output(
                      PrintOutput.log("WARNING: Sources found outside BUILD packages: $child")
                    )
                  }
                }
              }
            }
            valid
          } catch (ex: IOException) {
            context.output(PrintOutput.error("Failed to list content of $candidate due to ${ex.message}"))
            false
          }
        }
      }
    }

    private fun isUnderRootDirectory(rootDirectory: Path, relativePath: Path): Boolean {
      // TODO this can probably be cleaned up (or removed?) by using Path API properly.
      if (rootDirectory.toString() == "." || rootDirectory.toString().isEmpty()) {
        return true
      }
      val rootDirectoryString = rootDirectory.toString()
      return relativePath.startsWith(rootDirectoryString)
        && (relativePath.toString().length == rootDirectoryString.length
        || (relativePath.toString()[rootDirectoryString.length] == '/'))
    }
  }
}
