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
package com.google.idea.blaze.qsync

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import com.google.common.truth.Truth8
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.query.Query
import com.google.idea.blaze.qsync.query.QueryData
import com.google.idea.blaze.qsync.query.QuerySummary
import com.google.idea.blaze.qsync.query.QuerySummaryImpl
import java.nio.file.Path
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PartialProjectRefreshTest {
  @Test
  fun testApplyDelta_replacePackage() {
    val base =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package1:rule")).copy(
            ruleClass = "java_library",
            sources = listOf(Label.of("//my/build/package1:Class1.java"))
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package1:Class1.java"), listOf())
        )
        .putSourceFiles(
          QueryData.SourceFile(
            Label.of("//my/build/package1:subpackage/AnotherClass.java"),
            listOf()
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package1:BUILD"), listOf())
        )
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package2:rule")).copy(
            ruleClass = "java_library",
            sources = listOf(Label.of("//my/build/package2:Class2.java"))
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package2:Class2.java"), listOf())
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package2:BUILD"), listOf())
        )
        .build()
    val baseProject =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()

    val delta =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package1:newrule")).copy(
            ruleClass = "java_library",
            sources = listOf(Label.of("//my/build/package1:NewClass.java"))
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package1:NewClass.java"), listOf())
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package1:BUILD"), listOf())
        )
        .build()

    val queryStrategy = PartialProjectRefresh(
      Path.of("/workspace/root"),
      baseProject,
      QuerySyncTestUtils.CLEAN_VCS_STATE,
      Optional.empty(),  /* modifiedPackages= */
      ImmutableSet.of(Path.of("my/build/package1")),
      ImmutableSet.of()
    )
    val applied = queryStrategy.applyDelta(delta)
    Truth.assertThat(applied.rulesMap.keys)
      .containsExactly(
        Label.of("//my/build/package1:newrule"), Label.of("//my/build/package2:rule")
      )
    Truth.assertThat(applied.sourceFilesMap.keys)
      .containsExactly(
        Label.of("//my/build/package1:NewClass.java"),
        Label.of("//my/build/package1:BUILD"),
        Label.of("//my/build/package2:Class2.java"),
        Label.of("//my/build/package2:BUILD")
      )
  }

  @Test
  fun testApplyDelta_deletePackage() {
    val base =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package1:rule")).copy(
            ruleClass = "java_library",
            sources = listOf(Label.of("//my/build/package1:Class1.java"))
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package1:Class1.java"), listOf())
        )
        .putSourceFiles(
          QueryData.SourceFile(
            Label.of("//my/build/package1:subpackage/AnotherClass.java"),
            listOf()
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package1:BUILD"), listOf())
        )
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package2:rule")).copy(
            ruleClass = "java_library",
            sources = listOf(Label.of("//my/build/package2:Class2.java"))
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package2:Class2.java"), listOf())
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package2:BUILD"), listOf())
        )
        .build()
    val baseProject =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()

    val queryStrategy = PartialProjectRefresh(
      Path.of("/workspace/root"),
      baseProject,
      QuerySyncTestUtils.CLEAN_VCS_STATE,
      Optional.empty(),
      ImmutableSet.of(),  /* deletedPackages= */
      ImmutableSet.of(Path.of("my/build/package1"))
    )
    Truth8.assertThat(queryStrategy.getQuerySpec()).isEmpty()
    val applied = queryStrategy.applyDelta(QuerySummary.EMPTY)
    Truth.assertThat(applied.rulesMap.keys)
      .containsExactly(Label.of("//my/build/package2:rule"))
    Truth.assertThat(applied.sourceFilesMap.keys)
      .containsExactly(
        Label.of("//my/build/package2:Class2.java"), Label.of("//my/build/package2:BUILD")
      )
  }

  @Test
  fun testDelta_addPackage() {
    val base =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package1:rule")).copy(
            ruleClass = "java_library",
            sources = listOf(Label.of("//my/build/package1:Class1.java"))
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package1:Class1.java"), listOf())
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package1:BUILD"), listOf())
        )
        .build()
    val baseProject =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()
    val delta =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package2:rule")).copy(
            ruleClass = "java_library",
            sources = listOf(Label.of("//my/build/package2:Class2.java"))
          )
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package2:Class2.java"), listOf())
        )
        .putSourceFiles(
          QueryData.SourceFile(Label.of("//my/build/package2:BUILD"), listOf())
        )
        .build()

    val queryStrategy = PartialProjectRefresh(
      Path.of("/workspace/root"),
      baseProject,
      QuerySyncTestUtils.CLEAN_VCS_STATE,
      Optional.empty(),  /* modifiedPackages= */
      ImmutableSet.of(Path.of("my/build/package2")),
      ImmutableSet.of()
    )
    val applied = queryStrategy.applyDelta(delta)
    Truth.assertThat(applied.rulesMap.keys)
      .containsExactly(
        Label.of("//my/build/package1:rule"), Label.of("//my/build/package2:rule")
      )
    Truth.assertThat(applied.sourceFilesMap.keys)
      .containsExactly(
        Label.of("//my/build/package1:Class1.java"),
        Label.of("//my/build/package1:BUILD"),
        Label.of("//my/build/package2:Class2.java"),
        Label.of("//my/build/package2:BUILD")
      )
  }

  @Test
  fun testDelta_packagesWithErrors() {
    val base =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder().addPackagesWithErrors("//my/build/package:BUILD").build()
      )
    val baseProject =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()
    val delta =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder().addPackagesWithErrors("//my/build/package:BUILD").build()
      )

    val queryStrategy = PartialProjectRefresh(
      Path.of("/workspace/root"),
      baseProject,
      QuerySyncTestUtils.CLEAN_VCS_STATE,
      Optional.empty(),  /* modifiedPackages= */
      ImmutableSet.of(Path.of("my/build/package")),
      ImmutableSet.of()
    )
    val applied = queryStrategy.applyDelta(delta)
    Truth.assertThat(applied.packagesWithErrors).containsExactly(Path.of("my/build/package"))
  }

  private fun <T> listOf(vararg list: T): ImmutableList<T> = ImmutableList.copyOf(list)
}
