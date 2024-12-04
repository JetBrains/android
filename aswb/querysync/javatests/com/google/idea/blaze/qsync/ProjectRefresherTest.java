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
package com.google.idea.blaze.qsync;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.differForFiles;
import static com.google.idea.blaze.qsync.QuerySyncTestUtils.noFilesChangedDiffer;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.query.QuerySummaryTestUtil;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProjectRefresherTest {

  private ProjectRefresher createRefresher() {
    return createRefresher(Optional.of(QuerySyncProjectSnapshot.EMPTY));
  }

  private ProjectRefresher createRefresher(VcsStateDiffer vcsDiffer) {
    return createRefresher(vcsDiffer, Optional.of(QuerySyncProjectSnapshot.EMPTY));
  }

  private ProjectRefresher createRefresher(Optional<QuerySyncProjectSnapshot> existingSnapshot) {
    return createRefresher(noFilesChangedDiffer(), existingSnapshot);
  }

  private ProjectRefresher createRefresher(
    VcsStateDiffer vcsDiffer, Optional<QuerySyncProjectSnapshot> existingSnapshot) {
    return new ProjectRefresher(vcsDiffer, Path.of("/"), QuerySpec.QueryStrategy.PLAIN, Suppliers.ofInstance(existingSnapshot), () -> true);
  }

  @Test
  public void testStartPartialRefresh_pluginVersionChanged() throws Exception {
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setVcsState(
                Optional.of(new VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty())))
            .setQuerySummary(QuerySummary.create(Query.Summary.newBuilder().setVersion(-1).build()))
            .build();

    RefreshOperation update =
        createRefresher()
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                project.vcsState(),
                project.bazelVersion(),
                project.projectDefinition());
    assertThat(update).isInstanceOf(FullProjectUpdate.class);
  }

  @Test
  public void testStartPartialRefresh_vcsSnapshotUnchanged_existingProjectSnapshotWithVcsState()
      throws Exception {
    VcsState vcsState =
        new VcsState(
            "workspaceId",
            "1",
            ImmutableSet.of(),
            Optional.of(Path.of("/my/workspace/.snapshot/1")));
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setVcsState(Optional.of(vcsState))
            .setQuerySummary(QuerySummary.EMPTY)
            .build();
    QuerySyncProjectSnapshot existingProject = QuerySyncProjectSnapshot.EMPTY;
    RefreshOperation update =
        createRefresher(QuerySyncTestUtils.NO_CHANGES_DIFFER)
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                project.vcsState(),
                project.bazelVersion(),
                project.projectDefinition());
    assertThat(update).isInstanceOf(NoopProjectRefresh.class);
    assertThat(update.createPostQuerySyncData(QuerySummary.EMPTY))
        .isEqualTo(
            existingProject.queryData().toBuilder().setVcsState(Optional.of(vcsState)).build());
  }

  @Test
  public void testStartPartialRefresh_vcsSnapshotUnchanged_noExistingProjectSnapshot()
      throws Exception {
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setVcsState(
                Optional.of(
                    new VcsState(
                        "workspaceId",
                        "1",
                        ImmutableSet.of(),
                        Optional.of(Path.of("/my/workspace/.snapshot/1")))))
            .setQuerySummary(QuerySummary.EMPTY)
            .build();
    RefreshOperation update =
        createRefresher(Optional.empty())
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                project.vcsState(),
                project.bazelVersion(),
                project.projectDefinition());
    assertThat(update).isInstanceOf(PartialProjectRefresh.class);
  }

  @Test
  public void testStartPartialRefresh_workspaceChange() throws Exception {
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setVcsState(
                Optional.of(new VcsState("workspace1", "1", ImmutableSet.of(), Optional.empty())))
            .build();

    RefreshOperation update =
        createRefresher()
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                Optional.of(new VcsState("workspace2", "1", ImmutableSet.of(), Optional.empty())),
                project.bazelVersion(),
                project.projectDefinition());
    assertThat(update).isInstanceOf(FullProjectUpdate.class);
  }

  @Test
  public void testStartPartialRefresh_upstreamRevisionChange() throws Exception {
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setVcsState(
                Optional.of(new VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty())))
            .build();

    RefreshOperation update =
        createRefresher()
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                Optional.of(new VcsState("workspaceId", "2", ImmutableSet.of(), Optional.empty())),
                project.bazelVersion(),
                project.projectDefinition());
    assertThat(update).isInstanceOf(FullProjectUpdate.class);
  }

  @Test
  public void testStartPartialRefresh_bazelVersionChanged() throws Exception {
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule"))
            .setVcsState(
                Optional.of(
                    new VcsState(
                        "workspaceId",
                        "1",
                        ImmutableSet.of(
                            new WorkspaceFileChange(
                                Operation.MODIFY, Path.of("package/path/BUILD"))),
                        Optional.empty())))
            .setProjectDefinition(
                ProjectDefinition.builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("package")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .build())
            .setBazelVersion(Optional.of("1.0.0"))
            .build();

    RefreshOperation update =
        createRefresher(VcsStateDiffer.NONE)
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                project.vcsState(),
                Optional.of("2.0.0"),
                project.projectDefinition());

    assertThat(update).isInstanceOf(FullProjectUpdate.class);
  }

  @Test
  public void testStartPartialRefresh_buildFileAddedThenReverted() throws Exception {
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule"))
            .setVcsState(
                Optional.of(
                    new VcsState(
                        "workspaceId",
                        "1",
                        ImmutableSet.of(
                            new WorkspaceFileChange(Operation.ADD, Path.of("package/path/BUILD"))),
                        Optional.empty())))
            .setProjectDefinition(
                ProjectDefinition.builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("package")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .build())
            .build();

    RefreshOperation update =
        createRefresher(VcsStateDiffer.NONE)
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                Optional.of(new VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty())),
                project.bazelVersion(),
                project.projectDefinition());

    assertThat(update).isInstanceOf(PartialProjectRefresh.class);
    PartialProjectRefresh partialQuery = (PartialProjectRefresh) update;
    assertThat(partialQuery.deletedPackages).containsExactly(Path.of("package/path"));
    assertThat(partialQuery.modifiedPackages).isEmpty();
  }

  @Test
  public void testStartPartialRefresh_buildFileDeletedThenReverted() throws Exception {
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setVcsState(
                Optional.of(
                    new VcsState(
                        "workspaceId",
                        "1",
                        ImmutableSet.of(
                            new WorkspaceFileChange(
                                Operation.DELETE, Path.of("package/path/BUILD"))),
                        Optional.empty())))
            .setProjectDefinition(
                ProjectDefinition.builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("package")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .build())
            .build();

    RefreshOperation update =
        createRefresher(VcsStateDiffer.NONE)
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                Optional.of(new VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty())),
                project.bazelVersion(),
                project.projectDefinition());

    assertThat(update).isInstanceOf(PartialProjectRefresh.class);
    PartialProjectRefresh partialQuery = (PartialProjectRefresh) update;
    assertThat(partialQuery.deletedPackages).isEmpty();
    assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/path"));
  }

  @Test
  public void testStartPartialRefresh_buildFileModified() throws Exception {
    ImmutableSet<WorkspaceFileChange> workingSet =
        ImmutableSet.of(new WorkspaceFileChange(Operation.MODIFY, Path.of("package/path/BUILD")));
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule"))
            .setVcsState(
                Optional.of(new VcsState("workspaceId", "1", workingSet, Optional.empty())))
            .setProjectDefinition(
                ProjectDefinition.builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("package")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .build())
            .build();

    RefreshOperation update =
        createRefresher(differForFiles(Path.of("package/path/BUILD")))
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                Optional.of(new VcsState("workspaceId", "1", workingSet, Optional.empty())),
                project.bazelVersion(),
                project.projectDefinition());

    assertThat(update).isInstanceOf(PartialProjectRefresh.class);
    PartialProjectRefresh partialQuery = (PartialProjectRefresh) update;
    assertThat(partialQuery.deletedPackages).isEmpty();
    assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/path"));
  }

  @Test
  public void testStartPartialRefresh_buildFileInWorkingSet_unmodified() throws Exception {
    ImmutableSet<WorkspaceFileChange> workingSet =
        ImmutableSet.of(
            new WorkspaceFileChange(Operation.MODIFY, Path.of("package/path/BUILD")),
            new WorkspaceFileChange(Operation.MODIFY, Path.of("package/path/Class.java")));
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule"))
            .setVcsState(
                Optional.of(new VcsState("workspaceId", "1", workingSet, Optional.empty())))
            .setProjectDefinition(
                ProjectDefinition.builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("package")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .build())
            .build();

    RefreshOperation update =
        createRefresher(differForFiles(Path.of("package/path/Class.java")))
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                Optional.of(new VcsState("workspaceId", "1", workingSet, Optional.empty())),
                project.bazelVersion(),
                project.projectDefinition());

    assertThat(update).isInstanceOf(NoopProjectRefresh.class);
  }

  @Test
  public void testStartPartialRefresh_buildFileModifiedThenReverted() throws Exception {
    PostQuerySyncData project =
        PostQuerySyncData.EMPTY.toBuilder()
            .setQuerySummary(QuerySummaryTestUtil.createProtoForPackages("//package/path:rule"))
            .setVcsState(
                Optional.of(
                    new VcsState(
                        "workspaceId",
                        "1",
                        ImmutableSet.of(
                            new WorkspaceFileChange(
                                Operation.MODIFY, Path.of("package/path/BUILD"))),
                        Optional.empty())))
            .setProjectDefinition(
                ProjectDefinition.builder()
                    .setProjectIncludes(ImmutableSet.of(Path.of("package")))
                    .setLanguageClasses(ImmutableSet.of(QuerySyncLanguage.JAVA))
                    .build())
            .build();

    RefreshOperation update =
        createRefresher(VcsStateDiffer.NONE)
            .startPartialRefresh(
                QuerySyncTestUtils.LOGGING_CONTEXT,
                project,
                Optional.of(new VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty())),
                project.bazelVersion(),
                project.projectDefinition());

    assertThat(update).isInstanceOf(PartialProjectRefresh.class);
    PartialProjectRefresh partialQuery = (PartialProjectRefresh) update;
    assertThat(partialQuery.deletedPackages).isEmpty();
    assertThat(partialQuery.modifiedPackages).containsExactly(Path.of("package/path"));
  }
}
