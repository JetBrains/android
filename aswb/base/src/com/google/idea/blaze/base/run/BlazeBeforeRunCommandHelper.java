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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.TempDirectoryProvider;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
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
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.toolwindow.Task;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.file.Path;
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
  public static ListenableFuture<BuildResult> runBlazeCommand(
      BlazeCommandName commandName,
      BlazeCommandRunConfiguration configuration,
      BuildResultHelper buildResultHelper,
      List<String> requiredExtraBlazeFlags,
      List<String> overridableExtraBlazeFlags,
      BlazeInvocationContext invocationContext,
      String progressMessage) {
    return runBlazeCommand(
        commandName,
        configuration,
        buildResultHelper,
        requiredExtraBlazeFlags,
        overridableExtraBlazeFlags,
        invocationContext,
        progressMessage,
        configuration.getTargets());
  }

  /**
   * Runs the given blaze command on the given list of {@code targets} instead of retrieving the
   * targets from the run {@code configuration}.
   */
  public static ListenableFuture<BuildResult> runBlazeCommand(
      BlazeCommandName commandName,
      BlazeCommandRunConfiguration configuration,
      BuildResultHelper buildResultHelper,
      List<String> requiredExtraBlazeFlags,
      List<String> overridableExtraBlazeFlags,
      BlazeInvocationContext invocationContext,
      String progressMessage,
      ImmutableList<TargetExpression> targets) {

    Project project = configuration.getProject();
    BlazeCommandRunConfigurationCommonState handlerState =
        (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();

    String binaryPath =
        handlerState.getBlazeBinaryState().getBlazeBinary() != null
            ? handlerState.getBlazeBinaryState().getBlazeBinary()
            : Blaze.getBuildSystemProvider(project).getBinaryPath(project);

    return ProgressiveTaskWithProgressIndicator.builder(project, TASK_TITLE)
        .submitTaskWithResult(
            new ScopedTask<BuildResult>() {
              @Override
              protected BuildResult execute(BlazeContext context) {
                context
                    .push(
                        new ToolWindowScope.Builder(
                                project, new Task(project, TASK_TITLE, Task.Type.BEFORE_LAUNCH))
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

                BlazeCommand.Builder command =
                    BlazeCommand.builder(binaryPath, commandName)
                        .addTargets(targets)
                        .addBlazeFlags(overridableExtraBlazeFlags)
                        .addBlazeFlags(
                            BlazeFlags.blazeFlags(
                                project,
                                projectViewSet,
                                BlazeCommandName.BUILD,
                                context,
                                invocationContext))
                        .addBlazeFlags(
                            handlerState.getBlazeFlagsState().getFlagsForExternalProcesses())
                        .addBlazeFlags(requiredExtraBlazeFlags)
                        .addBlazeFlags(buildResultHelper.getBuildFlags());

                int exitCode =
                    ExternalTask.builder(workspaceRoot)
                        .addBlazeCommand(command.build())
                        .context(context)
                        .stderr(
                            LineProcessingOutputStream.of(
                                BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(
                                    context)))
                        .build()
                        .run();
                return BuildResult.fromExitCode(exitCode);
              }
            });
  }

  /** Creates a temporary output file to write the shell script to. */
  public static Path createScriptPathFile() throws IOException {
    Path tempDir = TempDirectoryProvider.getInstance().getTempDirectory();
    Path tempFile =
        FileOperationProvider.getInstance().createTempFile(tempDir, "blaze-script-", "");
    tempFile.toFile().deleteOnExit();
    return tempFile;
  }
}
