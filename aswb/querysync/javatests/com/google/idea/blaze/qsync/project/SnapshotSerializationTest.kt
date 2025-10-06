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

import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import com.google.common.truth.Truth8
import com.google.idea.blaze.common.TargetPattern.Companion.parse
import com.google.idea.blaze.common.vcs.VcsState
import com.google.idea.blaze.common.vcs.WorkspaceFileChange
import com.google.idea.blaze.qsync.QuerySyncTestUtils
import com.google.idea.blaze.qsync.query.QuerySummaryTestUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SnapshotSerializationTest {
  @Test
  @Throws(IOException::class)
  fun testSerialization_withVcsState() {
    val original =
      PostQuerySyncData.builder()
        .setProjectDefinition(
          ProjectDefinition(
            projectIncludes = setOf(Path.of("project/path")),
            projectExcludes = setOf(Path.of("project/path/excluded")),
            deriveTargetsFromDirectories = false,
            targetPatterns = emptyList(),
            isAndroidWorkspace = false,
            languageClasses = setOf(QuerySyncLanguage.JVM),
            testSources = setOf("javatests/*"),
            systemExcludes = setOf(Path.of(".aswb")),
          )
        )
        .setVcsState(
          Optional.of(
            VcsState(
              "workspaceId",
              "123",
              ImmutableSet.of(
                WorkspaceFileChange(
                  WorkspaceFileChange.Operation.ADD,
                  Path.of("project/path/Added.java")
                ),
                WorkspaceFileChange(
                  WorkspaceFileChange.Operation.DELETE,
                  Path.of("project/path/Deleted.java")
                ),
                WorkspaceFileChange(
                  WorkspaceFileChange.Operation.MODIFY,
                  Path.of("project/path/Modified.java")
                )
              ),
              Optional.empty()
            )
          )
        )
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .build()
    val serialized = SnapshotSerializer().visit(original).toProto().toByteArray()
    val deserialized: PostQuerySyncData =
      SnapshotDeserializer()
        .readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)
        ?.syncData!!
    Truth8.assertThat(deserialized.vcsState()).isEqualTo(original.vcsState())
    Truth.assertThat(deserialized).isEqualTo(original)
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_withVcsState_including_workspaceSnapshot() {
    val original =
      PostQuerySyncData.builder()
        .setProjectDefinition(ProjectDefinition.EMPTY)
        .setVcsState(
          Optional.of(
            VcsState(
              "workspaceId",
              "123",
              ImmutableSet.of(),
              Optional.of(Path.of("/snapshot/user/snapshot/1"))
            )
          )
        )
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .build()
    val serialized = SnapshotSerializer().visit(original).toProto().toByteArray()
    val deserialized: PostQuerySyncData =
      SnapshotDeserializer()
        .readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)
        ?.syncData!!
    Truth8.assertThat(deserialized.vcsState()).isEqualTo(original.vcsState())
    Truth.assertThat(deserialized).isEqualTo(original)
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_noVcsState() {
    val original =
      PostQuerySyncData.builder()
        .setProjectDefinition(
          ProjectDefinition(
            projectIncludes = setOf(Path.of("project/path")),
            projectExcludes = setOf(Path.of("project/path/excluded")),
            deriveTargetsFromDirectories = false,
            targetPatterns = listOf(
              parse("//some/pattern:all"),
              parse("-//some/negative/pattern")
            ),
            isAndroidWorkspace = false,
            languageClasses = setOf(QuerySyncLanguage.JVM),
            testSources = setOf("javatests/*"),
            systemExcludes = emptySet(),
          )
        )
        .setVcsState(Optional.empty())
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .build()
    val serialized = SnapshotSerializer().visit(original).toProto().toByteArray()
    val deserialized: PostQuerySyncData =
      SnapshotDeserializer()
        .readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)
        ?.syncData!!
    Truth8.assertThat(deserialized.vcsState()).isEqualTo(original.vcsState())
    Truth.assertThat(deserialized).isEqualTo(original)
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_versionBump() {
    val original =
      PostQuerySyncData.builder()
        .setProjectDefinition(
          ProjectDefinition(
            projectIncludes = setOf(Path.of("project/path")),
            projectExcludes = setOf(Path.of("project/path/excluded")),
            deriveTargetsFromDirectories = false,
            targetPatterns = emptyList(),
            isAndroidWorkspace = false,
            languageClasses = setOf(QuerySyncLanguage.JVM),
            testSources = setOf("javatests/*"),
            systemExcludes = emptySet(),
          )
        )
        .setVcsState(Optional.empty())
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .build()
    val serialized = SnapshotSerializer(-1).visit(original).toProto().toByteArray()
    Truth.assertThat(
      SnapshotDeserializer().readFrom(
        ByteArrayInputStream(serialized),
        QuerySyncTestUtils.NOOP_CONTEXT
      )
    )
      .isNull()
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_projectDefinition() {
    val projectDefinition =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("project/path")),
        projectExcludes = setOf(Path.of("project/path/excluded")),
        deriveTargetsFromDirectories = true,
        targetPatterns = listOf(
          parse("//some/pattern:all"),
          parse("-//some/negative/pattern")
        ),
        isAndroidWorkspace = true,
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = setOf("javatests/*"),
        systemExcludes = setOf(Path.of(".aswb")),
      )
    val original =
      PostQuerySyncData.builder()
        .setProjectDefinition(projectDefinition)
        .setVcsState(Optional.empty())
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .build()
    val serialized = SnapshotSerializer().visit(original).toProto().toByteArray()
    val deserialized: PostQuerySyncData =
      SnapshotDeserializer()
        .readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)
        ?.syncData!!
    Truth.assertThat(deserialized.projectDefinition()).isEqualTo(projectDefinition)
  }
}
