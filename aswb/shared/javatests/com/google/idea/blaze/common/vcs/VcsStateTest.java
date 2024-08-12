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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.vcs.WorkspaceFileChange.Operation;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VcsStateTest {

  @Test
  public void testModifiedFiles_emptyWorkingSet_returnsEmpty() {
    VcsState vcsState = new VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty());
    assertThat(vcsState.modifiedFiles()).isEmpty();
  }

  @Test
  public void testModifiedFiles_modifiedFile_returnsModifiedFile() {
    ImmutableSet<WorkspaceFileChange> workingSet =
        ImmutableSet.of(
            new WorkspaceFileChange(Operation.MODIFY, Path.of("com/example/Modified.java")));
    VcsState vcsState = new VcsState("workspaceId", "1", workingSet, Optional.empty());
    assertThat(vcsState.modifiedFiles()).containsExactly(Path.of("com/example/Modified.java"));
  }

  @Test
  public void testModifiedFiles_createdFile_returnsCreatedFile() {
    ImmutableSet<WorkspaceFileChange> workingSet =
        ImmutableSet.of(
            new WorkspaceFileChange(Operation.ADD, Path.of("com/example/Created.java")));
    VcsState vcsState = new VcsState("workspaceId", "1", workingSet, Optional.empty());
    assertThat(vcsState.modifiedFiles()).containsExactly(Path.of("com/example/Created.java"));
  }

  @Test
  public void testModifiedFiles_deletedFile_returnsEmpty() {
    ImmutableSet<WorkspaceFileChange> workingSet =
        ImmutableSet.of(
            new WorkspaceFileChange(Operation.DELETE, Path.of("com/example/Deleted.java")));
    VcsState vcsState = new VcsState("workspaceId", "1", workingSet, Optional.empty());
    assertThat(vcsState.modifiedFiles()).isEmpty();
  }

  @Test
  public void testModifiedFiles_allOperations_returnsOnlyModifiedAndCreatedFiles() {
    ImmutableSet<WorkspaceFileChange> workingSet =
        ImmutableSet.of(
            new WorkspaceFileChange(Operation.MODIFY, Path.of("com/example/Modified.java")),
            new WorkspaceFileChange(Operation.ADD, Path.of("com/example/Created.java")),
            new WorkspaceFileChange(Operation.DELETE, Path.of("com/example/Deleted.java")));
    VcsState vcsState = new VcsState("workspaceId", "1", workingSet, Optional.empty());
    assertThat(vcsState.modifiedFiles())
        .containsExactly(Path.of("com/example/Modified.java"), Path.of("com/example/Created.java"));
  }
}
