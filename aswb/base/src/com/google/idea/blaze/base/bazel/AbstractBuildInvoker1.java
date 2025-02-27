/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Base class for implementations of {@link BuildInvoker} that provides getters and deals with
 * running `blaze info`.
 */
// TODO(b/374906681): Replace @link{AbstractBuildInvoker} and its usages by this class
public abstract class AbstractBuildInvoker1 implements BuildInvoker {
  protected final Project project;
  private final String binaryPath;
  private final BuildSystem buildSystem;
  private BlazeInfo blazeInfo;

  public AbstractBuildInvoker1(Project project, BuildSystem buildSystem, String binaryPath) {
    this.project = project;
    this.buildSystem = buildSystem;
    this.binaryPath = binaryPath;
  }

  @Override
  public String getBinaryPath() {
    return this.binaryPath;
  }

  @Override
  @Nullable
  public synchronized BlazeInfo getBlazeInfo(BlazeContext blazeContext) throws SyncFailedException {
    if (blazeInfo == null) {
      blazeInfo = getBlazeInfoResult(blazeContext);
    }
    return blazeInfo;
  }

  private BlazeInfo getBlazeInfoResult(BlazeContext blazeContext) throws SyncFailedException {
    ListenableFuture<BlazeInfo> future = runBlazeInfo(blazeContext);
    FutureUtil.FutureResult<BlazeInfo> result =
      FutureUtil.waitForFuture(blazeContext, future)
        .timed(buildSystem.getName() + "Info", TimingScope.EventType.BlazeInvocation)
        .withProgressMessage(String.format("Running %s info...", buildSystem.getName()))
        .onError(String.format("Could not run %s info", buildSystem.getName()))
        .run();
    if (result.success()) {
      return result.result();
    }
    throw new SyncFailedException(
      String.format("Failed to run `%s info`", getBinaryPath()), result.exception());
  }

  private ListenableFuture<BlazeInfo> runBlazeInfo(BlazeContext blazeContext) {
    ProjectViewSet viewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (viewSet == null) {
      // defer the failure until later when it can be handled more easily:
      return Futures.immediateFailedFuture(new IllegalStateException("Empty project view set"));
    }
    List<String> syncFlags =
      BlazeFlags.blazeFlags(
        project,
        viewSet,
        BlazeCommandName.INFO,
        blazeContext,
        BlazeInvocationContext.SYNC_CONTEXT);
    return BlazeInfoRunner.getInstance()
      .runBlazeInfo(project, this, blazeContext, buildSystem.getName(), syncFlags);
  }

  @Override
  public BuildResultHelper createBuildResultHelper() {
    throw new UnsupportedOperationException("This method should not be called, this invoker does not support build result helpers.");
  }

  @Override
  public BlazeCommandRunner getCommandRunner() {
    throw new UnsupportedOperationException("This method should not be called, this invoker does not support command runners.");
  }

  @Override
  public BuildSystem getBuildSystem() {
    return this.buildSystem;
  }
}
