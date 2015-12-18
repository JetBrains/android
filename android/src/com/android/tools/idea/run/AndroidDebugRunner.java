/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

public class AndroidDebugRunner extends DefaultProgramRunner {
  public static final Key<AndroidSessionInfo> ANDROID_SESSION_INFO = new Key<AndroidSessionInfo>("ANDROID_SESSION_INFO");

  private static final Object myDebugLock = new Object();
  public static final String ANDROID_LOGCAT_CONTENT_ID = "Android Logcat";
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidDebugRunner");

  @Override
  protected RunContentDescriptor doExecute(@NotNull final RunProfileState state, @NotNull final ExecutionEnvironment environment)
    throws ExecutionException {
    if (!(state instanceof AndroidRunningState)) {
      return doExecSimple(state, environment);
    }
    final AndroidRunningState runningState = (AndroidRunningState) state;
    final RunContentDescriptor[] descriptor = {null};

    runningState.addListener(new AndroidRunningStateListener() {
      @Override
      public void executionFailed() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (descriptor[0] != null) {
              LaunchUtils
                .showNotification(environment.getProject(), environment.getExecutor(), descriptor[0], "Error", NotificationType.ERROR);
            }
          }
        });
      }

      @Override
      public void executionStarted(@NotNull IDevice device) {
        Executor executor = environment.getExecutor();
        // no need to show for debug executor: debug launches open the tool window, and even if not, the debug runner will show
        // the notification when it attaches to the debugger, we don't want the notification to show up when the activity is launched
        if (executor instanceof DefaultRunExecutor) {
          LaunchUtils.showNotification(environment.getProject(), executor, descriptor[0], "Launched on " + device.getName(),
                                       NotificationType.INFORMATION);
        }
      }
    });
    descriptor[0] = doExec(runningState, environment);
    return descriptor[0];
  }

  /**
   * Executes states that do not require Android debugging. May set Android tool window to open on launch.
   */
  private RunContentDescriptor doExecSimple(@NotNull final RunProfileState state, @NotNull final ExecutionEnvironment environment)
    throws ExecutionException {
    final RunContentDescriptor descriptor = super.doExecute(state, environment);
    if (descriptor != null) {
      // we want the run tool window to show up only for test runs
      boolean showRunContent = environment.getRunProfile() instanceof AndroidTestRunConfiguration;
      descriptor.setActivateToolWindowWhenAdded(showRunContent);
    }
    return descriptor;
  }

  private RunContentDescriptor doExec(AndroidRunningState state, ExecutionEnvironment environment) throws ExecutionException {
    if (!(environment.getExecutor() instanceof DefaultDebugExecutor)) {
      return doExecSimple(state, environment);
    }

    RunContentDescriptor descriptor;
    synchronized (myDebugLock) {
      MyDebugLauncher launcher = new MyDebugLauncher(state, environment);
      state.setDebugLauncher(launcher);

      descriptor = embedToExistingSession(environment.getProject(), environment.getExecutor(), state);
      if (descriptor != null) {
        launcher.setRunDescriptor(descriptor);
        return null;
      }

      descriptor = super.doExecute(state, environment);
      if (descriptor == null) {
        return null;
      }
      launcher.setRunDescriptor(descriptor);
    }

    final ProcessHandler handler = state.getProcessHandler();
    handler.putUserData(ANDROID_SESSION_INFO, new AndroidSessionInfo(handler, descriptor, state, environment.getExecutor().getId()));
    descriptor.setActivateToolWindowWhenAdded(false);
    return descriptor;
  }

  @Nullable
  protected static RunContentDescriptor embedToExistingSession(@NotNull final Project project,
                                                               @NotNull final Executor executor,
                                                               @NotNull final AndroidRunningState state) {
    final AndroidSessionInfo oldSessionInfo = AndroidSessionManager.findOldSession(project, executor, state.getConfiguration());

    if (oldSessionInfo == null || !oldSessionInfo.isEmbeddable()) {
      return null;
    }
    final AndroidExecutionState oldState = oldSessionInfo.getState();
    final ConsoleView oldConsole = oldState.getConsoleView();

    if (oldState.getDevices() == null || !oldState.getDevices().equals(state.getDevices())) {
      return null;
    }

    oldSessionInfo.getProcessHandler().detachProcess();
    state.setConsole(oldConsole);
    final RunContentDescriptor oldDescriptor = oldSessionInfo.getDescriptor();
    ProcessHandler newProcessHandler;
    if (oldDescriptor.getProcessHandler() instanceof RemoteDebugProcessHandler) {
      newProcessHandler = oldDescriptor.getProcessHandler();
      newProcessHandler.destroyProcess();
    } else {
      newProcessHandler = new DefaultDebugProcessHandler();
    }
    oldDescriptor.setProcessHandler(newProcessHandler);
    state.setProcessHandler(newProcessHandler);
    oldConsole.attachToProcess(newProcessHandler);
    AndroidProcessText.attach(newProcessHandler);
    newProcessHandler.notifyTextAvailable("The session was restarted\n", STDOUT);

    state.addListener(new AndroidRunningStateListener() {
      @Override
      public void executionFailed() {
        LaunchUtils.showNotification(project, executor, oldDescriptor, "Error", NotificationType.ERROR);
      }

      @Override
      public void executionStarted(@NotNull IDevice device) {
        // no need to show for debug executor: debug launches open the tool window, and even if not, the debug runner will show
        // the notification when it attaches to the debugger, we don't want the notification to show up when the activity is launched
        if (executor instanceof DefaultRunExecutor) {
          LaunchUtils.showNotification(project, executor, oldDescriptor, "Running", NotificationType.INFORMATION);
        }
      }
    });

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        state.start();
      }
    });
    return oldDescriptor;
  }

  @Override
  @NotNull
  public String getRunnerId() {
    return "AndroidDebugRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && !DefaultRunExecutor.EXECUTOR_ID.equals(executorId)) {
      return false;
    }

    if (!(profile instanceof AndroidRunConfigurationBase)) {
      return false;
    }

    return ((AndroidRunConfigurationBase)profile).usesSimpleLauncher();
  }

  private class MyDebugLauncher implements DebugLauncher {
    private final Project myProject;
    private final Executor myExecutor;
    private final AndroidRunningState myRunningState;
    private final ExecutionEnvironment myEnvironment;
    private RunContentDescriptor myRunDescriptor;

    public MyDebugLauncher(AndroidRunningState state,
                           ExecutionEnvironment environment) {
      myProject = environment.getProject();
      myRunningState = state;
      myEnvironment = environment;
      myExecutor = environment.getExecutor();
    }

    public void setRunDescriptor(RunContentDescriptor runDescriptor) {
      myRunDescriptor = runDescriptor;
    }

    @Override
    public void launchDebug(@NotNull final Client client) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
        public void run() {
          IDevice device = client.getDevice();
          String debugPort = Integer.toString(client.getDebuggerListenPort());

          RemoteConnection connection = new RemoteConnection(true, "localhost", debugPort, false);
          AndroidDebugState debugState = new AndroidDebugState(myProject, connection, myRunningState, device);
          RunContentDescriptor debugDescriptor = null;
          final ProcessHandler processHandler = myRunningState.getProcessHandler();
          processHandler.detachProcess();
          try {
            synchronized (myDebugLock) {
              assert myRunDescriptor != null;

              // @formatter:off
              ExecutionEnvironment env = new ExecutionEnvironmentBuilder(myEnvironment)
                .executor(myExecutor)
                .runner(AndroidDebugRunner.this)
                .contentToReuse(myRunDescriptor)
                .build();
              debugDescriptor = DebuggerPanelsManager.getInstance(myProject).attachVirtualMachine(
                env, debugState, debugState.getRemoteConnection(), false);
              // @formatter:on
            }
          }
          catch (ExecutionException e) {
            processHandler.notifyTextAvailable("ExecutionException: " + e.getMessage() + '.', STDERR);
          }
          ProcessHandler newProcessHandler = debugDescriptor != null ? debugDescriptor.getProcessHandler() : null;
          if (debugDescriptor == null || newProcessHandler == null) {
            LOG.info("cannot start debugging");
            return;
          }

          AndroidProcessText.attach(newProcessHandler);
          final AndroidProcessText oldText = AndroidProcessText.get(processHandler);
          if (oldText != null) {
            oldText.printTo(newProcessHandler);
          }

          ProcessHandler handler = myRunningState.getProcessHandler();
          handler.putUserData(ANDROID_SESSION_INFO, new AndroidSessionInfo(handler, debugDescriptor, debugState, myExecutor.getId()));
          debugDescriptor.setActivateToolWindowWhenAdded(false);
        }
      });
    }
  }
}
