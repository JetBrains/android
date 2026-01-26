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

import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.BuildResult.Status;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.SyncFlagsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput.Prefix;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedBuildProgressTracker;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.UUID;

/** Implementation of BlazeIdeInterface based on aspects. */
public class BlazeIdeInterfaceAspectsImpl implements BlazeIdeInterface {

  private static final BoolExperiment noFakeStampExperiment =
      new BoolExperiment("blaze.sync.nofake.stamp.data", true);

  @Override
  public BlazeBuildOutputs build(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeVersionData blazeVersion,
      BuildInvoker invoker,
      ProjectViewSet projectViewSet,
      List<? extends String> targets,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      BlazeInvocationContext blazeInvocationContext) {

    final ShardedBuildProgressTracker progressTracker =
        new ShardedBuildProgressTracker(1);

    // Sync only flags (sync_only) override build_flags, so log them to warn the users
    List<String> syncOnlyFlags =
        BlazeFlags.expandBuildFlags(projectViewSet.listItems(SyncFlagsSection.KEY));
    if (!syncOnlyFlags.isEmpty()) {
      String message =
          String.format(
              "Sync flags (`%s`) specified in the project view file will override the build"
                  + " flags set in blazerc configurations or general build flags in the"
                  + " project view file.",
              String.join(" ", syncOnlyFlags));
      // Print to both summary and print outputs (i.e. main and subtask window of blaze console)
      context.output(SummaryOutput.output(Prefix.INFO, message).dedupe());
      context.output(PrintOutput.log(message));
    }
    // Fetching blaze flags here using parent context, to avoid duplicate fetch for every shard.
    List<String> additionalBlazeFlags =
        BlazeFlags.blazeFlags(
          project, BlazeCommandName.BUILD, blazeInvocationContext);
    return Scope.push(
        context,
        (childContext) -> {
          Task task =
              createTask(
                  project,
                  context,
                  "Build shard " + StringUtil.first(UUID.randomUUID().toString(), 8, true)
              );
          // we use context (rather than childContext) here since the shard state relates to
          // the parent task (which encapsulates all the build shards).

          setupToolWindow(project, childContext, workspaceRoot, task);
          progressTracker.onBuildStarted(context);

          try {
            BlazeBuildOutputs result1 =
                runBuildForTargets(
                    invoker,
                    childContext,
                    targets,
                    additionalBlazeFlags
                );
            printShardFinishedSummary(context, task.getName(), result1, invoker);
            return result1;
          } catch (BuildException e) {
            context.handleException("Failed to build targets", e);
            return BlazeBuildOutputs.noOutputs(BuildResult.FATAL_ERROR);
          } finally {
            progressTracker.onBuildCompleted(context);
          }
        });
  }

  /* Prints summary only for failed shards */
  private void printShardFinishedSummary(
      BlazeContext context,
      String taskName,
      BlazeBuildOutputs result,
      BuildInvoker invoker) {
    if (result.buildResult().status == Status.SUCCESS) {
      return;
    }
    StringBuilder outputText = new StringBuilder();
    outputText.append(
        String.format(
            "%s finished with %s errors. ",
            taskName, result.buildResult().status == Status.BUILD_ERROR ? "build" : "fatal"));
    String invocationId = result.buildId();
    // is built and not when buildResults are combined
    invoker
        .getBuildSystem()
        .getInvocationLink(invocationId)
        .ifPresent(link -> outputText.append(String.format("See build results at %s", link)));
    context.output(SummaryOutput.error(Prefix.TIMESTAMP, outputText.toString()));
  }

  private static Task createTask(
      Project project, BlazeContext parentContext, String taskName) {
    ToolWindowScope parentToolWindowScope = parentContext.getScope(ToolWindowScope.class);
    Task parentTask = parentToolWindowScope != null ? parentToolWindowScope.getTask() : null;
    return new Task(project, taskName, parentTask);
  }

  private static void setupToolWindow(
      Project project, BlazeContext childContext, WorkspaceRoot workspaceRoot, Task task) {
    ContextType contextType = ContextType.Other;
    childContext.push(
        new ToolWindowScope.Builder(project, task)
            .setIssueParsers(
                BlazeIssueParser.defaultIssueParsers(project, workspaceRoot, contextType))
            .build());
  }

  /** Runs a blaze build for the given output groups. */
  private static BlazeBuildOutputs runBuildForTargets(
      BuildInvoker invoker,
      BlazeContext context,
      List<? extends String> targets,
      List<String> additionalBlazeFlags)
      throws BuildException {

    BlazeCommand.Builder builder = BlazeCommand.builder(BlazeCommandName.BUILD);
    builder
        .addTargetStrings(targets)
        .addBlazeFlags(BlazeFlags.KEEP_GOING)
        .addBlazeFlags(BlazeFlags.DISABLE_VALIDATIONS) // b/145245918: don't run lint during sync
        .addBlazeFlags(additionalBlazeFlags);

    // b/236031309: Sync builds that use rabbit-cli rely on build-changelist.txt being populated
    // with the correct build request id. We force Blaze to emit the correct build-changelist.
    if (noFakeStampExperiment.getValue() && invoker.getType() == BuildBinaryType.RABBIT) {
      builder.addBlazeFlags("--nofake_stamp_data");
    }

    return invoker.invoke(
        builder,
        context,
        streamProvider ->
          BlazeBuildOutputs.fromParsedBepOutput(
            BuildResultParser.getBuildOutput(streamProvider, Interners.STRING)));
  }
}
