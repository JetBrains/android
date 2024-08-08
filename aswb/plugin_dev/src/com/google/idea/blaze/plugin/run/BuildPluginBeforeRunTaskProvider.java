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
package com.google.idea.blaze.plugin.run;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.BlazeIcons;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * Builds the intellij_plugin jar via 'blaze build', for Blaze Intellij Plugin run configurations
 */
public final class BuildPluginBeforeRunTaskProvider
    extends BeforeRunTaskProvider<BuildPluginBeforeRunTaskProvider.Task> {
  public static final Key<Task> ID = Key.create("Blaze.Intellij.Plugin.BeforeRunTask");

  static class Task extends BeforeRunTask<Task> {
    private Task() {
      super(ID);
      setEnabled(true);
    }
  }

  private final Project project;

  public BuildPluginBeforeRunTaskProvider(Project project) {
    this.project = project;
  }

  @Override
  public Icon getIcon() {
    return BlazeIcons.Logo;
  }

  @Override
  public Icon getTaskIcon(Task task) {
    return BlazeIcons.Logo;
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }

  @Override
  public boolean configureTask(RunConfiguration runConfiguration, Task task) {
    return false;
  }

  @Override
  public Key<Task> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return taskName();
  }

  @Override
  public String getDescription(Task task) {
    return taskName();
  }

  private String taskName() {
    return Blaze.buildSystemName(project) + " build plugin before-run task";
  }

  @Override
  public final boolean canExecuteTask(RunConfiguration configuration, Task task) {
    return isValidConfiguration(configuration);
  }

  @Nullable
  @Override
  public Task createTask(RunConfiguration runConfiguration) {
    if (isValidConfiguration(runConfiguration)) {
      return new Task();
    }
    return null;
  }

  private static boolean isValidConfiguration(RunConfiguration runConfiguration) {
    return runConfiguration instanceof BlazeIntellijPluginConfiguration;
  }

  @Override
  public final boolean executeTask(
      final DataContext dataContext,
      final RunConfiguration configuration,
      final ExecutionEnvironment env,
      Task task) {
    if (!canExecuteTask(configuration, task)) {
      return false;
    }
    BlazeUserSettings userSettings = BlazeUserSettings.getInstance();
    return Scope.root(
        context -> {
          context
              .push(new ExperimentScope())
              .push(new ProblemsViewScope(project, userSettings.getShowProblemsViewOnRun()))
              .push(
                  new ToolWindowScope.Builder(
                          project,
                          new com.google.idea.blaze.base.toolwindow.Task(
                              project,
                              "Build Plugin Jar",
                              com.google.idea.blaze.base.toolwindow.Task.Type.BEFORE_LAUNCH))
                      .setPopupBehavior(userSettings.getShowBlazeConsoleOnRun())
                      .setIssueParsers(
                          BlazeIssueParser.defaultIssueParsers(
                              project,
                              WorkspaceRoot.fromProject(project),
                              ContextType.BeforeRunTask))
                      .build())
              .push(new IdeaLogScope());

          BlazeIntellijPluginDeployer deployer =
              env.getUserData(BlazeIntellijPluginDeployer.USER_DATA_KEY);
          if (deployer == null) {
            IssueOutput.error("Could not find BlazeIntellijPluginDeployer in env.").submit(context);
            return false;
          }
          deployer.buildStarted();

          final ProjectViewSet projectViewSet =
              ProjectViewManager.getInstance(project).getProjectViewSet();
          if (projectViewSet == null) {
            IssueOutput.error("Could not load project view. Please resync project").submit(context);
            return false;
          }

          final ScopedTask<Void> buildTask =
              new ScopedTask<Void>(context) {
                @Override
                protected Void execute(BlazeContext context) {
                  BlazeIntellijPluginConfiguration config =
                      (BlazeIntellijPluginConfiguration) configuration;
                  BlazeProjectData blazeProjectData =
                      BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
                  if (blazeProjectData == null) {
                    IssueOutput.error("Could not determine execution root").submit(context);
                    return null;
                  }

                  BuildInvoker invoker =
                      Blaze.getBuildSystemProvider(project)
                          .getBuildSystem()
                          .getBuildInvoker(project, context);
                  try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
                    BlazeCommand.Builder command =
                        BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
                            .addTargets(config.getTargets())
                            .addBlazeFlags(
                                BlazeFlags.blazeFlags(
                                    project,
                                    projectViewSet,
                                    BlazeCommandName.BUILD,
                                    context,
                                    BlazeInvocationContext.runConfigContext(
                                        ExecutorType.fromExecutor(env.getExecutor()),
                                        config.getType(),
                                        true)))
                            .addBlazeFlags(
                                config.getBlazeFlagsState().getFlagsForExternalProcesses())
                            .addExeFlags(config.getExeFlagsState().getFlagsForExternalProcesses())
                            .addBlazeFlags(buildResultHelper.getBuildFlags());

                    if (command == null || context.hasErrors() || context.isCancelled()) {
                      return null;
                    }
                    SaveUtil.saveAllFiles();
                    BlazeBuildOutputs outputs =
                        invoker
                            .getCommandRunner()
                            .run(project, command, buildResultHelper, context);
                    if (!outputs.buildResult.equals(BuildResult.SUCCESS)) {
                      context.setHasError();
                    }
                    ListenableFuture<Void> unusedFuture =
                        FileCaches.refresh(
                            project, context, BlazeBuildOutputs.noOutputs(outputs.buildResult));
                    try {
                      deployer.reportBuildComplete(outputs);
                    } catch (GetArtifactsException e) {
                      IssueOutput.error("Failed to get build artifacts: " + e.getMessage())
                          .submit(context);
                      return null;
                    }
                    return null;
                  } catch (BuildException e) {
                    context.handleException("Failed to build", e);
                    return null;
                  }
                }
              };

          ListenableFuture<Void> buildFuture =
              ProgressiveTaskWithProgressIndicator.builder(
                      project, "Executing blaze build for IntelliJ plugin jar")
                  .submitTaskWithResult(buildTask);

          try {
            Futures.getChecked(buildFuture, ExecutionException.class);
          } catch (ExecutionException e) {
            context.setHasError();
          } catch (CancellationException e) {
            context.setCancelled();
          }

          return !context.hasErrors() && !context.isCancelled();
        });
  }
}
