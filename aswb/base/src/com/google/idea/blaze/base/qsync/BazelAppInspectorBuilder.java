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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/** An object that knows how to build dependencies for given targets */
public class BazelAppInspectorBuilder implements AppInspectorBuilder {

  protected final Project project;
  protected final BuildSystem buildSystem;

  public BazelAppInspectorBuilder(Project project, BuildSystem buildSystem) {
    this.project = project;
    this.buildSystem = buildSystem;
  }

  @Override
  public AppInspectorInfo buildAppInspector(BlazeContext context, Set<Label> buildTargets)
      throws IOException, BuildException {
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
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
              .addBlazeFlags(buildTargets.stream().map(Label::toString).collect(toImmutableList()))
              .addBlazeFlags(buildResultHelper.getBuildFlags())
              .addBlazeFlags(additionalBlazeFlags);

      BlazeBuildOutputs outputs =
          invoker.getCommandRunner().run(project, builder, buildResultHelper, context);
      BazelExitCodeException.throwIfFailed(builder, outputs.buildResult);

      return createAppInspectorInfo(outputs);
    }
  }

  private AppInspectorInfo createAppInspectorInfo(BlazeBuildOutputs blazeBuildOutputs) {
    ImmutableList<OutputArtifact> appInspectorJars =
        blazeBuildOutputs.getOutputGroupArtifacts(s -> s.contains("default"));

    return AppInspectorInfo.create(appInspectorJars, blazeBuildOutputs.buildResult.exitCode);
  }
}
