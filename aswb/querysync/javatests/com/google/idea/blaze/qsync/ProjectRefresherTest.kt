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

import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import com.google.idea.blaze.common.vcs.VcsState
import com.google.idea.blaze.common.vcs.WorkspaceFileChange
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.query.Query
import com.google.idea.blaze.qsync.query.QuerySpec
import com.google.idea.blaze.qsync.query.QuerySummary
import com.google.idea.blaze.qsync.query.QuerySummaryImpl.Companion.create
import com.google.idea.blaze.qsync.query.QuerySummaryTestUtil
import java.nio.file.Path
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectRefresherTest {
  private fun createRefresher(existingSnapshot: QuerySyncProjectSnapshot? = QuerySyncProjectSnapshot.EMPTY): ProjectRefresher {
    return createRefresher(QuerySyncTestUtils.noFilesChangedDiffer(), existingSnapshot)
  }

  private fun createRefresher(
    vcsDiffer: VcsStateDiffer?,
    existingSnapshot: QuerySyncProjectSnapshot? = QuerySyncProjectSnapshot.EMPTY,
  ): ProjectRefresher {
    return ProjectRefresher(
      vcsDiffer,
      Path.of("/"),
      QuerySpec.QueryStrategy.PLAIN,
      Suppliers.ofInstance(Optional.ofNullable(existingSnapshot)),
    )
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_pluginVersionChanged() {
    val project = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(create(Query.Summary.newBuilder().setVersion(-1).build())).build()

    val vcsState = Optional.of(VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty()))
    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withVcsState(vcsState.orElse(null))

    val update =
      createRefresher(existingSnapshot = existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            ProjectDefinition.EMPTY,
            Optional.ofNullable(existingSnapshot.vcsState),
            vcsState,
            ProjectDefinition.EMPTY,
            requireFullSync = { true },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )
    Truth.assertThat(update).isInstanceOf(FullProjectUpdate::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_vcsSnapshotUnchanged_existingProjectSnapshotWithVcsState() {
    val vcsState = Optional.of(VcsState("workspaceId", "1", ImmutableSet.of(), Optional.of(Path.of("/my/workspace/.snapshot/1"))))
    val project: PostQuerySyncData = PostQuerySyncData.EMPTY
    val existingProject = QuerySyncProjectSnapshot.EMPTY.withVcsState(vcsState.orElse(null))
    val update =
      createRefresher(QuerySyncTestUtils.NO_CHANGES_DIFFER, existingProject)
        .startPartialRefresh(
          RefreshParameters(
            project,
            ProjectDefinition.EMPTY,
            Optional.ofNullable(existingProject.vcsState),
            vcsState,
            ProjectDefinition.EMPTY,
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )
    Truth.assertThat(update).isInstanceOf(NoopProjectRefresh::class.java)
    Truth.assertThat(update.createPostQuerySyncData(QuerySummary.EMPTY)).isEqualTo(existingProject.queryData)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_vcsSnapshotUnchanged_noExistingProjectSnapshot() {
    val vcsState = Optional.of(VcsState("workspaceId", "1", ImmutableSet.of(), Optional.of(Path.of("/my/workspace/.snapshot/1"))))
    val project: PostQuerySyncData = PostQuerySyncData.EMPTY
    val update =
      createRefresher(existingSnapshot = null)
        .startPartialRefresh(
          RefreshParameters(
            project,
            ProjectDefinition.EMPTY,
            vcsState,
            vcsState,
            ProjectDefinition.EMPTY,
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )
    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_workspaceChange() {
    val project = PostQuerySyncData.EMPTY
    val previousVcsState = Optional.of(VcsState("workspace1", "1", ImmutableSet.of(), Optional.empty()))
    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withVcsState(previousVcsState.orElse(null))

    val update =
      createRefresher(existingSnapshot = existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            ProjectDefinition.EMPTY,
            Optional.ofNullable(existingSnapshot.vcsState),
            Optional.of(VcsState("workspace2", "1", ImmutableSet.of(), Optional.empty())),
            ProjectDefinition.EMPTY,
            requireFullSync = { true },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )
    Truth.assertThat(update).isInstanceOf(FullProjectUpdate::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_upstreamRevisionChange() {
    val project = PostQuerySyncData.EMPTY
    val previousVcsState = Optional.of(VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty()))
    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withVcsState(previousVcsState.orElse(null))

    val update =
      createRefresher(existingSnapshot = existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            ProjectDefinition.EMPTY,
            Optional.ofNullable(existingSnapshot.vcsState),
            Optional.of(VcsState("workspaceId", "2", ImmutableSet.of(), Optional.empty())),
            ProjectDefinition.EMPTY,
            requireFullSync = { true },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )
    Truth.assertThat(update).isInstanceOf(FullProjectUpdate::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_bazelVersionChanged() {
    val projectDef =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("package")),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = emptySet(),
        systemExcludes = emptySet(),
      )
    val project =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")).build()
    val vcsState =
      Optional.of(
        VcsState(
          "workspaceId",
          "1",
          ImmutableSet.of(WorkspaceFileChange(WorkspaceFileChange.Operation.MODIFY, Path.of("package/path/BUILD"))),
          Optional.empty(),
        )
      )

    val existingSnapshot =
      QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef).withVcsState(vcsState.orElse(null)).withBazelVersion("1.0.0")
    val update =
      createRefresher(VcsStateDiffer.NONE, existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            projectDef,
            Optional.ofNullable(existingSnapshot.vcsState),
            vcsState,
            projectDef,
            requireFullSync = { true },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(FullProjectUpdate::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_buildFileAddedThenReverted() {
    val projectDef =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("package")),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = emptySet(),
        systemExcludes = emptySet(),
      )
    val previousVcsState =
      Optional.of(
        VcsState(
          "workspaceId",
          "1",
          ImmutableSet.of(WorkspaceFileChange(WorkspaceFileChange.Operation.ADD, Path.of("package/path/BUILD"))),
          Optional.empty(),
        )
      )
    val project =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")).build()

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef).withVcsState(previousVcsState.orElse(null))
    val update =
      createRefresher(VcsStateDiffer.NONE, existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            projectDef,
            Optional.ofNullable(existingSnapshot.vcsState),
            Optional.of(VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty())),
            projectDef,
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.deletedPackages).containsExactly(Path.of("package/path"))
    Truth.assertThat(partialQuery.modifiedPackages).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_buildFileDeletedThenReverted() {
    val projectDef =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("package")),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        systemExcludes = emptySet(),
        testSources = emptySet(),
        languageClasses = setOf(QuerySyncLanguage.JVM),
      )
    val previousVcsState =
      Optional.of(
        VcsState(
          "workspaceId",
          "1",
          ImmutableSet.of(WorkspaceFileChange(WorkspaceFileChange.Operation.DELETE, Path.of("package/path/BUILD"))),
          Optional.empty(),
        )
      )
    val project =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")).build()

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef).withVcsState(previousVcsState.orElse(null))
    val update =
      createRefresher(VcsStateDiffer.NONE, existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            projectDef,
            Optional.ofNullable(existingSnapshot.vcsState),
            Optional.of(VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty())),
            projectDef,
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.deletedPackages).isEmpty()
    Truth.assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/path"))
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_buildFileModified() {
    val workingSet = ImmutableSet.of(WorkspaceFileChange(WorkspaceFileChange.Operation.MODIFY, Path.of("package/path/BUILD")))
    val projectDef =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("package")),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = emptySet(),
        systemExcludes = emptySet(),
      )
    val project =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")).build()

    val previousVcsState = Optional.of(VcsState("workspaceId", "1", workingSet, Optional.empty()))
    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef).withVcsState(previousVcsState.orElse(null))
    val update =
      createRefresher(QuerySyncTestUtils.differForFiles(Path.of("package/path/BUILD")), existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            projectDef,
            Optional.ofNullable(existingSnapshot.vcsState),
            Optional.of(VcsState("workspaceId", "1", workingSet, Optional.empty())),
            projectDef,
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.deletedPackages).isEmpty()
    Truth.assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/path"))
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_buildFileInWorkingSet_unmodified() {
    val workingSet =
      ImmutableSet.of(
        WorkspaceFileChange(WorkspaceFileChange.Operation.MODIFY, Path.of("package/path/BUILD")),
        WorkspaceFileChange(WorkspaceFileChange.Operation.MODIFY, Path.of("package/path/Class.java")),
      )
    val projectDef =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("package")),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = emptySet(),
        systemExcludes = emptySet(),
      )
    val project =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")).build()

    val previousVcsState = Optional.of(VcsState("workspaceId", "1", workingSet, Optional.empty()))
    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef).withVcsState(previousVcsState.orElse(null))
    val update =
      createRefresher(QuerySyncTestUtils.differForFiles(Path.of("package/path/Class.java")), existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            projectDef,
            Optional.ofNullable(existingSnapshot.vcsState),
            Optional.of(VcsState("workspaceId", "1", workingSet, Optional.empty())),
            projectDef,
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(NoopProjectRefresh::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_buildFileModifiedThenReverted() {
    val projectDef =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("package")),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = emptySet(),
        systemExcludes = emptySet(),
      )
    val project =
      PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")).build()

    val previousVcsState = Optional.of(VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty()))
    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef).withVcsState(previousVcsState.orElse(null))
    val update =
      createRefresher(VcsStateDiffer.NONE, existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            project,
            projectDef,
            Optional.ofNullable(existingSnapshot.vcsState),
            Optional.of(
              VcsState(
                "workspaceId",
                "1",
                ImmutableSet.of(WorkspaceFileChange(WorkspaceFileChange.Operation.MODIFY, Path.of("package/path/BUILD"))),
                Optional.empty(),
              )
            ),
            projectDef,
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.deletedPackages).isEmpty()
    Truth.assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/path"))
  }
}
