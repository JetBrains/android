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
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.common.vcs.VcsState;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Used for bazel projects, when no other vcs handler can be found. Fallback to returning a null
 * working set.
 */
public class FallbackBlazeVcsHandlerProvider implements BlazeVcsHandlerProvider {

  @Override
  public String getVcsName() {
    return "Generic VCS Handler";
  }

  @Override
  public boolean handlesProject(Project project, WorkspaceRoot workspaceRoot) {
    return Blaze.getBuildSystemName(project) == BuildSystemName.Bazel;
  }

  @Override
  public BlazeVcsHandler getHandlerForProject(Project p) {
    return new FallbackBlazeVcsHandler();
  }

  static class FallbackBlazeVcsHandler implements BlazeVcsHandler {

    @Override
    public ListenableFuture<WorkingSet> getWorkingSet(
        BlazeContext context, ListeningExecutorService executor) {
      return Futures.immediateFuture(null);
    }

    @Override
    public ListenableFuture<String> getUpstreamContent(
        BlazeContext context, WorkspacePath path, ListeningExecutorService executor) {
      return Futures.immediateFuture("");
    }

    @Override
    public Optional<ListenableFuture<String>> getUpstreamVersion(
        BlazeContext context, ListeningExecutorService executor) {
      return Optional.empty();
    }

    @Nullable
    @Override
    public BlazeVcsSyncHandler createSyncHandler() {
      return null;
    }

    @Override
    public Optional<VcsState> vcsStateForSourceUri(String sourceUri) {
      return Optional.empty();
    }

    @Override
    public Optional<ImmutableSet<Path>> diffVcsState(VcsState current, VcsState previous) {
      return Optional.empty();
    }
  }
}
