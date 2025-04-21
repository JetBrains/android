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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static kotlinx.coroutines.CompletableDeferredKt.CompletableDeferred;

import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus;
import com.android.tools.idea.rendering.BuildTargetReference;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices;
import com.google.common.collect.MoreCollectors;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup;
import com.google.idea.blaze.base.qsync.action.TargetDisambiguationAnchors.WorkingSet;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.module.Module;
import java.util.Collection;
import java.util.Set;
import kotlinx.coroutines.Deferred;
import org.jetbrains.annotations.NotNull;

final class BazelBuildServices implements BuildServices<BazelBuildTargetReference> {
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
    var project = targets.stream()
      .map(BuildTargetReference::getModule)
      .map(Module::getProject)
      .distinct()
      .collect(MoreCollectors.onlyElement());

    var helper = new BuildDependenciesHelper(project);

    var files = targets.stream()
      .map(BazelBuildTargetReference::getFile)
      .collect(toImmutableList());

    helper.determineTargetsAndRun(WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, files),
                                  BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt(
                                    popup -> popup.showCenteredInCurrentWindow(project)),
                                  new WorkingSet(helper),
                                  QuerySyncActionStatsScope.createForFiles(BazelBuildServices.class, null, files),
                                  BazelBuildServices::invoke);
  }

  /**
   * Executed by the EDT
   */
  // TODO: b/412450450 - Give this a better name and implement it
  private static @NotNull Deferred<@NotNull Boolean> invoke(@NotNull Set<@NotNull Label> labels) {
    return CompletableDeferred(false);
  }
}
