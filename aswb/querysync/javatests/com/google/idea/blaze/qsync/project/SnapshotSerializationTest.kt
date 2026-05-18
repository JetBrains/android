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
    val projectDefinition =
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
    val original =
      PostQuerySyncData.builder()
        .setVcsState(
          Optional.of(
            VcsState(
              "workspaceId",
              "123",
              ImmutableSet.of(
                WorkspaceFileChange(WorkspaceFileChange.Operation.ADD, Path.of("project/path/Added.java")),
                WorkspaceFileChange(WorkspaceFileChange.Operation.DELETE, Path.of("project/path/Deleted.java")),
                WorkspaceFileChange(WorkspaceFileChange.Operation.MODIFY, Path.of("project/path/Modified.java")),
              ),
              Optional.empty(),
            )
          )
        )
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .setBazelVersion(Optional.of("1.2.3"))
        .build()
    val serialized =
      SnapshotSerializer().visit(projectDefinition).visit(original).visit(createDefaultProjectStructureData()).toProto().toByteArray()
    val deserialized = SnapshotDeserializer.readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)
    Truth8.assertThat(deserialized?.queryData?.vcsState()).isEqualTo(original.vcsState())
    Truth.assertThat(deserialized?.queryData).isEqualTo(original)
    Truth.assertThat(deserialized?.projectDefinition).isEqualTo(projectDefinition)
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_withVcsState_including_workspaceSnapshot() {
    val projectDefinition = ProjectDefinition.EMPTY
    val original =
      PostQuerySyncData.builder()
        .setVcsState(Optional.of(VcsState("workspaceId", "123", ImmutableSet.of(), Optional.of(Path.of("/snapshot/user/snapshot/1")))))
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .setBazelVersion(Optional.of("1.2.3"))
        .build()
    val serialized =
      SnapshotSerializer().visit(projectDefinition).visit(original).visit(createDefaultProjectStructureData()).toProto().toByteArray()
    val deserialized = SnapshotDeserializer.readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)
    Truth8.assertThat(deserialized?.queryData?.vcsState()).isEqualTo(original.vcsState())
    Truth.assertThat(deserialized?.queryData).isEqualTo(original)
    Truth.assertThat(deserialized?.projectDefinition).isEqualTo(projectDefinition)
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_noVcsState() {
    val projectDefinition =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("project/path")),
        projectExcludes = setOf(Path.of("project/path/excluded")),
        deriveTargetsFromDirectories = false,
        targetPatterns = listOf(parse("//some/pattern:all"), parse("-//some/negative/pattern")),
        isAndroidWorkspace = false,
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = setOf("javatests/*"),
        systemExcludes = emptySet(),
      )
    val original =
      PostQuerySyncData.builder()
        .setVcsState(Optional.empty())
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .build()
    val serialized =
      SnapshotSerializer().visit(projectDefinition).visit(original).visit(createDefaultProjectStructureData()).toProto().toByteArray()
    val deserialized = SnapshotDeserializer.readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)
    Truth8.assertThat(deserialized?.queryData?.vcsState()).isEqualTo(original.vcsState())
    Truth.assertThat(deserialized?.queryData).isEqualTo(original)
    Truth.assertThat(deserialized?.projectDefinition).isEqualTo(projectDefinition)
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_versionBump() {
    val projectDefinition =
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
    val original =
      PostQuerySyncData.builder()
        .setVcsState(Optional.empty())
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .build()
    val serialized = SnapshotSerializer(-1).visit(projectDefinition).visit(original).toProto().toByteArray()
    Truth.assertThat(SnapshotDeserializer.readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)).isNull()
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_projectDefinition() {
    val projectDefinition =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("project/path")),
        projectExcludes = setOf(Path.of("project/path/excluded")),
        deriveTargetsFromDirectories = true,
        targetPatterns = listOf(parse("//some/pattern:all"), parse("-//some/negative/pattern")),
        isAndroidWorkspace = true,
        languageClasses = setOf(QuerySyncLanguage.JVM),
        testSources = setOf("javatests/*"),
        systemExcludes = setOf(Path.of(".aswb")),
      )
    val original =
      PostQuerySyncData.builder()
        .setVcsState(Optional.empty())
        .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
        .build()
    val serialized =
      SnapshotSerializer().visit(projectDefinition).visit(original).visit(createDefaultProjectStructureData()).toProto().toByteArray()
    val deserialized = SnapshotDeserializer.readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)
    Truth.assertThat(deserialized?.projectDefinition).isEqualTo(projectDefinition)
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_projectStructureData() {
    val projectDefinition = ProjectDefinition.EMPTY
    val originalSyncData = createDefaultSyncData()
    val originalProjectStructureData = createDefaultProjectStructureData()

    val serializer = SnapshotSerializer()
    serializer.visit(projectDefinition)
    serializer.visit(originalSyncData)
    serializer.visit(originalProjectStructureData)
    val serialized = serializer.toProto().toByteArray()

    val deserializedSnapshot = SnapshotDeserializer.readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)

    Truth.assertThat(deserializedSnapshot).isNotNull()
    Truth.assertThat(deserializedSnapshot!!.queryData).isEqualTo(originalSyncData)
    Truth.assertThat(deserializedSnapshot.projectDefinition).isEqualTo(projectDefinition)
    Truth.assertThat(deserializedSnapshot.projectStructureData?.roots).isEqualTo(originalProjectStructureData.roots)
    Truth.assertThat(deserializedSnapshot.projectStructureData?.activeLanguages).isEqualTo(originalProjectStructureData.activeLanguages)
  }

  private fun createDefaultProjectStructureData(): ProjectStructureData =
    ProjectStructureData.create(
      roots =
        listOf(
          ProjectStructureRoot(
            projectStructureRootPath = Path.of("project"),
            buildPackages =
              mapOf(
                Path.of("project/path") to
                  BuildPackage(
                    path = Path.of("project/path"),
                    sourceSets =
                      listOf(
                        SourceSet(
                          rootPath = Path.of("project/path"),
                          javaSourceFiles = listOf(Path.of("A.java")),
                          nonJavaSourceFiles = listOf(Path.of("B.txt")),
                          javaPackage = "com.example",
                        )
                      ),
                  )
              ),
          )
        ),
      activeLanguages = setOf(QuerySyncLanguage.JVM),
    )

  @Test
  @Throws(IOException::class)
  fun testSerialization_withoutProjectStructureData() {
    val projectDefinition = ProjectDefinition.EMPTY
    val originalSyncData = createDefaultSyncData()

    val serializer = SnapshotSerializer()
    serializer.visit(projectDefinition)
    serializer.visit(originalSyncData)
    val serialized = serializer.toProto().toByteArray()

    val deserializedSnapshot = SnapshotDeserializer.readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)

    Truth.assertThat(deserializedSnapshot).isNull()
  }

  @Test
  @Throws(IOException::class)
  fun testSerialization_withNullProjectStructureData() {
    val projectDefinition = ProjectDefinition.EMPTY
    val originalSyncData = createDefaultSyncData()

    val serializer = SnapshotSerializer()
    serializer.visit(projectDefinition)
    serializer.visit(originalSyncData)
    serializer.visit(null as ProjectStructureData?)
    val serialized = serializer.toProto().toByteArray()

    val deserializedSnapshot = SnapshotDeserializer.readFrom(ByteArrayInputStream(serialized), QuerySyncTestUtils.NOOP_CONTEXT)

    Truth.assertThat(deserializedSnapshot).isNull()
  }

  private fun createDefaultSyncData(): PostQuerySyncData {
    return PostQuerySyncData.builder()
      .setVcsState(Optional.empty())
      .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
      .build()
  }
}
