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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.execution.BazelGuard;
import com.google.idea.blaze.base.execution.ExecutionDeniedException;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.common.vcs.VcsState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/** Vcs diff provider for git */
public class GitBlazeVcsHandlerProvider implements BlazeVcsHandlerProvider {

  private static final Logger logger = Logger.getInstance(GitBlazeVcsHandlerProvider.class);

  @Override
  public String getVcsName() {
    return "git";
  }

  @Override
  public boolean handlesProject(Project project, WorkspaceRoot workspaceRoot) {
    try {
      BazelGuard.checkExtensionsIsExecutionAllowed(project);
    } catch (ExecutionDeniedException e) {
      logger.warn("Git provider is not allowed because of", e);
      return false;
    }
    return Blaze.getBuildSystemName(project) == BuildSystemName.Bazel
        && isGitRepository(workspaceRoot)
        && tracksRemote(workspaceRoot);
  }

  @Override
  public BlazeVcsHandler getHandlerForProject(Project project) {
    return new GitBlazeVcsHandler(project);
  }

  static class GitBlazeVcsHandler implements BlazeVcsHandler {

    private final WorkspaceRoot workspaceRoot;

    GitBlazeVcsHandler(Project project) {
      this.workspaceRoot = WorkspaceRoot.fromProject(project);
    }

    @Override
    public ListenableFuture<WorkingSet> getWorkingSet(
        BlazeContext context, ListeningExecutorService executor) {
      return executor.submit(
          () -> {
            String upstreamSha = getUpstreamSha(workspaceRoot, false);
            if (upstreamSha == null) {
              return null;
            }
            return GitWorkingSetProvider.calculateWorkingSet(workspaceRoot, upstreamSha, context);
          });
    }

    @Nullable
    @Override
    public BlazeVcsSyncHandler createSyncHandler() {
      return null;
    }

    @Override
    public Optional<ImmutableSet<Path>> diffVcsState(VcsState current, VcsState previous) {
      return Optional.empty();
    }

    @Override
    public ListenableFuture<String> getUpstreamContent(
        BlazeContext context, WorkspacePath path, ListeningExecutorService executor) {
      return executor.submit(() -> getGitUpstreamContent(workspaceRoot, path));
    }

    @Override
    public Optional<ListenableFuture<String>> getUpstreamVersion(
        BlazeContext context, ListeningExecutorService executor) {
      return Optional.of(executor.submit(() -> getUpstreamSha(workspaceRoot)));
    }

    @Override
    public Optional<VcsState> vcsStateForSourceUri(String sourceUri) {
      return Optional.empty();
    }
  }

  private static String getGitUpstreamContent(WorkspaceRoot workspaceRoot, WorkspacePath path) {
    String upstreamSha = getUpstreamSha(workspaceRoot, false);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ExternalTask.builder(workspaceRoot)
        .args(
            "git",
            "show",
            // Normally "For plain blobs, it shows the plain contents.", but let's add some
            // options to be a bit more paranoid.
            "--no-color",
            "--no-expand-tabs",
            "--no-notes",
            "--no-textconv",
            String.format("%s:./%s", upstreamSha, path.relativePath()))
        .stdout(outputStream)
        .build()
        .run();
    return outputStream.toString();
  }

  private static boolean isGitRepository(WorkspaceRoot workspaceRoot) {
    // TODO: What if the git repo root is a parent directory of the workspace root?
    // Just call 'git rev-parse --is-inside-work-tree' or similar instead?
    File gitDir = new File(workspaceRoot.directory(), ".git");
    return FileOperationProvider.getInstance().isDirectory(gitDir);
  }

  /**
   * If we're not on a git branch which tracks a remote, we have no way of determining a WorkingSet.
   */
  private static boolean tracksRemote(WorkspaceRoot workspaceRoot) {
    return getUpstreamSha(workspaceRoot, true) != null;
  }

  /**
   * Returns the git commit SHA corresponding to the most recent commit in the current branch which
   * matches a commit in the currently-tracked remote branch, or null if that fails for any reason.
   */
  @Nullable
  private static String getUpstreamSha(WorkspaceRoot workspaceRoot, boolean suppressErrors) {
    try {
      return getUpstreamSha(workspaceRoot);
    } catch (VcsException e) {
      if (!suppressErrors) {
        logger.warn(e.getMessage());
      }
      return null;
    }
  }

  /**
   * Returns the git commit SHA corresponding to the most recent commit in the current branch which
   * matches a commit in the currently-tracked remote branch.
   *
   * @throws VcsException if we cannot get the SHA.
   */
  private static String getUpstreamSha(WorkspaceRoot workspaceRoot) throws VcsException {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    String[] args = new String[] {"git", "rev-parse", "@{u}"};
    int retVal =
        ExternalTask.builder(workspaceRoot).args(args).stdout(stdout).stderr(stderr).build().run();
    if (retVal != 0) {
      throw new VcsException(
          "Could not obtain upstream sha: `"
              + Joiner.on(' ').join(args)
              + "` exited with "
              + retVal
              + "; stderr: "
              + stderr);
    }
    return StringUtil.trimEnd(stdout.toString(), "\n");
  }
}
