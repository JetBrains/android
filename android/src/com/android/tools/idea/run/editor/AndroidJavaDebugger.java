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

import com.android.ddmlib.Client;
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.google.common.collect.ImmutableSet;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.breakpoints.JavaFieldBreakpointType;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.debugger.ui.breakpoints.JavaMethodBreakpointType;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.NotNullFunction;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class AndroidJavaDebugger extends AndroidDebuggerImplBase<AndroidDebuggerState> {
  public static final String ID = "Java";
  private static final String RUN_CONFIGURATION_NAME_PATTERN = "Android Debugger (%s)";

  // This set of breakpoints is by no means is fully complete because
  // it stands for validation purposes which improves user's experience.
  public static final Set<Class<? extends XBreakpointType<?, ?>>> JAVA_BREAKPOINT_TYPES =
    ImmutableSet.<Class<? extends XBreakpointType<?, ?>>>of(
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
    return getId();
  }

  @NotNull
  @Override
  public AndroidDebuggerState createState() {
    return new AndroidDebuggerState();
  }

  @NotNull
  @Override
  public AndroidDebuggerConfigurable<AndroidDebuggerState> createConfigurable(@NotNull Project project) {
    return new AndroidDebuggerConfigurable<AndroidDebuggerState>();
  }

  @NotNull
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull ExecutionEnvironment env,
                                                   @NotNull Set<String> applicationIds,
                                                   @NotNull AndroidFacet facet,
                                                   @NotNull AndroidDebuggerState state,
                                                   @NotNull String runConfigTypeId) {
    return new ConnectJavaDebuggerTask(applicationIds, this, env.getProject());
  }

  @Override
  public boolean supportsProject(@NotNull Project project) {
    return true;
  }

  @Override
  public void attachToClient(@NotNull Project project, @NotNull Client client) {
    String debugPort = getClientDebugPort(client);
    String runConfigName = String.format(RUN_CONFIGURATION_NAME_PATTERN, debugPort);

    // Try to find existing debug session
    if (hasExistingDebugSession(project, debugPort, runConfigName)) {
      return;
    }

    // Create run configuration
    RemoteConfigurationType remoteConfigurationType = RemoteConfigurationType.getInstance();
    ConfigurationFactory factory = remoteConfigurationType.getFactory();
    RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project).createRunConfiguration(runConfigName, factory);

    RemoteConfiguration configuration = (RemoteConfiguration)runSettings.getConfiguration();
    configuration.HOST = "localhost";
    configuration.PORT = debugPort;
    configuration.USE_SOCKET_TRANSPORT = true;
    configuration.SERVER_MODE = false;

    ProgramRunnerUtil.executeConfiguration(project, runSettings, DefaultDebugExecutor.getDebugExecutorInstance());
  }

  private static boolean hasExistingDebugSession(@NotNull Project project, @NotNull final String debugPort, @NotNull final String runConfigName) {
    Collection<RunContentDescriptor> descriptors = null;
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    Project targetProject = null;

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
          } else {
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
