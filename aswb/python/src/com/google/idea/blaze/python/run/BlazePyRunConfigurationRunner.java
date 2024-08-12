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
package com.google.idea.blaze.python.run;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.ideinfo.PyIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeBeforeRunCommandHelper;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.WithBrowserHyperlinkExecutionException;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationRunner.BlazeCommandRunProfileState;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.ProcessGroupUtil;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.python.PySdkUtils;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.jetbrains.python.console.PyDebugConsoleBuilder;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.run.PythonScriptCommandLineState;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Python-specific run configuration runner. */
public class BlazePyRunConfigurationRunner implements BlazeCommandRunConfigurationRunner {

  /** This inserts flags provided by any BlazePyDebugHelpers to the pydevd.py invocation */

  /** Used to store a runner to an {@link ExecutionEnvironment}. */
  private static final Key<AtomicReference<PyExecutionInfo>> EXECUTABLE_KEY =
      Key.create("blaze.debug.py.executable");

  /** Converts to the native python plugin debug configuration state */
  static class BlazePyDummyRunProfileState implements RunProfileState {
    final BlazeCommandRunConfiguration configuration;

    BlazePyDummyRunProfileState(BlazeCommandRunConfiguration configuration) {
      this.configuration = configuration;
    }

    PythonScriptCommandLineState toNativeState(ExecutionEnvironment env) throws ExecutionException {
      PyExecutionInfo executionInfo = env.getCopyableUserData(EXECUTABLE_KEY).get();
      if (executionInfo.executable == null
          || StringUtil.isEmptyOrSpaces(executionInfo.executable.getPath())) {
        throw new ExecutionException("No blaze output script found");
      }
      PythonRunConfiguration nativeConfig =
          (PythonRunConfiguration)
              PythonConfigurationType.getInstance()
                  .getFactory()
                  .createTemplateConfiguration(env.getProject());
      nativeConfig.setScriptName(executionInfo.executable.getPath());
      nativeConfig.setAddContentRoots(false);
      nativeConfig.setAddSourceRoots(false);
      nativeConfig.setWorkingDirectory(
          Strings.nullToEmpty(
              getRunfilesPath(
                  executionInfo.executable, WorkspaceRoot.fromProjectSafe(env.getProject()))));
      // BUILD file defined args
      List<String> args = new ArrayList<>(executionInfo.args);

      Sdk sdk = PySdkUtils.getPythonSdk(env.getProject());
      if (sdk == null) {
        throw new ExecutionException("Can't find a Python SDK when debugging a python target.");
      }
      nativeConfig.setModule(null);
      nativeConfig.setSdkHome(sdk.getHomePath());

      BlazePyRunConfigState handlerState =
          configuration.getHandlerStateIfType(BlazePyRunConfigState.class);
      if (handlerState != null) {
        // Run configuration defined args
        args.addAll(getScriptParams(handlerState));

        EnvironmentVariablesData envState = handlerState.getEnvVarsState().getData();
        nativeConfig.setPassParentEnvs(envState.isPassParentEnvs());
        nativeConfig.setEnvs(envState.getEnvs());
      }
      nativeConfig.setScriptParameters(Strings.emptyToNull(ParametersListUtil.join(args)));
      Label target = getSingleTarget(configuration);
      return new PythonScriptCommandLineState(nativeConfig, env) {

        private final CommandLinePatcher applyHelperPydevFlags =
            (commandLine) ->
                BlazePyDebugHelper.doBlazeDebugCommandlinePatching(
                    nativeConfig.getProject(), target, commandLine);

        @Override
        protected ProcessHandler startProcess(
            PythonProcessStarter starter, @Nullable CommandLinePatcher... patchers)
            throws ExecutionException {
          // Need to run after the other CommandLinePatchers
          List<CommandLinePatcher> modifiedPatchers = new ArrayList<>();
          if (patchers != null) {
            Collections.addAll(modifiedPatchers, patchers);
          }
          modifiedPatchers.add(applyHelperPydevFlags);
          ProcessHandler process =
              super.startProcess(starter, modifiedPatchers.toArray(new CommandLinePatcher[0]));
          BlazePyDebugHelper.attachProcessListeners(target, process);
          return process;
        }

        @Override
        public boolean isDebug() {
          return true;
        }

        @Override
        protected ConsoleView createAndAttachConsole(
            Project project, ProcessHandler processHandler, Executor executor)
            throws ExecutionException {
          ConsoleView consoleView = createConsoleBuilder(project, getSdk()).getConsole();
          consoleView.addMessageFilter(createUrlFilter(processHandler));

          consoleView.attachToProcess(processHandler);
          return consoleView;
        }

        @Override
        protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine)
            throws ExecutionException {
          return super.doCreateProcess(ProcessGroupUtil.newProcessGroupFor(commandLine));
        }
      };
    }

    @Nullable
    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner<?> runner)
        throws ExecutionException {
      return null;
    }

    private static TextConsoleBuilder createConsoleBuilder(Project project, Sdk sdk) {
      return new PyDebugConsoleBuilder(project, sdk) {
        @Override
        protected ConsoleView createConsole() {
          PythonDebugLanguageConsoleView consoleView =
              new PythonDebugLanguageConsoleView(project, sdk);
          for (Filter filter : getFilters()) {
            consoleView.addMessageFilter(filter);
          }
          return consoleView;
        }
      };
    }

    private static ImmutableList<String> getScriptParams(
        BlazeCommandRunConfigurationCommonState state) {
      ImmutableList.Builder<String> paramsBuilder = ImmutableList.builder();
      paramsBuilder.addAll(state.getExeFlagsState().getFlagsForExternalProcesses());
      paramsBuilder.addAll(state.getTestArgs());
      String filterFlag = state.getTestFilterFlag();
      if (filterFlag != null) {
        String testFilterArg = filterFlag.substring((BlazeFlags.TEST_FILTER + "=").length());
        // testFilterArg is a space-delimited list of filters
        paramsBuilder.addAll(Splitter.on(" ").splitToList(testFilterArg));
      }
      return paramsBuilder.build();
    }
  }

  private static ImmutableList<Filter> getFilters() {
    return ImmutableList.<Filter>builder()
        .add(new UrlFilter())
        .build();
  }

  @Override
  public RunProfileState getRunProfileState(Executor executor, ExecutionEnvironment env) {
    if (!BlazeCommandRunConfigurationRunner.isDebugging(env)
        || BlazeCommandName.BUILD.equals(BlazeCommandRunConfigurationRunner.getBlazeCommand(env))) {
      return new BlazeCommandRunProfileState(env);
    }
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getConfiguration(env);
    env.putCopyableUserData(EXECUTABLE_KEY, new AtomicReference<>());
    return new BlazePyDummyRunProfileState(configuration);
  }

  @Override
  public boolean executeBeforeRunTask(ExecutionEnvironment env) {
    if (!BlazeCommandRunConfigurationRunner.isDebugging(env)
        || BlazeCommandName.BUILD.equals(BlazeCommandRunConfigurationRunner.getBlazeCommand(env))) {
      return true;
    }
    env.getCopyableUserData(EXECUTABLE_KEY).set(null);
    try {
      PyExecutionInfo executionInfo = getExecutableToDebug(env);
      env.getCopyableUserData(EXECUTABLE_KEY).set(executionInfo);
      if (executionInfo.executable != null) {
        return true;
      }
    } catch (ExecutionException e) {
      ExecutionUtil.handleExecutionError(
          env.getProject(), env.getExecutor().getToolWindowId(), env.getRunProfile(), e);
    }
    return false;
  }

  /** Make a best-effort attempt to get the runfiles path. Returns null if it can't be found. */
  @Nullable
  private static String getRunfilesPath(File executable, @Nullable WorkspaceRoot root) {
    if (root == null) {
      return null;
    }
    String workspaceName = root.directory().getName();
    File expectedPath = new File(executable.getPath() + ".runfiles", workspaceName);
    if (FileOperationProvider.getInstance().exists(expectedPath)) {
      return expectedPath.getPath();
    }
    return null;
  }

  private static Label getSingleTarget(BlazeCommandRunConfiguration config)
      throws ExecutionException {
    ImmutableList<? extends TargetExpression> targets = config.getTargets();
    if (targets.size() != 1 || !(targets.get(0) instanceof Label)) {
      throw new ExecutionException("Invalid configuration: doesn't have a single target label");
    }
    return (Label) targets.get(0);
  }

  /**
   * Builds blaze python target and returns the output build artifact.
   *
   * @throws ExecutionException if the target cannot be debugged.
   */
  private static PyExecutionInfo getExecutableToDebug(ExecutionEnvironment env)
      throws ExecutionException {
    BlazeCommandRunConfiguration configuration =
        BlazeCommandRunConfigurationRunner.getConfiguration(env);
    Project project = configuration.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      throw new ExecutionException("Not synced yet, please sync project");
    }

    Label target = getSingleTarget(configuration);
    ImmutableList<String> args = getPythonArgsFor(blazeProjectData, target);
    String validationError = BlazePyDebugHelper.validateDebugTarget(env.getProject(), target);
    if (validationError != null) {
      throw new WithBrowserHyperlinkExecutionException(validationError);
    }

    SaveUtil.saveAllFiles();
    // Explicitly depend on local build helper because the debuggable binary is expected to be
    // present locally
    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.createForLocalBuild(project)) {

      ListenableFuture<BuildResult> buildOperation =
          BlazeBeforeRunCommandHelper.runBlazeCommand(
              BlazeCommandName.BUILD,
              configuration,
              buildResultHelper,
              BlazePyDebugHelper.getAllBlazeDebugFlags(configuration.getProject(), target),
              ImmutableList.of(),
              BlazeInvocationContext.runConfigContext(
                  ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), true),
              "Building debug binary");

      try {
        BuildResult result = buildOperation.get();
        if (result.status != BuildResult.Status.SUCCESS) {
          throw new ExecutionException("Blaze failure building debug binary");
        }
      } catch (InterruptedException | CancellationException e) {
        buildOperation.cancel(true);
        throw new RunCanceledByUserException();
      } catch (java.util.concurrent.ExecutionException e) {
        throw new ExecutionException(e);
      }
      List<File> candidateFiles;
      try {
        candidateFiles =
            LocalFileArtifact.getLocalFiles(
                    buildResultHelper.getBuildArtifactsForTarget(target, file -> true))
                .stream()
                .filter(File::canExecute)
                .collect(Collectors.toList());
      } catch (GetArtifactsException e) {
        throw new ExecutionException(
            String.format(
                "Failed to get output artifacts when building %s: %s", target, e.getMessage()));
      }
      if (candidateFiles.isEmpty()) {
        throw new ExecutionException(
            String.format("No output artifacts found when building %s", target));
      }
      File file = findExecutable(target, candidateFiles);
      if (file == null) {
        throw new ExecutionException(
            String.format(
                "More than 1 executable was produced when building %s; "
                    + "don't know which one to debug",
                target));
      }
      LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(file));
      return new PyExecutionInfo(file, args);
    }
  }

  private static ImmutableList<String> getPythonArgsFor(
      BlazeProjectData projectData, Label target) {
    TargetIdeInfo ideInfo = projectData.getTargetMap().get(TargetKey.forPlainTarget(target));
    if (ideInfo == null) {
      return ImmutableList.of();
    }
    PyIdeInfo pyIdeInfo = ideInfo.getPyIdeInfo();
    if (pyIdeInfo == null) {
      return ImmutableList.of();
    }
    return pyIdeInfo.getArgs();
  }

  /**
   * Basic heuristic for choosing between multiple output files. Currently just looks for a filename
   * matching the target name.
   */
  @VisibleForTesting
  @Nullable
  static File findExecutable(Label target, List<File> outputs) {
    if (outputs.size() == 1) {
      return outputs.get(0);
    }
    String name = PathUtil.getFileName(target.targetName().toString());
    for (File file : outputs) {
      if (file.getName().equals(name)) {
        return file;
      }
    }
    return null;
  }

  private static class PyExecutionInfo {
    public final File executable;
    public final ImmutableList<String> args;

    PyExecutionInfo(File executable, ImmutableList<String> args) {
      this.executable = executable;
      this.args = args;
    }
  }
}
