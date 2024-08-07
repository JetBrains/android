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
package com.google.idea.blaze.base.sync.aspects;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.model.AspectSyncProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.ProjectTargetData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncBuildResult;
import com.google.idea.blaze.base.sync.SyncProjectState;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** A blaze build interface used for mocking out the blaze layer in tests. */
public interface BlazeIdeInterface {

  static BlazeIdeInterface getInstance() {
    return ApplicationManager.getApplication().getService(BlazeIdeInterface.class);
  }

  /**
   * Parses the output intellij-info.txt files, returning an updated {@link ProjectTargetData}.
   *
   * @param mergeWithOldState If true, we overlay the given targets to the current rule map.
   */
  @Nullable
  ProjectTargetData updateTargetData(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      SyncProjectState projectState,
      BlazeSyncBuildResult buildResult,
      boolean mergeWithOldState,
      @Nullable AspectSyncProjectData oldProjectData);

  /**
   * Invokes a blaze build for the given output groups.
   *
   * @param outputGroups Set of {@link OutputGroup} to be generated in the build.
   */
  BlazeBuildOutputs build(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeVersionData blazeVersion,
      BuildInvoker invoker,
      ProjectViewSet projectViewSet,
      ShardedTargetList shardedTargets,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ImmutableSet<OutputGroup> outputGroups,
      BlazeInvocationContext blazeInvocationContext,
      boolean invokeParallel);
}
