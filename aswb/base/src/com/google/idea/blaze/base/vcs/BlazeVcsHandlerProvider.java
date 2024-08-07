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
package com.google.idea.blaze.base.vcs;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.VcsStateDiffer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/** VCS handler provider. Provides the state of the VCS system. */
public interface BlazeVcsHandlerProvider {
  ExtensionPointName<BlazeVcsHandlerProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.VcsHandler");

  @Nullable
  static BlazeVcsHandlerProvider vcsHandlerProviderForProject(Project project) {
    return BlazeVcsHandlerCache.vcsHandlerProviderForProject(project);
  }

  @Nullable
  static BlazeVcsHandler vcsHandlerForProject(Project project) {
    return BlazeVcsHandlerCache.vcsHandlerForProject(project);
  }

  /**
   * Exception that may be thrown from a future returned by {@link BlazeVcsHandlerProvider} if an
   * operation fails.
   */
  class VcsException extends BuildException {
    public VcsException(String message) {
      super(message);
    }

    public VcsException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Returns the name of this VCS, eg. "git" or "hg" */
  String getVcsName();

  /** Returns whether this vcs handler can manage this project */
  boolean handlesProject(Project project, WorkspaceRoot workspaceRoot);

  BlazeVcsHandler getHandlerForProject(Project p);

  /** VCS handler for a project. */
  interface BlazeVcsHandler {

    /**
     * Returns the working set of modified files compared to the base/upstream revision, as returned
     * by {@link #getUpstreamVersion}.
     */
    ListenableFuture<WorkingSet> getWorkingSet(
        BlazeContext context, ListeningExecutorService executor);

    /** Returns the original file content of a file path from "upstream". */
    ListenableFuture<String> getUpstreamContent(
        BlazeContext context, WorkspacePath path, ListeningExecutorService executor);

    /**
     * Returns the upstream/base version that the client is based on.
     *
     * <p>The upstream version is an opaque string that should only be tested for equality. All
     * files in the workspace that differ compared to the base revision are returned by {@link
     * #getWorkingSet}.
     *
     * @return The upstream version as a future, or empty if ths VCS does not support this
     *     functionality.
     */
    Optional<ListenableFuture<String>> getUpstreamVersion(
        BlazeContext context, ListeningExecutorService executor);

    /** Optionally creates a sync handler to perform vcs-specific computation during sync. */
    @Nullable
    BlazeVcsSyncHandler createSyncHandler();

    default Optional<ListenableFuture<VcsState>> getVcsState(
        BlazeContext context, ListeningExecutorService executor) {
      Optional<ListenableFuture<String>> upstreamRev = getUpstreamVersion(context, executor);
      if (!upstreamRev.isPresent()) {
        return Optional.empty();
      }
      ListenableFuture<String> upstreamFuture = upstreamRev.get();
      ListenableFuture<WorkingSet> workingSet = getWorkingSet(context, executor);
      return Optional.of(
          Futures.whenAllSucceed(upstreamFuture, workingSet)
              .call(
                  () ->
                      new VcsState(
                          "default",
                          Futures.getDone(upstreamFuture),
                          Futures.getDone(workingSet).toWorkspaceFileChanges(),
                          Optional.empty()),
                  executor));
    }

    Optional<VcsState> vcsStateForSourceUri(String sourceUri) throws BuildException;

    /**
     * Diffs two VCS states from different points in time.
     *
     * @param current The more recent VCS state
     * @param previous An earlier VCS state
     * @return All files that changed between the points at which the two states correspond to, as
     *     workspace relative paths. If this VCS does not support diffing two states in this way,
     *     returns empty. If nothing has changed between the two points, an empty list is returned.
     */
    Optional<ImmutableSet<Path>> diffVcsState(VcsState current, VcsState previous)
        throws BuildException;

    default VcsStateDiffer getVcsStateDiffer() {
      return this::diffVcsState;
    }
  }

  /** Sync handler that performs VCS specific computation. */
  interface BlazeVcsSyncHandler {
    enum ValidationResult {
      OK,
      Error,
      RestartSync, // The sync process needs restarting
    }

    /**
     * Updates the vcs state of the project.
     *
     * @return True for OK, false to abort the sync process.
     */
    boolean update(BlazeContext context, ListeningExecutorService executor);

    /** Returns a custom workspace path resolver for this vcs. */
    @Nullable
    WorkspacePathResolver getWorkspacePathResolver();

    /** Validates the project view. Can cause sync to fail or restart. */
    ValidationResult validateProjectView(BlazeContext context, ProjectViewSet projectViewSet);
  }
}
