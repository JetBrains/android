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
package com.google.idea.blaze.java.run;

import static com.google.idea.blaze.base.bazel.LocalInvokerHelper.getScopedProcessHandler;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker.Capability;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunnerExperiments;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.issueparser.ToolWindowTaskIssueOutputFilter;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.BlazeTestUiSession;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFinderStrategy;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultHolder;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.java.TargetKindUtil;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A Blaze run configuration set up with an executor, program runner, and other settings, ready to
 * be executed. This class creates a command line for Blaze and exposes debug connection information
 * when using a debug executor.
 */
public final class BlazeJavaRunProfileState extends BlazeJavaDebuggableRunProfileState {
  private static final Logger logger = Logger.getInstance(BlazeJavaRunProfileState.class);
  private static final String JAVA_RUNFILES_ENV = "JAVA_RUNFILES=";
  private static final String TEST_DIAGNOSTICS_OUTPUT_DIR_ENV = "TEST_DIAGNOSTICS_OUTPUT_DIR=";
  private static final String TEST_SIZE_ENV = "TEST_SIZE=";
  private static final String TEST_TIMEOUT_ENV = "TEST_TIMEOUT=";
  private static final String TEST_DIAGNOSTICS_OUTPUT_DIR = "/tmp/test.test_diagnostics";
  @Nullable private String kotlinxCoroutinesJavaAgent;

  BlazeJavaRunProfileState(ExecutionEnvironment environment) {
    super(environment);
  }

  public void addKotlinxCoroutinesJavaAgent(String kotlinxCoroutinesJavaAgent) {
    this.kotlinxCoroutinesJavaAgent = kotlinxCoroutinesJavaAgent;
  }

  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    Project project = getConfiguration().getProject();
    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
    BlazeContext context = BlazeContext.create();
    boolean debuggingLocalTest =
      TargetKindUtil.isLocalTest(getConfiguration().getTargetKind())
      && getExecutorType().isDebugType();
    final BuildInvoker invoker;
    if (debuggingLocalTest) {
      Optional<BuildInvoker> invokerWithDebuggerCapability = buildSystem.getBuildInvoker(project,
                                                                                         ImmutableSet.of(Capability.ATTACH_JAVA_DEBUGGER,
                                                                                                         Capability.RETURN_PROCESS_HANDLER));
      if (invokerWithDebuggerCapability.isEmpty()) {
        return startProcessRunfilesCase(project);
      }
      else {
        invoker = invokerWithDebuggerCapability.get();
      }
    }
    else {
      invoker = buildSystem.getBuildInvoker(project, ImmutableSet.of(Capability.RETURN_PROCESS_HANDLER)).orElseThrow();
    }
    return startProcessBazelCliCase(invoker, project, context);
  }

  private ProcessHandler startProcessRunfilesCase(Project project) throws ExecutionException {
    File downloadDir = getDownloadDir();
    WorkspaceRoot workspaceRoot =
      new WorkspaceRoot(
        new File(downloadDir, WorkspaceRoot.fromProject(project).directory().getName()));
    ImmutableList.Builder<String> commandBuilder =
      ImmutableList.<String>builder()
        .add("env")
        .add("-")
        .add(JAVA_RUNFILES_ENV + downloadDir.getAbsolutePath());

    // android_local_tests need additional env variables
    if (TargetKindUtil.isAndroidLocalTest(getConfiguration().getTargetKind())) {
      commandBuilder
        .add(TEST_TIMEOUT_ENV + "300")
        .add(TEST_SIZE_ENV + "medium")
        .add(
          TEST_DIAGNOSTICS_OUTPUT_DIR_ENV
          + downloadDir.getAbsolutePath()
          + TEST_DIAGNOSTICS_OUTPUT_DIR);
    }
    commandBuilder
      .add(getEntryPointScript())
      .add(debugPortFlag(false, getState(getConfiguration()).getDebugPortState().port));
    if (TargetKindUtil.isAndroidLocalTest(getConfiguration().getTargetKind())
        && BlazeCommandRunnerExperiments.USE_SINGLEJAR_FOR_DEBUGGING.getValue()) {
      commandBuilder.add("--singlejar");
    }
    return getScopedProcessHandler(project, commandBuilder.build(), workspaceRoot);
  }

  private ProcessHandler startProcessBazelCliCase(
    BuildInvoker invoker, Project project, BlazeContext context) throws ExecutionException {
    PrepareBazelCommandResult result = prepareBazelCommand(project);
    addConsoleFilters(
      ToolWindowTaskIssueOutputFilter.createWithDefaultParsers(
        project,
        WorkspaceRoot.fromProject(project),
        BlazeInvocationContext.ContextType.RunConfiguration));

    try {
      return invoker.invokeAsProcessHandler(prepareBazelCommand(project).blazeCommand(), context);
    }
    catch (BuildException e) {
      throw new ExecutionException(e);
    }
  }

  private PrepareBazelCommandResult prepareBazelCommand(Project project) {
    BlazeCommand.Builder blazeCommand;
    BlazeTestUiSession testUiSession = null;
    BlazeTestResultFinderStrategy testResultFinderStrategy = new BlazeTestResultHolder();
    if (useTestUi()
        && BlazeTestEventsHandler.targetsSupported(project, getConfiguration().getTargets())) {
      testUiSession =
        BlazeTestUiSession.create(
          ImmutableList.<String>builder()
            .add("--runs_per_test=1")
            .add("--flaky_test_attempts=1")
            .build(),
          testResultFinderStrategy);
    }
    if (testUiSession != null) {
      blazeCommand =
        getBlazeCommandBuilder(
          project,
          getConfiguration(),
          testUiSession.getBlazeFlags(),
          getExecutorType(),
          kotlinxCoroutinesJavaAgent);
      ConsoleView consoleView = SmRunnerUtils.getConsoleView(project, getConfiguration(), getEnvironment().getExecutor(), testUiSession);
      setConsoleBuilder(
        new TextConsoleBuilderImpl(project) {
          @Override
          protected ConsoleView createConsole() {
            return consoleView;
          }
        });
    }
    else {
      blazeCommand =
        getBlazeCommandBuilder(
          project,
          getConfiguration(),
          ImmutableList.of(),
          getExecutorType(),
          kotlinxCoroutinesJavaAgent);
    }
    PrepareBazelCommandResult result = new PrepareBazelCommandResult(blazeCommand, testResultFinderStrategy);
    return result;
  }

  private record PrepareBazelCommandResult(BlazeCommand.Builder blazeCommand, BlazeTestResultFinderStrategy testResultFinderStrategy) {
  }

  @Override
  public ExecutionResult execute(Executor executor, ProgramRunner<?> runner)
    throws ExecutionException {
    if (BlazeCommandRunConfigurationRunner.isDebugging(getEnvironment())) {
      new MultiRunDebuggerSessionListener(getEnvironment(), this).startListening();
    }
    DefaultExecutionResult result = (DefaultExecutionResult)super.execute(executor, runner);
    return SmRunnerUtils.attachRerunFailedTestsAction(result);
  }

  private boolean useTestUi() {
    BlazeCommandRunConfigurationCommonState state =
      getConfiguration().getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return state != null && BlazeCommandName.TEST.equals(state.getCommandState().getCommand());
  }

  private static BlazeJavaRunConfigState getState(BlazeCommandRunConfiguration config) {
    return Preconditions.checkNotNull(config.getHandlerStateIfType(BlazeJavaRunConfigState.class));
  }

  @VisibleForTesting
  static BlazeCommand.Builder getBlazeCommandBuilder(
    Project project,
    BlazeCommandRunConfiguration configuration,
    List<String> extraBlazeFlags,
    ExecutorType executorType,
    @Nullable String kotlinxCoroutinesJavaAgent) {

    List<String> blazeFlags = new ArrayList<>(extraBlazeFlags);

    ProjectViewSet projectViewSet =
      Preconditions.checkNotNull(ProjectViewManager.getInstance(project).getProjectViewSet());
    BlazeJavaRunConfigState handlerState = getState(configuration);

    String binaryPath =
      handlerState.getBlazeBinaryState().getBlazeBinary() != null
      ? handlerState.getBlazeBinaryState().getBlazeBinary()
      : Blaze.getBuildSystemProvider(project).getBinaryPath(project);

    BlazeCommandName blazeCommand =
      Preconditions.checkNotNull(handlerState.getCommandState().getCommand());
    if (executorType == ExecutorType.COVERAGE) {
      blazeCommand = BlazeCommandName.COVERAGE;
    }
    BlazeCommand.Builder command =
      BlazeCommand.builder(binaryPath, blazeCommand)
        .addTargets(configuration.getTargets())
        .addBlazeFlags(
          BlazeFlags.blazeFlags(
            project,
            projectViewSet,
            blazeCommand,
            BlazeContext.create(),
            BlazeInvocationContext.runConfigContext(
              executorType, configuration.getType(), false)))
        .addBlazeFlags(blazeFlags)
        .addBlazeFlags(handlerState.getBlazeFlagsState().getFlagsForExternalProcesses());

    if (executorType == ExecutorType.DEBUG) {
      Kind kind = configuration.getTargetKind();
      boolean isBinary = kind != null && kind.getRuleType() == RuleType.BINARY;
      int debugPort = handlerState.getDebugPortState().port;
      if (isBinary) {
        command.addExeFlags(debugPortFlag(false, debugPort));
      }
      else {
        command.addBlazeFlags(BlazeFlags.JAVA_TEST_DEBUG);
        command.addBlazeFlags(debugPortFlag(true, debugPort));
      }
      if (kotlinxCoroutinesJavaAgent != null) {
        command.addBlazeFlags("--jvmopt=-javaagent:" + kotlinxCoroutinesJavaAgent);
      }
    }

    command.addExeFlags(handlerState.getExeFlagsState().getFlagsForExternalProcesses());
    return command;
  }

  private File getDownloadDir() {
    String testTargetString = getConfiguration().getSingleTarget().toString();
    return new File(
      FileUtilRt.getTempDirectory(),
      testTargetString.substring(testTargetString.lastIndexOf(":") + 1) + ".runfiles");
  }

  private static String debugPortFlag(boolean isTest, int port) {
    String flag = "--wrapper_script_flag=--debug=127.0.0.1:" + port;
    return isTest ? testArg(flag) : flag;
  }

  private String getEntryPointScript() {
    return CharMatcher.is('/')
      .trimLeadingFrom(getConfiguration().getSingleTarget().toString())
      .replace(':', '/');
  }

  private static String testArg(String flag) {
    return "--test_arg=" + flag;
  }

  //TODO(akhildixit) - the following output sink is the same as the one in BlazeCommandGenericRunConfigurationRunner.java.
  // Extract it into a separate class to avoid code duplication.
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
        output.getOutputType() == PrintOutput.OutputType.ERROR
        ? ConsoleViewContentType.ERROR_OUTPUT
        : ConsoleViewContentType.NORMAL_OUTPUT);
      return Propagation.Continue;
    }
  }
}