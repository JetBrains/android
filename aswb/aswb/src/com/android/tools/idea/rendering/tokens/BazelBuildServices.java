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

import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus;
import com.android.tools.idea.rendering.BuildTargetReference;
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices;
import com.google.common.collect.MoreCollectors;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.module.Module;
import java.util.Collection;
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

    // TODO: b/409388569 - Do you need to pass the file that contains the previews to this?
    var scope = QuerySyncActionStatsScope.create(BazelBuildServices.class, null);

    QuerySyncManager.getInstance(project)
      .runBuild("Build & Refresh", null, scope, BazelBuildServices::buildAndRefresh, TaskOrigin.USER_ACTION);
  }

  /**
   * Executed by the Blaze executor
   */
  private static void buildAndRefresh(@NotNull BlazeContext context) {
    // TODO: b/409388814 - Implement this
    throw new UnsupportedOperationException();
  }
}
