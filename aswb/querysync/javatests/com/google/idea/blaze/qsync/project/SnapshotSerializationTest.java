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
package com.google.idea.blaze.qsync.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation.ADD;
import static com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation.DELETE;
import static com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation.MODIFY;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.NOOP_CONTEXT;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth8;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange;
import com.google.idea.blaze.qsync.query.QuerySummaryTestUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth8.assertThat;

@RunWith(JUnit4.class)
public class SnapshotSerializationTest {

  @Test
  public void testSerialization_withVcsState() throws IOException {
    PostQuerySyncData original =
        PostQuerySyncData.builder()
            .setProjectDefinition(
                ProjectDefinition
                    .builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("project/path")))
                    .setProjectExcludes(ImmutableSet.of(Path.of("project/path/excluded")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .setTestSources(ImmutableSet.of("javatests/*"))
                    .build())
            .setVcsState(
                Optional.of(
                    new VcsState(
                        "workspaceId",
                        "123",
                        ImmutableSet.of(
                            new WorkspaceFileChange(ADD, Path.of("project/path/Added.java")),
                            new WorkspaceFileChange(DELETE, Path.of("project/path/Deleted.java")),
                            new WorkspaceFileChange(MODIFY, Path.of("project/path/Modified.java"))),
                        Optional.empty())))
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
            .build();
    byte[] serialized = new SnapshotSerializer().visit(original).toProto().toByteArray();
    PostQuerySyncData deserialized =
        new SnapshotDeserializer()
            .readFrom(new ByteArrayInputStream(serialized), NOOP_CONTEXT)
            .get()
            .getSyncData();
    Truth8.assertThat(deserialized.vcsState()).isEqualTo(original.vcsState());
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  public void testSerialization_withVcsState_including_workspaceSnapshot() throws IOException {
    PostQuerySyncData original =
        PostQuerySyncData.builder()
            .setProjectDefinition(ProjectDefinition.EMPTY)
            .setVcsState(
                Optional.of(
                    new VcsState(
                        "workspaceId",
                        "123",
                        ImmutableSet.of(),
                        Optional.of(Path.of("/snapshot/user/snapshot/1")))))
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
            .build();
    byte[] serialized = new SnapshotSerializer().visit(original).toProto().toByteArray();
    PostQuerySyncData deserialized =
        new SnapshotDeserializer()
            .readFrom(new ByteArrayInputStream(serialized), NOOP_CONTEXT)
            .get()
            .getSyncData();
    Truth8.assertThat(deserialized.vcsState()).isEqualTo(original.vcsState());
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  public void testSerialization_noVcsState() throws IOException {
    PostQuerySyncData original =
        PostQuerySyncData.builder()
            .setProjectDefinition(
                ProjectDefinition
                    .builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("project/path")))
                    .setProjectExcludes(ImmutableSet.of(Path.of("project/path/excluded")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .setTestSources(ImmutableSet.of("javatests/*"))
                    .build())
            .setVcsState(Optional.empty())
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
            .build();
    byte[] serialized = new SnapshotSerializer().visit(original).toProto().toByteArray();
    PostQuerySyncData deserialized =
        new SnapshotDeserializer()
            .readFrom(new ByteArrayInputStream(serialized), NOOP_CONTEXT)
            .get()
            .getSyncData();
    Truth8.assertThat(deserialized.vcsState()).isEqualTo(original.vcsState());
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  public void testSerialization_versionBump() throws IOException {
    PostQuerySyncData original =
        PostQuerySyncData.builder()
            .setProjectDefinition(
                ProjectDefinition
                    .builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("project/path")))
                    .setProjectExcludes(ImmutableSet.of(Path.of("project/path/excluded")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .setTestSources(ImmutableSet.of("javatests/*"))
                    .build())
            .setVcsState(Optional.empty())
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//project/path:path"))
            .build();
    byte[] serialized = new SnapshotSerializer(-1).visit(original).toProto().toByteArray();
    Truth8.assertThat(
            new SnapshotDeserializer().readFrom(new ByteArrayInputStream(serialized), NOOP_CONTEXT))
        .isEmpty();
  }
}
