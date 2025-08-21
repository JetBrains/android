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

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.common.Label
import java.nio.file.Path

/**
 * Summaries the output from a `query` invocation into just the data needed by the rest of
 * querysync.
 *
 *
 * The main purpose of the summarized output is to allow the outputs from multiple `query`
 * invocations to be combined. This enables delta updates to the project.
 */
interface QuerySummary {
  /**
   * Returns the map of source files included in the query output.
   *
   *
   * This is a map of source target label to the [QueryData.SourceFile] proto representing it.
   */
  val sourceFilesMap: Map<Label, QueryData.SourceFile>

  /**
   * Returns the map of rules included in the query output.
   *
   *
   * This is a map of rule label to the [QueryData.Rule] proto representing it.
   */
  val rulesMap: Map<Label, QueryData.Rule>

  /**
   * Returns the set of build packages in the query output.
   *
   *
   * The packages are workspace relative paths that contain a BUILD file.
   */
  val packages: PackageSet

  /**
   * Returns a map of .bzl file labels to BUILD file labels that include them.
   *
   *
   * This is used to determine, for example, which build files include a given .bzl file.
   */
  val reverseSubincludeMap: Map<Path, Collection<Path>>

  /**
   * Returns the set of labels of all files includes from BUILD files.
   */
  val allBuildIncludedFiles: Set<Label>
  /**
   * Returns the list of packages that the query sync was unable to fetch or fetched with errors.
   */
  val packagesWithErrors: Set<Path>

  /**
   * Returns the number of packages that the query sync was unable to fetch or fetched with errors.
   */
  val packagesWithErrorsCount: Int

  /**
   * Returns the number of currently loaded rules in the project scope.
   */
  val rulesCount: Int

  /**
   * Returns the configured query strategy.
   */
  val queryStrategy: QuerySpec.QueryStrategy

  val isCompatibleWithCurrentPluginVersion: Boolean

  fun protoForSerializationOnly(): Query.Summary

  companion object {
    @JvmField
    val EMPTY: QuerySummary = object : QuerySummary {
      override val isCompatibleWithCurrentPluginVersion: Boolean = true
      override val sourceFilesMap: Map<Label, QueryData.SourceFile> = emptyMap()
      override val rulesMap: Map<Label, QueryData.Rule> = emptyMap()
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
