/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.vcs.git;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Vcs diff provider for git. */
public class GitWorkingSetProvider {

  private static final Logger logger = Logger.getInstance(GitWorkingSetProvider.class);

  /**
   * Finds all changes between HEAD and the git commit specified by the provided SHA.<br>
   * Returns null if an error occurred.
   */
  @Nullable
  public static WorkingSet calculateWorkingSet(
      WorkspaceRoot workspaceRoot, String upstreamSha, BlazeContext context) {

    String gitRoot = getConsoleOutput(workspaceRoot, "git", "rev-parse", "--show-toplevel");
    if (gitRoot == null) {
      return null;
    }
    GitStatusLineProcessor processor = new GitStatusLineProcessor(workspaceRoot, gitRoot);
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    // Do a git diff to find all modified files we know about
    int retVal =
        ExternalTask.builder(workspaceRoot)
            .args("git", "diff", "--name-status", "--no-renames", upstreamSha)
            .context(context)
            .stdout(LineProcessingOutputStream.of(processor))
            .stderr(stderr)
            .build()
            .run(new TimingScope("GitDiff", EventType.Other));
    if (retVal != 0) {
      logger.error(stderr);
      return null;
    }

    // Finally list all untracked files, as they're not caught by the git diff step above
    String untrackedFilesOutput =
        getConsoleOutput(workspaceRoot, "git", "ls-files", "--others", "--exclude-standard");
    if (untrackedFilesOutput == null) {
      return null;
    }

    List<WorkspacePath> untrackedFiles =
        Arrays.asList(untrackedFilesOutput.split("\n"))
            .stream()
            .filter(s -> !Strings.isNullOrEmpty(s))
            .filter(WorkspacePath::isValid)
            .map(WorkspacePath::new)
            .collect(Collectors.toList());

    return new WorkingSet(
        ImmutableList.<WorkspacePath>builder()
            .addAll(processor.addedFiles)
            .addAll(untrackedFiles)
            .build(),
        ImmutableList.copyOf(processor.modifiedFiles),
        ImmutableList.copyOf(processor.deletedFiles));
  }

  /** @return the console output, in string form, or null if there was a non-zero exit code. */
  @Nullable
  private static String getConsoleOutput(WorkspaceRoot workspaceRoot, String... commands) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int retVal =
        ExternalTask.builder(workspaceRoot)
            .args(commands)
            .stdout(stdout)
            .stderr(stderr)
            .build()
            .run();
    if (retVal != 0) {
      logger.error(stderr);
      return null;
    }
    return StringUtil.trimEnd(stdout.toString(), "\n");
  }
}
