/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.common.vcs.VcsState;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/** Provides a {@link BlazeVcsHandlerProvider} for integration tests. */
public class MockBlazeVcsHandlerProvider implements BlazeVcsHandlerProvider {

  private final WorkingSet workingSet;
  private final ImmutableMap<WorkspacePath, String> upstreamContent;

  public MockBlazeVcsHandlerProvider() {
    workingSet = new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of());
    upstreamContent = ImmutableMap.of();
  }

  public MockBlazeVcsHandlerProvider(
      WorkingSet workingSet, Map<WorkspacePath, String> upstreamContent) {
    this.workingSet = workingSet;
    this.upstreamContent = ImmutableMap.copyOf(upstreamContent);
  }

  @Override
  public String getVcsName() {
    return "Mock";
  }

  @Override
  public boolean handlesProject(Project project, WorkspaceRoot workspaceRoot) {
    return true;
  }

  @Override
  public BlazeVcsHandler getHandlerForProject(Project p) {
    return new MockBlazeVcsHandler();
  }

  class MockBlazeVcsHandler implements BlazeVcsHandler {

    @Override
    public ListenableFuture<WorkingSet> getWorkingSet(
        BlazeContext context, ListeningExecutorService executor) {
      return immediateFuture(workingSet);
    }

    @Override
    public ListenableFuture<String> getUpstreamContent(
        BlazeContext context, WorkspacePath path, ListeningExecutorService executor) {
      return immediateFuture(upstreamContent.getOrDefault(path, ""));
    }

    @Override
    public Optional<ListenableFuture<String>> getUpstreamVersion(
        BlazeContext context, ListeningExecutorService executor) {
      return Optional.of(immediateFuture(""));
    }

    @Nullable
    @Override
    public BlazeVcsSyncHandler createSyncHandler() {
      return null;
    }

    @Override
    public Optional<VcsState> vcsStateForWorkspaceStatus(Map<String, String> workspaceStatus) {
      return Optional.empty();
    }

    @Override
    public Optional<ImmutableSet<Path>> diffVcsState(VcsState current, VcsState previous) {
      return Optional.empty();
    }
  }
}
