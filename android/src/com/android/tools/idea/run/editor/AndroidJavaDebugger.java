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

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;

import com.android.annotations.concurrency.Slow;
import com.android.builder.model.TestOptions;
import com.android.ddmlib.Client;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.android.tools.idea.run.tasks.ReattachingDebugConnectorTask;
import com.google.common.collect.ImmutableSet;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.breakpoints.JavaFieldBreakpointType;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.debugger.ui.breakpoints.JavaMethodBreakpointType;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Ref;
import com.intellij.util.NotNullFunction;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidJavaDebugger extends AndroidDebuggerImplBase<AndroidDebuggerState> {
  public static final String ID = "Java";
  private static final String RUN_CONFIGURATION_NAME_PATTERN = "Android Debugger (%s)";

  // This set of breakpoints is by no means is fully complete because
  // it stands for validation purposes which improves user's experience.
  public static final Set<Class<? extends XBreakpointType<?, ?>>> JAVA_BREAKPOINT_TYPES =
    ImmutableSet.of(
      JavaLineBreakpointType.class,
      JavaMethodBreakpointType.class,
      JavaFieldBreakpointType.class
    );

  public AndroidJavaDebugger() {
    super(JAVA_BREAKPOINT_TYPES);
  }

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
                                                   @NotNull String runConfigTypeId) {
    TestOptions.Execution executionType = Optional.ofNullable(AndroidModuleModel.get(facet))
      .map(AndroidModuleModel::getTestExecutionStrategy)
      .orElse(TestOptions.Execution.HOST);
    switch(executionType) {
      case ANDROID_TEST_ORCHESTRATOR:
      case ANDROIDX_TEST_ORCHESTRATOR:
        return new ReattachingDebugConnectorTask(applicationIds, this, env.getProject());
      default:
        return new ConnectJavaDebuggerTask(applicationIds, this, env.getProject(),
                                           facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP);
    }
  }

  @Override
  public boolean supportsProject(@NotNull Project project) {
    return true;
  }

  @Slow
  @Override
  public void attachToClient(@NotNull Project project, @NotNull Client client) {
    String debugPort = getClientDebugPort(client);
    String runConfigName = getRunConfigurationName(debugPort);

    // Try to find existing debug session
    Ref<Boolean> existingSession = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> existingSession.set(hasExistingDebugSession(project, debugPort, runConfigName)));
    if (existingSession.get()) {
      return;
    }

    // Create run configuration
    RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project).createConfiguration(runConfigName, RemoteConfigurationType.class);

    RemoteConfiguration configuration = (RemoteConfiguration)runSettings.getConfiguration();
    configuration.HOST = "localhost";
    configuration.PORT = debugPort;
    configuration.USE_SOCKET_TRANSPORT = true;
    configuration.SERVER_MODE = false;

    ProgramRunnerUtil.executeConfiguration(runSettings, DefaultDebugExecutor.getDebugExecutorInstance());
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
}
