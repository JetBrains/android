/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildEventStreamConsumer;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.util.List;

/**
 * Runs a blaze build command associated with a {@link BlazeCommandRunConfiguration configuration},
 * typically as a 'before run configuration' task. The flags in requiredExtraBlazeFlags appear in
 * the command line after flags a user specifies in their configuration and will override those
 * flags. The flags in overridableExtraBlazeFlags appear before, and can be overridden by
 * user-specified flags.
 */
public final class BlazeBeforeRunCommandHelper {
  private static final String TASK_TITLE = "Blaze before run task";

  private BlazeBeforeRunCommandHelper() {}

  /**
   * Kicks off the blaze task, returning a corresponding {@link ListenableFuture}.
   *
   * <p>Runs the blaze command on the targets specified in the given {@code configuration}.
   */
  public static <T> ListenableFuture<T> runBlazeCommand(
      BlazeCommandName commandName,
      BlazeCommandRunConfiguration configuration,
      List<String> requiredExtraBlazeFlags,
      List<String> overridableExtraBlazeFlags,
      BlazeInvocationContext invocationContext,
      String progressMessage,
      BuildEventStreamConsumer<T> consumer) {
    return runBlazeCommand(
      configuration.getProject(),
      commandName,
      requiredExtraBlazeFlags,
      overridableExtraBlazeFlags,
      invocationContext,
      progressMessage,
      configuration.getTargetPatterns(),
      ((BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState()).getBlazeFlagsState().getFlagsForExternalProcesses(),
      consumer
    );
  }

  /**
   * Runs the given blaze command on the given list of {@code targets} instead of retrieving the
   * targets from the run {@code configuration}.
   */
  public static <T> ListenableFuture<T> runBlazeCommand(
    final Project project,
    BlazeCommandName commandName,
    List<String> requiredExtraBlazeFlags,
    List<String> overridableExtraBlazeFlags,
    BlazeInvocationContext invocationContext,
    String progressMessage,
    List<String> targets,
    List<String> flags,
    BuildEventStreamConsumer<T> consumer) {

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();

    return ProgressiveTaskWithProgressIndicator.builder(project, TASK_TITLE)
        .submitTaskWithResult(
            new ScopedTask<T>() {
              @Override
              protected T execute(BlazeContext context) {
                context
                    .push(
                        new ToolWindowScope.Builder(
                          project, new Task(project, TASK_TITLE))
                            .setPopupBehavior(
                                BlazeUserSettings.getInstance().getShowBlazeConsoleOnRun())
                            .setIssueParsers(
                                BlazeIssueParser.defaultIssueParsers(
                                  project, workspaceRoot, invocationContext.type()))
                            .build())
                    .push(
                        new ProblemsViewScope(
                          project, BlazeUserSettings.getInstance().getShowProblemsViewOnRun()));

                context.output(new StatusOutput(progressMessage));

                BuildSystem.BuildInvoker invoker =
                  Blaze.getBuildSystemProvider(project)
                    .getBuildSystem()
                    .getBuildInvoker(project);

                BlazeCommand.Builder command =
                    BlazeCommand.builder(commandName)
                        .addTargetStrings(targets)
                        .addBlazeFlags(overridableExtraBlazeFlags)
                        .addBlazeFlags(
                            BlazeFlags.blazeFlags(
                              project,
                                projectViewSet,
                                BlazeCommandName.BUILD,
                                invocationContext))
                      .addBlazeFlags(flags)
                      .addBlazeFlags(requiredExtraBlazeFlags);
                try {
                  return invoker.invoke(command, context, consumer);
                } catch (BuildException e) {
                  throw new RuntimeException(e);
                }
              }
            });
  }
}
