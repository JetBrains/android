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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableBiMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation;
import com.google.idea.blaze.qsync.project.SnapshotProto.WorkspaceSnapshot;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.protobuf.AbstractMessageLite;
import java.nio.file.Path;

/** Serializes a {@link PostQuerySyncData} instance to a proto message. */
public class SnapshotSerializer {

  public static final int PROTO_VERSION = 1;

  static final ImmutableBiMap<Operation, SnapshotProto.WorkspaceFileChange.VcsOperation> OP_MAP =
      ImmutableBiMap.of(
          Operation.ADD, SnapshotProto.WorkspaceFileChange.VcsOperation.ADD,
          Operation.DELETE, SnapshotProto.WorkspaceFileChange.VcsOperation.DELETE,
          Operation.MODIFY, SnapshotProto.WorkspaceFileChange.VcsOperation.MODIFY);

  private final SnapshotProto.Snapshot.Builder proto;

  public SnapshotSerializer() {
    proto = SnapshotProto.Snapshot.newBuilder().setVersion(PROTO_VERSION);
  }

  @VisibleForTesting
  public SnapshotSerializer(int protoVersion) {
    proto = SnapshotProto.Snapshot.newBuilder().setVersion(protoVersion);
  }

  @CanIgnoreReturnValue
  public SnapshotSerializer visit(PostQuerySyncData snapshot) {
    visitProjectDefinition(snapshot.projectDefinition());
    snapshot.vcsState().ifPresent(this::visitVcsState);
    visitQuerySummary(snapshot.querySummary());
    return this;
  }

  public AbstractMessageLite<?, ?> toProto() {
    return proto.build();
  }

  private void visitProjectDefinition(ProjectDefinition projectDefinition) {
    SnapshotProto.ProjectDefinition.Builder proto = this.proto.getProjectDefinitionBuilder();
    projectDefinition.projectIncludes().stream()
        .map(Path::toString)
        .forEach(proto::addIncludePaths);
    projectDefinition.projectExcludes().stream()
        .map(Path::toString)
        .forEach(proto::addExcludePaths);
    projectDefinition.languageClasses().stream()
        .map(l -> l.protoValue)
        .forEach(proto::addLanguageClasses);
    projectDefinition.testSources().forEach(proto::addTestSources);
  }

  private void visitVcsState(VcsState vcsState) {
    visitVcsState(vcsState, proto.getVcsStateBuilder());
  }

  public static void visitVcsState(VcsState vcsState, SnapshotProto.VcsState.Builder vcsProto) {
    vcsProto.setWorkspaceId(vcsState.workspaceId).setUpstreamRevision(vcsState.upstreamRevision);
    for (WorkspaceFileChange change : vcsState.workingSet) {
      vcsProto.addWorkingSet(
          SnapshotProto.WorkspaceFileChange.newBuilder()
              .setOperation(OP_MAP.get(change.operation))
              .setWorkspaceRelativePath(change.workspaceRelativePath.toString()));
    }
    vcsState
        .workspaceSnapshotPath
        .map(p -> WorkspaceSnapshot.newBuilder().setPath(p.toString()).build())
        .ifPresent(snapshot -> vcsProto.setWorkspaceSnapshot(snapshot));
  }

  private void visitQuerySummary(QuerySummary summary) {
    proto.setQuerySummary(summary.proto());
  }
}
