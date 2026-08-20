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
import com.google.idea.blaze.qsync.project.BuildPackage
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.ProjectStructureRoot
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.query.Query
import com.google.idea.blaze.qsync.query.QueryData
import com.google.idea.blaze.qsync.query.QuerySummary
import com.google.idea.blaze.qsync.query.QuerySummaryImpl
import com.google.idea.blaze.qsync.query.rulesMapForTests
import com.google.idea.blaze.qsync.query.sourceFilesMapForTests
import java.nio.file.Path
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
          QueryData.Rule.createForTests(label = Label.of("//my/build/package1:rule"))
            .copy(ruleClass = "java_library", sources = listOf(Label.of("//my/build/package1:Class1.java")))
        )
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:Class1.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:subpackage/AnotherClass.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:BUILD"), listOf()))
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package2:rule"))
            .copy(ruleClass = "java_library", sources = listOf(Label.of("//my/build/package2:Class2.java")))
        )
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package2:Class2.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package2:BUILD"), listOf()))
        .build()
    val baseProject = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()

    val delta =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package1:newrule"))
            .copy(ruleClass = "java_library", sources = listOf(Label.of("//my/build/package1:NewClass.java")))
        )
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:NewClass.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:BUILD"), listOf()))
        .build()

    val refresh =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        /* modifiedPackages= */ ImmutableSet.of(Path.of("my/build/package1")),
        ImmutableSet.of(),
        ProjectStructureData.EMPTY,
      )
    val applied = refresh.applyDelta(delta)
    Truth.assertThat(applied.rulesMapForTests.keys)
      .containsExactly(Label.of("//my/build/package1:newrule"), Label.of("//my/build/package2:rule"))
    Truth.assertThat(applied.sourceFilesMapForTests.keys)
      .containsExactly(
        Label.of("//my/build/package1:NewClass.java"),
        Label.of("//my/build/package1:BUILD"),
        Label.of("//my/build/package2:Class2.java"),
        Label.of("//my/build/package2:BUILD"),
      )
  }

  @Test
  fun testApplyDelta_deletePackage() {
    val base =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package1:rule"))
            .copy(ruleClass = "java_library", sources = listOf(Label.of("//my/build/package1:Class1.java")))
        )
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:Class1.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:subpackage/AnotherClass.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:BUILD"), listOf()))
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package2:rule"))
            .copy(ruleClass = "java_library", sources = listOf(Label.of("//my/build/package2:Class2.java")))
        )
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package2:Class2.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package2:BUILD"), listOf()))
        .build()
    val baseProject = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()

    val queryStrategy =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        ImmutableSet.of(),
        /* deletedPackages= */ ImmutableSet.of(Path.of("my/build/package1")),
        ProjectStructureData.EMPTY,
      )
    Truth8.assertThat(queryStrategy.getQuerySpec()).isEmpty()
    val applied = queryStrategy.applyDelta(QuerySummary.EMPTY)
    Truth.assertThat(applied.rulesMapForTests.keys).containsExactly(Label.of("//my/build/package2:rule"))
    Truth.assertThat(applied.sourceFilesMapForTests.keys)
      .containsExactly(Label.of("//my/build/package2:Class2.java"), Label.of("//my/build/package2:BUILD"))
  }

  @Test
  fun testDelta_addPackage() {
    val base =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package1:rule"))
            .copy(ruleClass = "java_library", sources = listOf(Label.of("//my/build/package1:Class1.java")))
        )
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:Class1.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package1:BUILD"), listOf()))
        .build()
    val baseProject = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()
    val delta =
      QuerySummaryImpl.newBuilder()
        .putRules(
          QueryData.Rule.createForTests(label = Label.of("//my/build/package2:rule"))
            .copy(ruleClass = "java_library", sources = listOf(Label.of("//my/build/package2:Class2.java")))
        )
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package2:Class2.java"), listOf()))
        .putSourceFiles(QueryData.SourceFile(Label.of("//my/build/package2:BUILD"), listOf()))
        .build()

    val queryStrategy =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        /* modifiedPackages= */ ImmutableSet.of(Path.of("my/build/package2")),
        ImmutableSet.of(),
        ProjectStructureData.EMPTY,
      )
    val applied = queryStrategy.applyDelta(delta)
    Truth.assertThat(applied.rulesMapForTests.keys)
      .containsExactly(Label.of("//my/build/package1:rule"), Label.of("//my/build/package2:rule"))
    Truth.assertThat(applied.sourceFilesMapForTests.keys)
      .containsExactly(
        Label.of("//my/build/package1:Class1.java"),
        Label.of("//my/build/package1:BUILD"),
        Label.of("//my/build/package2:Class2.java"),
        Label.of("//my/build/package2:BUILD"),
      )
  }

  @Test
  fun testDelta_preservesUnaffectedPackageErrors() {
    val base =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder()
          .addBuildPackages(
            Query.StoredBuildPackage.newBuilder()
              .setWorkspace(0) // index 0 is ""
              .setBuildPackage(1) // index 1
              .setHasError(true)
          )
          .setStringStorage(Query.StringStorage.newBuilder().addAllIndexedStrings(listOf("", "my/build/package1")).build())
          .build()
      )
    val baseProject = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()

    val delta =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder()
          .addBuildPackages(Query.StoredBuildPackage.newBuilder().setWorkspace(0).setBuildPackage(1).setHasError(true))
          .setStringStorage(Query.StringStorage.newBuilder().addAllIndexedStrings(listOf("", "my/build/package2")).build())
          .build()
      )

    val queryStrategy =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        /* modifiedPackages= */ ImmutableSet.of(Path.of("my/build/package2")),
        ImmutableSet.of(),
        ProjectStructureData.EMPTY,
      )
    val applied = queryStrategy.applyDelta(delta)
    Truth.assertThat(applied.packagesWithErrors).containsExactly(Path.of("my/build/package1"), Path.of("my/build/package2"))
  }

  @Test
  fun testDelta_preservesAndUpdatesPackageStamps() {
    val base =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder()
          .addBuildPackages(Query.StoredBuildPackage.newBuilder().setWorkspace(0).setBuildPackage(1).setStamp(100L))
          .setStringStorage(Query.StringStorage.newBuilder().addAllIndexedStrings(listOf("", "my/build/package1")).build())
          .build()
      )
    val baseProject = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()

    val delta =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder()
          .addBuildPackages(
            Query.StoredBuildPackage.newBuilder().setWorkspace(0).setBuildPackage(1).setStamp(0L) // Newly queried, stamp not yet set
          )
          .setStringStorage(Query.StringStorage.newBuilder().addAllIndexedStrings(listOf("", "my/build/package2")).build())
          .build()
      )

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("my"),
            mapOf(
              Path.of("my/build/package1") to BuildPackage(Path.of("my/build/package1"), emptyList(), stamp = 100L),
              Path.of("my/build/package2") to BuildPackage(Path.of("my/build/package2"), emptyList(), stamp = 200L),
            ),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val queryStrategy =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        /* modifiedPackages= */ ImmutableSet.of(Path.of("my/build/package2")),
        ImmutableSet.of(),
        projectStructureData,
      )
    val applied = queryStrategy.applyDelta(delta)
    Truth.assertThat(applied.getBuildPackage(Label.of("//my/build/package1:package1"))?.stamp).isEqualTo(100L)
    Truth.assertThat(applied.getBuildPackage(Label.of("//my/build/package2:package2"))?.stamp).isEqualTo(200L)
  }

  @Test
  fun testDelta_modifiedPackageUpdatesExistingStamp() {
    val base =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder()
          .addBuildPackages(Query.StoredBuildPackage.newBuilder().setWorkspace(0).setBuildPackage(1).setStamp(100L))
          .setStringStorage(Query.StringStorage.newBuilder().addAllIndexedStrings(listOf("", "my/build/package1")).build())
          .build()
      )
    val baseProject = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()

    val delta =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder()
          .addBuildPackages(Query.StoredBuildPackage.newBuilder().setWorkspace(0).setBuildPackage(1).setStamp(0L))
          .setStringStorage(Query.StringStorage.newBuilder().addAllIndexedStrings(listOf("", "my/build/package1")).build())
          .build()
      )

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("my"),
            mapOf(Path.of("my/build/package1") to BuildPackage(Path.of("my/build/package1"), emptyList(), stamp = 300L)),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val refresh =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        /* modifiedPackages= */ ImmutableSet.of(Path.of("my/build/package1")),
        ImmutableSet.of(),
        projectStructureData,
      )
    val applied = refresh.applyDelta(delta)
    Truth.assertThat(applied.getBuildPackage(Label.of("//my/build/package1:package1"))?.stamp).isEqualTo(300L)
  }

  @Test
  fun testDelta_deletePackageRemovesPackageStamp() {
    val base =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder()
          .addBuildPackages(Query.StoredBuildPackage.newBuilder().setWorkspace(0).setBuildPackage(1).setStamp(100L))
          .addBuildPackages(Query.StoredBuildPackage.newBuilder().setWorkspace(0).setBuildPackage(2).setStamp(200L))
          .setStringStorage(
            Query.StringStorage.newBuilder().addAllIndexedStrings(listOf("", "my/build/package1", "my/build/package2")).build()
          )
          .build()
      )
    val baseProject = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(base).build()

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("my"),
            mapOf(Path.of("my/build/package2") to BuildPackage(Path.of("my/build/package2"), emptyList(), stamp = 200L)),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val refresh =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        /* modifiedPackages= */ ImmutableSet.of(),
        /* deletedPackages= */ ImmutableSet.of(Path.of("my/build/package1")),
        projectStructureData,
      )
    val applied = refresh.applyDelta(QuerySummary.EMPTY)
    Truth.assertThat(applied.getBuildPackage(Label.of("//my/build/package1:package1"))).isNull()
    Truth.assertThat(applied.getBuildPackage(Label.of("//my/build/package2:package2"))?.stamp).isEqualTo(200L)
  }

  @Test
  fun testQuerySpec_withModifiedPackages() {
    val baseProject = PostQuerySyncData.EMPTY
    val refresh =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        /* modifiedPackages= */ ImmutableSet.of(Path.of("my/build/package1")),
        ImmutableSet.of(),
        ProjectStructureData.EMPTY,
      )
    val querySpec = refresh.getQuerySpec()
    Truth8.assertThat(querySpec).isPresent()
    Truth.assertThat(querySpec.get().getQueryExpression().orElse("")).isEqualTo("(//my/build/package1:*)")
  }

  @Test
  fun testCreatePostQuerySyncData() {
    val baseProject = PostQuerySyncData.EMPTY
    val delta =
      QuerySummaryImpl.create(
        Query.Summary.newBuilder()
          .addBuildPackages(Query.StoredBuildPackage.newBuilder().setWorkspace(0).setBuildPackage(1).setStamp(0L))
          .setStringStorage(Query.StringStorage.newBuilder().addAllIndexedStrings(listOf("", "my/build/package1")).build())
          .build()
      )
    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("my"),
            mapOf(Path.of("my/build/package1") to BuildPackage(Path.of("my/build/package1"), emptyList(), stamp = 500L)),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val refresh =
      PartialProjectRefresh(
        Path.of("/workspace/root"),
        baseProject,
        /* modifiedPackages= */ ImmutableSet.of(Path.of("my/build/package1")),
        ImmutableSet.of(),
        projectStructureData,
      )
    val postQuerySyncData = refresh.createPostQuerySyncData(delta)
    Truth.assertThat(postQuerySyncData.querySummary().getBuildPackage(Label.of("//my/build/package1:package1"))?.stamp).isEqualTo(500L)
  }

  private fun <T : Any> listOf(vararg list: T): ImmutableList<T> = ImmutableList.copyOf(list)
}
