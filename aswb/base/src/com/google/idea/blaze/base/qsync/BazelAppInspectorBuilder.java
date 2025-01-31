/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.util.List;

/** An object that knows how to build dependencies for given targets */
public class BazelAppInspectorBuilder implements AppInspectorBuilder {

  protected final Project project;
  protected final BuildSystem buildSystem;

  public BazelAppInspectorBuilder(Project project, BuildSystem buildSystem) {
    this.project = project;
    this.buildSystem = buildSystem;
  }

  @Override
  public AppInspectorInfo buildAppInspector(BlazeContext context, Label buildTarget)
      throws BuildException {
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    List<String> additionalBlazeFlags =
        BlazeFlags.blazeFlags(
            project,
            projectViewSet,
            BlazeCommandName.BUILD,
            context,
            BlazeInvocationContext.OTHER_CONTEXT);

    BlazeCommand.Builder builder =
        BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
            .addBlazeFlags(buildTarget.toString())
            .addBlazeFlags(additionalBlazeFlags);

    try (BuildEventStreamProvider streamProvider = invoker.invoke(builder, context)) {
      BlazeBuildOutputs outputs =
          BlazeBuildOutputs.fromParsedBepOutput(
              BuildResultParser.getBuildOutput(streamProvider, Interners.STRING));
      BazelExitCodeException.throwIfFailed(builder, outputs.buildResult());
      return createAppInspectorInfo(outputs);
    }
  }

  private AppInspectorInfo createAppInspectorInfo(BlazeBuildOutputs blazeBuildOutputs) {
    ImmutableList<OutputArtifact> appInspectorJars =
        blazeBuildOutputs.getOutputGroupArtifacts("default");

    return AppInspectorInfo.create(appInspectorJars, blazeBuildOutputs.buildResult().exitCode);
  }
}
