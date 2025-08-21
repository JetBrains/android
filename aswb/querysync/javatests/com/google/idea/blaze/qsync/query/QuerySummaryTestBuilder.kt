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
package com.google.idea.blaze.qsync.query

import com.google.common.collect.ImmutableList
import com.google.common.collect.Multimap
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.idea.blaze.common.Label
import java.nio.file.Path

class QuerySummaryTestBuilder {
  private val packagesBuilder = mutableListOf<Label>()
  private val buildFilesWithErrorsBuilder = mutableListOf<Label>()
  private val includesBuilder = mutableMapOf<Label, MutableList<Label>>()

  @CanIgnoreReturnValue
  fun addPackages(vararg packages: String): QuerySummaryTestBuilder {
    packages.forEach { packagesBuilder.add(Label.of(it)) }
    return this
  }

  @CanIgnoreReturnValue
  fun addSubincludes(subIncludes: Multimap<Label, Label>): QuerySummaryTestBuilder {
    subIncludes.forEach { k, v -> includesBuilder.getOrPut(k) { mutableListOf() }.add(v) }
    return this
  }

  @CanIgnoreReturnValue
  fun addBuildFileLabelsWithErrors(vararg buildFilesWithErrors: String): QuerySummaryTestBuilder {
    buildFilesWithErrors.forEach { buildFilesWithErrorsBuilder.add(Label.of(it)) }
    return this
  }

  fun build(): Query.Summary {
    val sourceFiles: MutableSet<Label> =
      packagesBuilder
        .map { it.getBuildPackagePath() }
        .map {
          Label.fromWorkspacePackageAndName(
            Label.ROOT_WORKSPACE,
            it,
            Path.of("BUILD")
          )
        }
        .toMutableSet()
    sourceFiles.addAll(includesBuilder.keys)

    val builder = QuerySummaryImpl.newBuilder()
    builder.putAllRules(
      packagesBuilder
        .filter { !buildFilesWithErrorsBuilder.contains(it.siblingWithName("BUILD")) }
        .map {
          QueryData.Rule.builderForTests()
            .label(it)
            .ruleClass("java_library")
            .build()
        }
    )

    builder.putAllSourceFiles(
      sourceFiles.associateWith {
        QueryData.SourceFile(
          it,
          ImmutableList.copyOf(includesBuilder.get(it).orEmpty())
        )
      }
    )

    builder.putAllPackagesWithErrors(
      buildFilesWithErrorsBuilder.map { it.getBuildPackagePath() }.toSet()
    )

    return builder.build().protoForSerializationOnly()
  }
}
