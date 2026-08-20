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
import com.google.common.truth.Truth
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.project.BuildPackage
import com.google.idea.blaze.qsync.project.PostQuerySyncData
import com.google.idea.blaze.qsync.project.ProjectDefinition
import com.google.idea.blaze.qsync.project.ProjectStructureData
import com.google.idea.blaze.qsync.project.ProjectStructureRoot
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.google.idea.blaze.qsync.query.QuerySpec
import com.google.idea.blaze.qsync.query.QuerySummary
import com.google.idea.blaze.qsync.query.QuerySummaryImpl
import com.google.idea.blaze.qsync.query.QuerySummaryTestUtil
import java.nio.file.Path
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectRefresherTest {
  private fun createRefresher(existingSnapshot: QuerySyncProjectSnapshot? = QuerySyncProjectSnapshot.EMPTY): ProjectRefresher {
    return ProjectRefresher(
      Path.of("/"),
      QuerySpec.QueryStrategy.PLAIN,
      Suppliers.ofInstance(Optional.ofNullable(existingSnapshot)),
    )
  }

  private val projectDef =
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

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_requireFullSync() {
    val project = PostQuerySyncData.EMPTY
    val update =
      createRefresher()
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = ProjectDefinition.EMPTY,
            projectStructureData = ProjectStructureData.EMPTY,
            requestedPackages = emptySet(),
            requireFullSync = { true },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )
    Truth.assertThat(update).isInstanceOf(FullProjectUpdate::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_noChanges() {
    val project: PostQuerySyncData = PostQuerySyncData.EMPTY
    val existingProject = QuerySyncProjectSnapshot.EMPTY
    val update =
      createRefresher(existingProject)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = ProjectDefinition.EMPTY,
            projectStructureData = ProjectStructureData.EMPTY,
            requestedPackages = emptySet(),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )
    Truth.assertThat(update).isInstanceOf(NoopProjectRefresh::class.java)
    Truth.assertThat(update.createPostQuerySyncData(QuerySummary.EMPTY)).isEqualTo(existingProject.queryData)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_noChanges_noExistingProjectSnapshot() {
    val project = PostQuerySyncData.EMPTY
    val update =
      createRefresher(existingSnapshot = null)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = ProjectDefinition.EMPTY,
            projectStructureData = ProjectStructureData.EMPTY,
            requestedPackages = emptySet(),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )
    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.deletedPackages).isEmpty()
    Truth.assertThat(partialQuery.modifiedPackages).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_packageModified() {
    val querySummary =
      QuerySummaryImpl.newBuilder()
        .putAllPackages(QuerySummaryImpl.create(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")).buildPackages)
        .putPackageStamp(Label.of("//package/path:path"), 1L)
        .build()
    val project = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(querySummary).build()

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("package"),
            mapOf(Path.of("package/path") to BuildPackage(Path.of("package/path"), emptyList(), stamp = 2L)),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef)
    val update =
      createRefresher(existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = projectDef,
            projectStructureData = projectStructureData,
            requestedPackages = setOf(Path.of("package/path")),
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
  fun testStartPartialRefresh_packageAdded() {
    val project = PostQuerySyncData.EMPTY

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("package"),
            mapOf(Path.of("package/newpkg") to BuildPackage(Path.of("package/newpkg"), emptyList(), stamp = 100L)),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef)
    val update =
      createRefresher(existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = projectDef,
            projectStructureData = projectStructureData,
            requestedPackages = setOf(Path.of("package/newpkg")),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.deletedPackages).isEmpty()
    Truth.assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/newpkg"))
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_packageAdded_withParentPackage() {
    val querySummary =
      QuerySummaryImpl.newBuilder()
        .putAllPackages(QuerySummaryImpl.create(QuerySummaryTestUtil.createProtoForPackages("//package/parent:rule")).buildPackages)
        .putPackageStamp(Label.of("//package/parent:parent"), 50L)
        .build()
    val project = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(querySummary).build()

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("package"),
            mapOf(
              Path.of("package/parent") to BuildPackage(Path.of("package/parent"), emptyList(), stamp = 50L),
              Path.of("package/parent/child") to BuildPackage(Path.of("package/parent/child"), emptyList(), stamp = 100L),
            ),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef)
    val update =
      createRefresher(existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = projectDef,
            projectStructureData = projectStructureData,
            requestedPackages = setOf(Path.of("package/parent/child")),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.deletedPackages).isEmpty()
    Truth.assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/parent/child"), Path.of("package/parent"))
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_packageDeleted() {
    val querySummary =
      QuerySummaryImpl.newBuilder()
        .putAllPackages(QuerySummaryImpl.create(QuerySummaryTestUtil.createProtoForPackages("//package/deleted:rule")).buildPackages)
        .putPackageStamp(Label.of("//package/deleted:deleted"), 100L)
        .build()
    val project = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(querySummary).build()

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("package"),
            emptyMap(),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef)
    val update =
      createRefresher(existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = projectDef,
            projectStructureData = projectStructureData,
            requestedPackages = emptySet(),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.deletedPackages).containsExactly(Path.of("package/deleted"))
    Truth.assertThat(partialQuery.modifiedPackages).isEmpty()
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_packagesModifiedAndDeleted() {
    val querySummary =
      QuerySummaryImpl.newBuilder()
        .putAllPackages(
          QuerySummaryImpl.create(QuerySummaryTestUtil.createProtoForPackages("//package/mod:rule", "//package/del:rule")).buildPackages
        )
        .putPackageStamp(Label.of("//package/mod:mod"), 100L)
        .putPackageStamp(Label.of("//package/del:del"), 100L)
        .build()
    val project = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(querySummary).build()

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("package"),
            mapOf(Path.of("package/mod") to BuildPackage(Path.of("package/mod"), emptyList(), stamp = 200L)),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef)
    val update =
      createRefresher(existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = projectDef,
            projectStructureData = projectStructureData,
            requestedPackages = setOf(Path.of("package/mod")),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(PartialProjectRefresh::class.java)
    val partialQuery = update as PartialProjectRefresh
    Truth.assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/mod"))
    Truth.assertThat(partialQuery.deletedPackages).containsExactly(Path.of("package/del"))
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_packageOutsideProjectScope_ignored() {
    val querySummary =
      QuerySummaryImpl.newBuilder()
        .putAllPackages(QuerySummaryImpl.create(QuerySummaryTestUtil.createProtoForPackages("//other/package:rule")).buildPackages)
        .putPackageStamp(Label.of("//other/package:package"), 100L)
        .build()
    val project = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(querySummary).build()

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("other"),
            emptyMap(),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef)
    val update =
      createRefresher(existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = projectDef,
            projectStructureData = projectStructureData,
            requestedPackages = setOf(Path.of("other/package")),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(NoopProjectRefresh::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_packageUnchanged_returnsNoop() {
    val querySummary =
      QuerySummaryImpl.newBuilder()
        .putAllPackages(QuerySummaryImpl.create(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")).buildPackages)
        .putPackageStamp(Label.of("//package/path:path"), 100L)
        .build()
    val project = PostQuerySyncData.EMPTY.toBuilder().setQuerySummary(querySummary).build()

    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("package"),
            mapOf(Path.of("package/path") to BuildPackage(Path.of("package/path"), emptyList(), stamp = 100L)),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef)
    val update =
      createRefresher(existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = projectDef,
            projectStructureData = projectStructureData,
            requestedPackages = setOf(Path.of("package/path")),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(NoopProjectRefresh::class.java)
  }

  @Test
  @Throws(Exception::class)
  fun testStartPartialRefresh_userRequiredPackageNotInProjectStructure_ignored() {
    val project = PostQuerySyncData.EMPTY
    val projectStructureData = ProjectStructureData.EMPTY
    val existingSnapshot = QuerySyncProjectSnapshot.EMPTY.withProjectDefinition(projectDef)
    val update =
      createRefresher(existingSnapshot)
        .startPartialRefresh(
          RefreshParameters(
            lastQuery = project,
            projectDefinition = projectDef,
            projectStructureData = projectStructureData,
            requestedPackages = setOf(Path.of("package/nonexistent")),
            requireFullSync = { false },
          ),
          QuerySyncTestUtils.LOGGING_CONTEXT,
        )

    Truth.assertThat(update).isInstanceOf(NoopProjectRefresh::class.java)
  }

  @Test
  fun testFullProjectUpdate_preservesAndUpdatesPackageStamps() {
    val projectStructureData =
      ProjectStructureData.create(
        listOf(
          ProjectStructureRoot(
            Path.of("package"),
            mapOf(Path.of("package/path") to BuildPackage(Path.of("package/path"), emptyList(), stamp = 12345L)),
          )
        ),
        setOf(QuerySyncLanguage.JVM),
      )

    val update = createRefresher().startFullUpdate(QuerySyncTestUtils.LOGGING_CONTEXT, projectDef, projectStructureData)
    Truth.assertThat(update).isInstanceOf(FullProjectUpdate::class.java)

    val querySummary = QuerySummaryTestUtil.createProtoForPackages("//package/path:rule")
    val postQuerySyncData = update.createPostQuerySyncData(QuerySummaryImpl.create(querySummary))
    val pkg = postQuerySyncData.querySummary().getBuildPackage(Label.of("//package/path:path"))
    Truth.assertThat(pkg?.stamp).isEqualTo(12345L)
  }
}
