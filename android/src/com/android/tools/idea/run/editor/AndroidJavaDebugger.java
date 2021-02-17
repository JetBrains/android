/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.editor;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;

import com.android.annotations.concurrency.Slow;
import com.android.builder.model.TestOptions;
import com.android.ddmlib.Client;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.testartifacts.instrumented.orchestrator.OrchestratorUtilsKt;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Ref;
import com.intellij.util.NotNullFunction;
import com.intellij.xdebugger.XDebugSession;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidJavaDebugger extends AndroidDebuggerImplBase<AndroidDebuggerState> {
  public static final String ID = "Java";
  private static final String RUN_CONFIGURATION_NAME_PATTERN = "Android Debugger (%s)";

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Java Only";
  }

  @NotNull
  @Override
  public AndroidDebuggerState createState() {
    return new AndroidDebuggerState();
  }

  @NotNull
  @Override
  public AndroidDebuggerConfigurable<AndroidDebuggerState> createConfigurable(@NotNull RunConfiguration runConfiguration) {
    return new AndroidDebuggerConfigurable<>();
  }

  @NotNull
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull ExecutionEnvironment env,
                                                   @Nullable AndroidVersion version,
                                                   @NotNull Set<String> applicationIds,
                                                   @NotNull AndroidFacet facet,
                                                   @NotNull AndroidDebuggerState state,
                                                   @NotNull String runConfigTypeId,
                                                   @Nullable String packageNameOverride) {
    // TODO(b/153668177): Note/Review: packageNameOverride is used in native debugger only.
    ConnectJavaDebuggerTask baseConnector = new ConnectJavaDebuggerTask(
      applicationIds, this, env.getProject(),
      facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP);
    TestOptions.Execution executionType = Optional.ofNullable(AndroidModuleModel.get(facet))
      .map(AndroidModuleModel::getTestExecutionStrategy)
      .orElse(TestOptions.Execution.HOST);
    switch (executionType) {
      case ANDROID_TEST_ORCHESTRATOR:
      case ANDROIDX_TEST_ORCHESTRATOR:
        return OrchestratorUtilsKt.createReattachingDebugConnectorTask(baseConnector, executionType);
      default:
        return baseConnector;
    }
  }

  @Override
  public boolean supportsProject(@NotNull Project project) {
    return true;
  }

  @Slow
  @Override
  public void attachToClient(@NotNull Project project, @NotNull Client client, @Nullable RunConfiguration config) {
    String debugPort = getClientDebugPort(client);
    String runConfigName = getRunConfigurationName(debugPort);

    // Try to find existing debug session
    Ref<Boolean> existingSession = new Ref<>();
    ApplicationManager.getApplication()
      .invokeAndWait(() -> existingSession.set(hasExistingDebugSession(project, debugPort, runConfigName)));
    if (existingSession.get()) {
      return;
    }

    // Create run configuration
    RunnerAndConfigurationSettings runSettings =
      RunManager.getInstance(project).createConfiguration(runConfigName, RemoteConfigurationType.class);

    RemoteConfiguration configuration = (RemoteConfiguration)runSettings.getConfiguration();
    configuration.HOST = "localhost";
    configuration.PORT = debugPort;
    configuration.USE_SOCKET_TRANSPORT = true;
    configuration.SERVER_MODE = false;

    ProgramRunner.Callback callback = new ProgramRunner.Callback() {
      @Override
      public void processStarted(RunContentDescriptor descriptor) {
        // Callback to add a termination listener after the process handler gets created.
        ProcessHandler handler = descriptor.getProcessHandler();
        if (handler == null) {
          return;
        }
        VMExitedNotifier notifier = new VMExitedNotifier(client);
        ProcessAdapter processAdapter = new ProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            handler.removeProcessListener(this);
            notifier.notifyClient();
          }
        };
        // Add the handler first, then check, as to avoid race condition where process terminates between checking then adding.
        handler.addProcessListener(processAdapter, project);
        if (handler.isProcessTerminated()) {
          handler.removeProcessListener(processAdapter);
          notifier.notifyClient();
        }
      }
    };

    ExecutionEnvironment executionEnvironment;
    try {
      // Code lifted out of ProgramRunnerUtil. We do this because we need to access the callback field.
      executionEnvironment =
        ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance(), runSettings)
          .contentToReuse(null)
          .dataContext(null)
          .build();
    }
    catch (ExecutionException e) {
      Logger.getInstance(AndroidJavaDebugger.class).error(e);
      return;
    }

    // Need to execute on the EDT since the associated tool window may be created internally by IJ
    // (we may be not be on the EDT at this point in the code).
    ApplicationManager.getApplication().invokeLater(
      () -> ProgramRunnerUtil.executeConfigurationAsync(executionEnvironment, /*showSettings=*/true, /*assignNewId=*/true, callback));
  }

  public DebuggerSession getDebuggerSession(@NotNull Client client) {
    String debugPort = getClientDebugPort(client);

    for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
      DebuggerSession debuggerSession = findJdwpDebuggerSession(openProject, debugPort);
      if (debuggerSession != null) {
        return debuggerSession;
      }
    }
    return null;
  }

  @NotNull
  public static String getRunConfigurationName(@NotNull String debugPort) {
    return String.format(RUN_CONFIGURATION_NAME_PATTERN, debugPort);
  }

  public static boolean hasExistingDebugSession(@NotNull Project project,
                                                @NotNull final String debugPort,
                                                @NotNull final String runConfigName) {
    Collection<RunContentDescriptor> descriptors = null;
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    Project targetProject;

    // Scan through open project to find if this port has been opened in any session.
    for (Project openProject : openProjects) {
      targetProject = openProject;

      // First check the titles of the run configurations.
      descriptors = ExecutionHelper.findRunningConsoleByTitle(targetProject, new NotNullFunction<String, Boolean>() {
        @NotNull
        @Override
        public Boolean fun(String title) {
          return runConfigName.equals(title);
        }
      });

      // If it can't find a matching title, check the debugger sessions.
      if (descriptors.isEmpty()) {
        DebuggerSession debuggerSession = findJdwpDebuggerSession(targetProject, debugPort);
        if (debuggerSession != null) {
          XDebugSession session = debuggerSession.getXDebugSession();
          if (session != null) {
            descriptors = Collections.singletonList(session.getRunContentDescriptor());
          }
          else {
            // Detach existing session.
            debuggerSession.getProcess().stop(false);
          }
        }
      }

      if (!descriptors.isEmpty()) {
        break;
      }
    }

    if (descriptors != null && !descriptors.isEmpty()) {
      return activateDebugSessionWindow(project, descriptors.iterator().next());
    }
    return false;
  }

  private static class VMExitedNotifier {
    @NotNull private final Client myClient;
    @NotNull private final AtomicBoolean myNeedsToNotify = new AtomicBoolean(true);

    private VMExitedNotifier(@NotNull Client client) {
      myClient = client;
    }

    private void notifyClient() {
      // The atomic boolean guarantees that we only ever notify the Client once.
      if (myNeedsToNotify.getAndSet(false)) {
        myClient.notifyVmMirrorExited();
      }
    }
  }
}
