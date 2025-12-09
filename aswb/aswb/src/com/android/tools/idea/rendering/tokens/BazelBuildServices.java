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
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.RenderingServices;
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
import com.google.idea.blaze.base.run.RuntimeArtifactCache;
import com.google.idea.blaze.base.run.RuntimeArtifactKind;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.deps.OutputInfo;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.guava.ListenableFutureKt;
import org.jetbrains.annotations.NotNull;

// TODO: b/418844903 - Update the artifact manager
final class BazelBuildServices implements BuildServices<BazelBuildTargetReference> {
  private static final FeatureRolloutExperiment aswbComposablePreviews = new FeatureRolloutExperiment("aswb.composable.previews");

  private final Collection<BuildListener> listeners = new CopyOnWriteArrayList<>();
  private final Map<Key, BazelRenderingServices> keyToRenderingServicesMap = new ConcurrentHashMap<>();

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

  RenderingServices getRenderingServices(BazelBuildTargetReference target) {
    return aswbComposablePreviews.isEnabled()
           ? keyToRenderingServicesMap.computeIfAbsent(new Key(target), key -> new BazelRenderingServices())
           : new BazelRenderingServices();
  }

  @NotNull
  @Override
  public BuildStatus getLastCompileStatus(@NotNull BazelBuildTargetReference target) {
    // TODO: b/409383880 - Implement this
    return BuildStatus.UNKNOWN;
  }

  /**
   * Executed by an application pool thread
   */
  @Override
  public void buildArtifacts(@NotNull Collection<? extends BazelBuildTargetReference> targets) {
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
      labels -> buildAndRefresh(target, Iterables.getOnlyElement(labels), scope));
  }

  /**
   * Executed by the EDT
   */
  @UiThread
  private Deferred<Boolean> buildAndRefresh(BazelBuildTargetReference target, Label label, QuerySyncActionStatsScope scope) {
    var project = target.getProject();

    var buildAndRefresh = QuerySyncManager.createOperation("Build & Refresh",
                                                           "Building and refreshing",
                                                           OperationType.BUILD_DEPS,
                                                           context -> buildAndRefresh(target, context, label));

    var buildAndRefreshFuture = QuerySyncManager.getInstance(project).runOperation(scope, TaskOrigin.USER_ACTION, buildAndRefresh);

    var newBuildResultFuture = Futures.transform(buildAndRefreshFuture,
                                                 succeeded -> newBuildResult(succeeded, project),
                                                 MoreExecutors.directExecutor());

    listeners.forEach(listener -> listener.buildStarted(BuildMode.COMPILE, newBuildResultFuture));

    return ListenableFutureKt.asDeferred(buildAndRefreshFuture);
  }

  /**
   * Executed by the Blaze executor
   */
  private void buildAndRefresh(BazelBuildTargetReference target, BlazeContext context, Label label) throws BuildException {
    var tracker = QuerySyncManager.getInstance(target.getProject()).getDependencyTracker();
    assert tracker != null;

    var builder = tracker.getBuilder();
    var groups = DependencyBuildRequest.getOutputGroups(List.of(QuerySyncLanguage.JVM), RequestType.FILE_PREVIEWS);

    try {
      cacheOutput(target, builder.build(context, Set.of(label), groups), context);
    }
    catch (IOException exception) {
      throw new BuildException(exception);
    }
  }

  private void cacheOutput(BazelBuildTargetReference target, OutputInfo output, BlazeContext context) {
    var project = target.getProject();
    var path = target.getFileWorkspaceRelativePath();

    var cache = RuntimeArtifactCache.getInstance(project);
    var label = QuerySyncManager.getInstance(project).getCurrentSnapshot().orElseThrow().getGraph().sourceFileToLabel(path);

    var jars = cache.fetchArtifacts(label, output.getTransitiveRuntimeJars(), context, RuntimeArtifactKind.TRANSITIVE_RUNTIME_JAR);

    var externalJars = cache.fetchArtifacts(label,
                                            output.getExternalTransitiveRuntimeJars(),
                                            context,
                                            RuntimeArtifactKind.EXTERNAL_TRANSITIVE_RUNTIME_JAR);

    keyToRenderingServicesMap.put(new Key(target), new BazelRenderingServices(jars, externalJars));
  }

  /**
   * Executed by the Blaze executor
   */
  private BuildResult newBuildResult(boolean succeeded, Project project) {
    return new BuildResult(succeeded ? BuildStatus.SUCCESS : BuildStatus.FAILED, GlobalSearchScope.projectScope(project));
  }
}
