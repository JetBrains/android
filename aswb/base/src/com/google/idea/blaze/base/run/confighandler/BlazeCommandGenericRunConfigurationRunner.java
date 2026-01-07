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
package com.google.idea.blaze.base.run.confighandler;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.issueparser.ToolWindowTaskIssueOutputFilter;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFetcher;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Interners;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.PrintOutput.OutputType;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generic runner for {@link BlazeCommandRunConfiguration}s, used as a fallback in the case where no
 * other runners are more relevant.
 */
public final class BlazeCommandGenericRunConfigurationRunner
    implements BlazeCommandRunConfigurationRunner {

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment environment) {
    return new BlazeCommandRunProfileState(environment);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment environment) {
    // Don't execute any tasks.
    return true;
  }

  /** {@link RunProfileState} for generic blaze commands. */
  public static class BlazeCommandRunProfileState extends CommandLineState {
    private static final int BLAZE_BUILD_INTERRUPTED = 8;
    private final BlazeCommandRunConfiguration configuration;
    private final BlazeCommandRunConfigurationCommonState handlerState;
    private final ImmutableList<Filter> consoleFilters;

    public BlazeCommandRunProfileState(ExecutionEnvironment environment) {
      super(environment);
      this.configuration = getConfiguration(environment);
      this.handlerState =
          (BlazeCommandRunConfigurationCommonState) configuration.getHandler().getState();
      Project project = environment.getProject();
      this.consoleFilters =
          ImmutableList.of(
              new UrlFilter(),
              ToolWindowTaskIssueOutputFilter.createWithDefaultParsers(
                  project,
                  WorkspaceRoot.fromProject(project),
                  BlazeInvocationContext.ContextType.RunConfiguration));
    }

    private static BlazeCommandRunConfiguration getConfiguration(ExecutionEnvironment environment) {
      return BlazeCommandRunConfigurationRunner.getConfiguration(environment);
    }

    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner<?> runner)
        throws ExecutionException {
      DefaultExecutionResult result = (DefaultExecutionResult) super.execute(executor, runner);
      return SmRunnerUtils.attachRerunFailedTestsAction(result);
    }

    @Override
    protected ProcessHandler startProcess() throws ExecutionException {
      Project project = configuration.getProject();
      BlazeImportSettings importSettings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();
      assert importSettings != null;

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      assert projectViewSet != null;
      BlazeContext context = BlazeContext.create();
      BuildInvoker invoker =
          Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project);
      BlazeCommand.Builder blazeCommand =
          getBlazeCommand(
              project,
              ExecutorType.fromExecutor(getEnvironment().getExecutor()),
              ImmutableList.of()
          );
      return isTest()
          ? getProcessHandlerForTests(project, invoker, blazeCommand, context)
          : getProcessHandlerForNonTests(project, invoker, blazeCommand, context);
    }

    private ProcessHandler getGenericProcessHandler() {
      return new ProcessHandler() {
        @Override
        protected void destroyProcessImpl() {
          notifyProcessTerminated(BLAZE_BUILD_INTERRUPTED);
        }

        @Override
        protected void detachProcessImpl() {
          ApplicationManager.getApplication().executeOnPooledThread(this::notifyProcessDetached);
        }

        @Override
        public boolean detachIsDefault() {
          return false;
        }

        @Nullable
        @Override
        public OutputStream getProcessInput() {
          return null;
        }
      };
    }

    private ProcessHandler getProcessHandlerForNonTests(
        Project project,
        BuildInvoker invoker,
        BlazeCommand.Builder blazeCommandBuilder,
        BlazeContext context)
        throws ExecutionException {
      if (invoker.getCapabilities().contains(BuildInvoker.Capability.RETURN_PROCESS_HANDLER)) {
        try {
          return invoker.invokeAsProcessHandler(blazeCommandBuilder, context, bepStreamProvider -> Unit.INSTANCE);
        }
        catch (BuildException e) {
          throw new ExecutionException(e);
        }
      }
      ProcessHandler processHandler = getGenericProcessHandler();
      ConsoleView consoleView = getConsoleBuilder().getConsole();
      context.addOutputSink(PrintOutput.class, new WritingOutputSink(consoleView));
      setConsoleBuilder(
          new TextConsoleBuilderImpl(project) {
            @Override
            protected ConsoleView createConsole() {
              return consoleView;
            }
          });
      addConsoleFilters(consoleFilters.toArray(new Filter[0]));

      ListenableFuture<BlazeBuildOutputs> blazeBuildOutputsListenableFuture =
          BlazeExecutor.getInstance()
              .submit(
                  () -> {
                    return invoker.invoke(
                        blazeCommandBuilder,
                        context,
                        streamProvider -> {
                          BlazeBuildOutputs outputs =
                              BlazeBuildOutputs.fromParsedBepOutput(
                                  BuildResultParser.getBuildOutput(streamProvider, Interners.STRING));
                          return outputs;
                        });
                  });
      Futures.addCallback(
          blazeBuildOutputsListenableFuture,
          new FutureCallback<BlazeBuildOutputs>() {
            @Override
            public void onSuccess(BlazeBuildOutputs blazeBuildOutputs) {
              processHandler.detachProcess();
            }

            @Override
            public void onFailure(Throwable throwable) {
              context.handleException(throwable.getMessage(), throwable);
              processHandler.detachProcess();
            }
          },
          BlazeExecutor.getInstance().getExecutor());

      processHandler.addProcessListener(
          new ProcessAdapter() {
            @Override
            public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
              if (willBeDestroyed) {
                context.setCancelled();
              }
            }
          });
      return processHandler;
    }

    private ProcessHandler getProcessHandlerForTests(
        Project project,
        BuildInvoker invoker,
        BlazeCommand.Builder blazeCommandBuilder,
        BlazeContext context) {
      final var testResultFinderStrategy = new BlazeTestResultFetcher();
      BlazeTestUiSession testUiSession = null;
      if (BlazeTestEventsHandler.targetsSupported(project, configuration.getTargetPatterns())) {
        testUiSession =
            BlazeTestUiSession.create(
                ImmutableList.<String>builder()
                    .add("--runs_per_test=1")
                    .add("--flaky_test_attempts=1")
                    .build(),
                testResultFinderStrategy);
      }
      if (testUiSession != null) {
        ConsoleView consoleView =
            SmRunnerUtils.getConsoleView(
              project, configuration, getEnvironment().getExecutor(), testUiSession.getTestResultFinderStrategy());
        setConsoleBuilder(
            new TextConsoleBuilderImpl(project) {
              @Override
              protected ConsoleView createConsole() {
                return consoleView;
              }
            });
        context.addOutputSink(PrintOutput.class, new WritingOutputSink(consoleView));
      }
      addConsoleFilters(consoleFilters.toArray(new Filter[0]));
      return getCommandRunnerProcessHandlerForTests(
          invoker, blazeCommandBuilder, testResultFinderStrategy, context);
    }

    private ProcessHandler getCommandRunnerProcessHandlerForTests(
        BuildInvoker invoker,
        BlazeCommand.Builder blazeCommandBuilder,
        BlazeTestResultFetcher testResultFinderStrategy,
        BlazeContext context) {
      ProcessHandler processHandler = getGenericProcessHandler();
      final var testResults = BlazeExecutor.getInstance()
              .submit(
                  () -> invoker.invoke(
                      blazeCommandBuilder,
                      context,
                      bepStreamProvider -> {
                        testResultFinderStrategy.setTestResults(bepStreamProvider);
                        return null;
                      }));
      Futures.addCallback(
          testResults,
          new FutureCallback<>() {
            @Override
            public void onSuccess(Object result) {
              processHandler.detachProcess();
            }

            @Override
            public void onFailure(Throwable throwable) {
              context.handleException(throwable.getMessage(), throwable);
              processHandler.detachProcess();
            }
          },
          BlazeExecutor.getInstance().getExecutor());

      processHandler.addProcessListener(
          new ProcessAdapter() {
            @Override
            public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
              if (willBeDestroyed) {
                context.setCancelled();
              }
            }
          });
      return processHandler;
    }

    private BlazeCommand.Builder getBlazeCommand(
        Project project,
        ExecutorType executorType,
        ImmutableList<String> testHandlerFlags) {
      ProjectViewSet projectViewSet =
          Preconditions.checkNotNull(ProjectViewManager.getInstance(project).getProjectViewSet());

      List<String> extraBlazeFlags = new ArrayList<>(testHandlerFlags);
      BlazeCommandName command = getCommand();
      if (executorType == ExecutorType.COVERAGE) {
        command = BlazeCommandName.COVERAGE;
      }

      return BlazeCommand.builder(command)
          .addTargetStrings(configuration.getTargetPatterns())
          .addBlazeFlags(
              BlazeFlags.blazeFlags(
                  project,
                  projectViewSet,
                  getCommand(),
                  BlazeInvocationContext.runConfigContext(
                      executorType, configuration.getType(), false)))
          .addBlazeFlags(extraBlazeFlags)
          .addBlazeFlags(handlerState.getBlazeFlagsState().getFlagsForExternalProcesses())
          .addExeFlags(handlerState.getExeFlagsState().getFlagsForExternalProcesses());
    }

    private BlazeCommandName getCommand() {
      return handlerState.getCommandState().getCommand();
    }

    private boolean isTest() {
      return BlazeCommandName.TEST.equals(getCommand());
    }
  }

  private static class WritingOutputSink implements OutputSink<PrintOutput> {
    private final ConsoleView console;

    public WritingOutputSink(ConsoleView console) {
      this.console = console;
    }

    @Override
    public Propagation onOutput(PrintOutput output) {
      // Add ANSI support to the console to view colored output
      console.print(
          output.getText().replaceAll("\u001B\\[[;\\d]*m", "") + "\n",
          output.getOutputType() == OutputType.ERROR
              ? ConsoleViewContentType.ERROR_OUTPUT
              : ConsoleViewContentType.NORMAL_OUTPUT);
      return Propagation.Continue;
    }
  }
}
