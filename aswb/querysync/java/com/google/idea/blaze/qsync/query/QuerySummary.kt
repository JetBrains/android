/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query

import com.google.idea.blaze.common.Label
import java.nio.file.Path
import org.jetbrains.annotations.TestOnly

/**
 * Summaries the output from a `query` invocation into just the data needed by the rest of querysync.
 *
 * The main purpose of the summarized output is to allow the outputs from multiple `query` invocations to be combined. This enables delta
 * updates to the project.
 */
interface QuerySummary {
  interface BuildPackage {
    val packageLabel: Label
    val sourceFilesMap: Map<Label, QueryData.SourceFile>
    val rulesMap: Map<Label, QueryData.Rule>
    val hasError: Boolean

    /**
     * The set of all `.bzl` files loaded by this build package.
     *
     * Note: This list is transitively expanded by Bazel query itself (meaning it contains all nested subincludes recursively loaded by any
     * of the direct `.bzl` files).
     */
    val subincludes: Set<Label>
  }

  val buildPackages: Collection<BuildPackage>

  fun getBuildPackage(packageLabel: Label): BuildPackage?

  /**
   * Returns the set of build packages in the query output.
   *
   * The packages are workspace relative paths that contain a BUILD file.
   */
  val packages: PackageSet

  /**
   * Returns a map of .bzl file labels to BUILD file labels that include them.
   *
   * Note: This map contains transitive/nested includes (meaning if BUILD loads a.bzl, and a.bzl loads b.bzl, both a.bzl and b.bzl are
   * mapped back to the BUILD file path recursively).
   */
  val reverseSubincludeMap: Map<Path, Collection<Path>>

  /**
   * Returns the set of labels of all files included from BUILD files.
   *
   * Note: This set is transitively expanded and includes all nested/transitive `.bzl` files recursively.
   */
  val allBuildIncludedFiles: Set<Label>
  /** Returns the list of packages that the query sync was unable to fetch or fetched with errors. */
  val packagesWithErrors: Set<Path>

  /** Returns the number of packages that the query sync was unable to fetch or fetched with errors. */
  val packagesWithErrorsCount: Int

  /** Returns the number of currently loaded rules in the project scope. */
  val rulesCount: Int

  /** Returns the configured query strategy. */
  val queryStrategy: QuerySpec.QueryStrategy

  val isCompatibleWithCurrentPluginVersion: Boolean

  fun protoForSerializationOnly(): Query.Summary

  companion object {
    @JvmField
    val EMPTY: QuerySummary =
      object : QuerySummary {
        override val isCompatibleWithCurrentPluginVersion: Boolean = true
        override val buildPackages: Collection<BuildPackage> = emptyList()

        override fun getBuildPackage(packageLabel: Label): BuildPackage? = null

        override val packages: PackageSet = PackageSet.EMPTY
        override val reverseSubincludeMap: Map<Path, Collection<Path>> = emptyMap()
        override val allBuildIncludedFiles: Set<Label> = emptySet()
        override val packagesWithErrors: Set<Path> = emptySet()
        override val packagesWithErrorsCount: Int = 0
        override val rulesCount: Int = 0
        override val queryStrategy: QuerySpec.QueryStrategy = QuerySpec.QueryStrategy.PLAIN

        override fun protoForSerializationOnly(): Query.Summary = Query.Summary.getDefaultInstance()
      }
  }
}

fun QuerySummary.getRule(label: Label): QueryData.Rule? {
  return getBuildPackage(label)?.rulesMap?.get(label)
}

fun QuerySummary.getSourceFile(label: Label): QueryData.SourceFile? {
  return getBuildPackage(label)?.sourceFilesMap?.get(label)
}

@get:TestOnly
val QuerySummary.sourceFilesMapForTests: Map<Label, QueryData.SourceFile>
  get() = buildPackages.asSequence().flatMap { it.sourceFilesMap.values }.associateBy { it.label }

@get:TestOnly
val QuerySummary.rulesMapForTests: Map<Label, QueryData.Rule>
  get() = buildPackages.asSequence().flatMap { it.rulesMap.values }.associateBy { it.label }
