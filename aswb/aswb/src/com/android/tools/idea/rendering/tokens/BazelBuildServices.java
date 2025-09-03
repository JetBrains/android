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
package com.android.tools.idea.rendering.tokens;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener.BuildMode;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener.BuildResult;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.DependencyTracker.DependencyBuildRequest;
import com.google.idea.blaze.base.qsync.DependencyTracker.DependencyBuildRequest.RequestType;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.OperationType;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup;
import com.google.idea.blaze.base.qsync.action.TargetDisambiguationAnchors;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.guava.ListenableFutureKt;
import org.jetbrains.annotations.NotNull;

// TODO: b/418844903 - Update the artifact manager
final class BazelBuildServices implements BuildServices<BazelBuildTargetReference> {
  private final Collection<BuildListener> listeners = new CopyOnWriteArrayList<>();

  /**
   * Executed by an application pool thread and the EDT
   */
  void add(BuildListener listener) {
    listeners.add(listener);
  }

  /**
   * Executed by the EDT
   */
  @UiThread
  void remove(BuildListener listener) {
    listeners.remove(listener);
  }

  @Override
  public @NotNull BuildStatus getLastCompileStatus(@NotNull BazelBuildTargetReference target) {
    // TODO: b/409383880 - Implement this
    return BuildStatus.UNKNOWN;
  }

  /**
   * Executed by an application pool thread
   */
  @Override
  public void buildArtifacts(@NotNull Collection<? extends @NotNull BazelBuildTargetReference> targets) {
    buildArtifactsAsync(Iterables.getOnlyElement(targets));
  }

  /**
   * Executed by an application pool thread
   */
  @VisibleForTesting
  Deferred<Boolean> buildArtifactsAsync(BazelBuildTargetReference target) {
    var project = target.getProject();
    var file = target.getFile();
    var scope = QuerySyncActionStatsScope.createForFile(project, BazelBuildServices.class, null, file);

    return new BuildDependenciesHelper(project).determineTargetsAndRun(
      WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, List.of(file)),
      BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt(popup -> popup.showCenteredInCurrentWindow(project)),
      TargetDisambiguationAnchors.NONE,
      scope,
      labels -> buildAndRefresh(project, labels, scope));
  }

  /**
   * Executed by the EDT
   */
  @UiThread
  private Deferred<Boolean> buildAndRefresh(Project project, Set<Label> labels, QuerySyncActionStatsScope scope) {
    var manager = QuerySyncManager.getInstance(project);

    var buildAndRefresh = QuerySyncManager.createOperation("Build & Refresh",
                                                           "Building and refreshing",
                                                           OperationType.BUILD_DEPS,
                                                           context -> buildAndRefresh(manager, context, labels));

    var buildAndRefreshFuture = manager.runOperation(scope, TaskOrigin.USER_ACTION, buildAndRefresh);

    var newBuildResultFuture = Futures.transform(buildAndRefreshFuture,
                                                 succeeded -> newBuildResult(succeeded, project),
                                                 MoreExecutors.directExecutor());

    listeners.forEach(listener -> listener.buildStarted(BuildMode.COMPILE, newBuildResultFuture));

    return ListenableFutureKt.asDeferred(buildAndRefreshFuture);
  }

  /**
   * Executed by the Blaze executor
   */
  private static void buildAndRefresh(@NotNull QuerySyncManager manager,
                                      @NotNull BlazeContext context,
                                      @NotNull Set<@NotNull Label> labels) throws BuildException {
    var tracker = manager.getDependencyTracker();
    assert tracker != null;

    var builder = tracker.getBuilder();
    var groups = DependencyBuildRequest.getOutputGroups(List.of(QuerySyncLanguage.JVM), RequestType.FILE_PREVIEWS);

    try {
      builder.build(context, labels, groups);
    }
    catch (IOException exception) {
      throw new BuildException(exception);
    }
  }

  /**
   * Executed by the Blaze executor
   */
  private BuildResult newBuildResult(boolean succeeded, Project project) {
    return new BuildResult(succeeded ? BuildStatus.SUCCESS : BuildStatus.FAILED, GlobalSearchScope.projectScope(project));
  }
}
