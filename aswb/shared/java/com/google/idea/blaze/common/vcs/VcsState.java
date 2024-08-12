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
package com.google.idea.blaze.common.vcs;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** State of the projects VCS at a point in time. */
public class VcsState {

  /**
   * A unique ID for the workspace that this state derives from.
   *
   * <p>This is treated as an opaque string for equality testing only.
   */
  public final String workspaceId;

  /**
   * Upstream/base revision or CL number. This usually represents the last checked-in change that
   * the users workspace contains.
   *
   * <p>This is treated as an opaque string for equality testing only.
   */
  public final String upstreamRevision;

  /** The set of files in the workspace that differ compared to {@link #upstreamRevision}. */
  public final ImmutableSet<WorkspaceFileChange> workingSet;

  /**
   * The readonly workspace snapshot path that this state derives from. If set, this can be used to
   * ensure atomic operations on the workspace by ensuring that a set of sequential operations are
   * all using the exact same revision of the workspace.
   */
  public final Optional<Path> workspaceSnapshotPath;

  public VcsState(
      String workspaceId,
      String upstreamRevision,
      ImmutableSet<WorkspaceFileChange> workingSet,
      Optional<Path> workspaceSnapshotPath) {
    this.workspaceId = workspaceId;
    this.upstreamRevision = upstreamRevision;
    this.workingSet = workingSet;
    this.workspaceSnapshotPath = workspaceSnapshotPath;
  }

  @Override
  public String toString() {
    return "VcsState{upstreamRevision='" + upstreamRevision + "', workingSet=" + workingSet + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VcsState)) {
      return false;
    }
    VcsState that = (VcsState) o;
    return workspaceId.equals(that.workspaceId)
        && upstreamRevision.equals(that.upstreamRevision)
        && workingSet.equals(that.workingSet)
        && workspaceSnapshotPath.equals(that.workspaceSnapshotPath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(workspaceId, upstreamRevision, workingSet, workspaceSnapshotPath);
  }

  /**
   * Returns workspace-relative paths of modified files (excluding deletions), according to the VCS
   */
  public ImmutableSet<Path> modifiedFiles() {
    return workingSet.stream()
        .filter(c -> c.operation != Operation.DELETE)
        .map(c -> c.workspaceRelativePath)
        .collect(toImmutableSet());
  }
}
