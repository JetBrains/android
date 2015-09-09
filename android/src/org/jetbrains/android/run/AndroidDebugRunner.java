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
package org.jetbrains.android.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.*;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.List;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author coyote
 */
public class AndroidDebugRunner extends DefaultProgramRunner {
  public static final Key<AndroidSessionInfo> ANDROID_SESSION_INFO = new Key<AndroidSessionInfo>("ANDROID_SESSION_INFO");

  private static final Object myDebugLock = new Object();
  public static final String ANDROID_LOGCAT_CONTENT_ID = "Android Logcat";
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidDebugRunner");

  private static NotificationGroup ourNotificationGroup; // created and accessed only in EDT

  private static void tryToCloseOldSessions(final Executor executor, Project project) {
    final ExecutionManager manager = ExecutionManager.getInstance(project);
    ProcessHandler[] processes = manager.getRunningProcesses();
    for (ProcessHandler process : processes) {
      final AndroidSessionInfo info = process.getUserData(ANDROID_SESSION_INFO);
      if (info != null) {
        process.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(ProcessEvent event) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                manager.getContentManager().removeRunContent(executor, info.getDescriptor());
              }
            });
          }
        });
        process.detachProcess();
      }
    }
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull final RunProfileState state, @NotNull final ExecutionEnvironment environment) throws ExecutionException {
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
              showNotification(environment.getProject(), environment.getExecutor(), descriptor[0], "error", false, NotificationType.ERROR);
            }
          }
        });
      }
    });
    descriptor[0] = doExec(runningState, environment);
    return descriptor[0];
  }

  /** Executes states that do not require Android debugging. May set Android tool window to open on launch. */
  private RunContentDescriptor doExecSimple(
    @NotNull final RunProfileState state,
    @NotNull final ExecutionEnvironment environment
  ) throws ExecutionException {
    final RunContentDescriptor descriptor = super.doExecute(state, environment);
    if (descriptor != null) {
        // suppress the run tool window because it takes focus away
        deactivateToolWindowWhenAddedProperty(environment.getProject(), environment.getExecutor(), descriptor, "running");
    }
    return descriptor;
  }

  private RunContentDescriptor doExec(AndroidRunningState state, ExecutionEnvironment environment) throws ExecutionException {
    if (!(environment.getExecutor() instanceof DefaultDebugExecutor)) {
      return doExecSimple(state, environment);
    }

    final RunProfile runProfile = environment.getRunProfile();
    if (runProfile instanceof AndroidTestRunConfiguration) {
      // attempt to set the target package only in case on non Gradle projects
      if (!state.getFacet().requiresAndroidModel()) {
        String targetPackage = getTargetPackage((AndroidTestRunConfiguration)runProfile, state);
        if (targetPackage == null) {
          throw new ExecutionException(AndroidBundle.message("target.package.not.specified.error"));
        }
        state.setTargetPackageName(targetPackage);
      }
    }

    state.setDebugMode(true);
    RunContentDescriptor runDescriptor;
    synchronized (myDebugLock) {
      MyDebugLauncher launcher = new MyDebugLauncher(state, environment);
      state.setDebugLauncher(launcher);

      final RunContentDescriptor descriptor = embedToExistingSession(environment.getProject(), environment.getExecutor(), state);
      runDescriptor = descriptor != null ? descriptor : super.doExecute(state, environment);
      launcher.setRunDescriptor(runDescriptor);
      if (descriptor != null) {
        return null;
      }
    }
    if (runDescriptor == null) {
      return null;
    }
    tryToCloseOldSessions(environment.getExecutor(), environment.getProject());
    final ProcessHandler handler = state.getProcessHandler();
    handler.putUserData(ANDROID_SESSION_INFO, new AndroidSessionInfo(runDescriptor, state, environment.getExecutor().getId()));
    deactivateToolWindowWhenAddedProperty(environment.getProject(), environment.getExecutor(), runDescriptor, "running");
    return runDescriptor;
  }

  private static void deactivateToolWindowWhenAddedProperty(Project project,
                                                            Executor executor,
                                                            RunContentDescriptor descriptor,
                                                            String status) {
    // don't pop up the tool window (AndroidRunningState.execute takes care of popping up the tool window if there are errors)
    descriptor.setActivateToolWindowWhenAdded(false);
    // but show a notification that a launch took place
    showNotification(project, executor, descriptor, status, false, NotificationType.INFORMATION);
  }

  @Nullable
  private static Pair<ProcessHandler, AndroidSessionInfo> findOldSession(Project project,
                                                                         Executor executor,
                                                                         AndroidRunConfigurationBase configuration) {
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      final AndroidSessionInfo info = handler.getUserData(ANDROID_SESSION_INFO);

      if (info != null &&
          info.getState().getConfiguration().equals(configuration) &&
          executor.getId().equals(info.getExecutorId())) {
        return Pair.create(handler, info);
      }
    }
    return null;
  }

  @Nullable
  protected static RunContentDescriptor embedToExistingSession(final Project project,
                                                               final Executor executor,
                                                               final AndroidRunningState state) {
    final Pair<ProcessHandler, AndroidSessionInfo> pair = findOldSession(project, executor, state.getConfiguration());
    final AndroidSessionInfo oldSessionInfo = pair != null ? pair.getSecond() : null;
    final ProcessHandler oldProcessHandler = pair != null ? pair.getFirst() : null;

    if (oldSessionInfo == null || oldProcessHandler == null) {
      return null;
    }
    final AndroidExecutionState oldState = oldSessionInfo.getState();
    final IDevice[] oldDevices = oldState.getDevices();
    final ConsoleView oldConsole = oldState.getConsoleView();

    if (oldDevices == null ||
        oldConsole == null ||
        oldDevices.length == 0 ||
        oldDevices.length > 1) {
      return null;
    }
    final Ref<List<IDevice>> devicesRef = Ref.create();

    final boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        devicesRef.set(state.getAllCompatibleDevices());
      }
    }, "Scanning available devices", false, project);

    if (!result) {
      return null;
    }
    final List<IDevice> devices = devicesRef.get();

    if (devices.size() == 0 ||
        devices.size() > 1 ||
        devices.get(0) != oldDevices[0]) {
      return null;
    }
    oldProcessHandler.detachProcess();
    state.setTargetDevices(devices.toArray(new IDevice[devices.size()]));
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

    showNotification(project, executor, oldDescriptor, "running", false, NotificationType.INFORMATION);
    state.addListener(new AndroidRunningStateListener() {
      @Override
      public void executionFailed() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            showNotification(project, executor, oldDescriptor, "error", false, NotificationType.ERROR);
          }
        });
      }
    });

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        state.start(false);
      }
    });
    return oldDescriptor;
  }

  private static void showNotification(final Project project,
                                       final Executor executor,
                                       final RunContentDescriptor descriptor,
                                       final String status,
                                       final boolean notifySelectedContent,
                                       final NotificationType type) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) {
          return;
        }
        final String sessionName = descriptor.getDisplayName();
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(executor.getToolWindowId());
        final Content content = descriptor.getAttachedContent();
        final String notificationMessage;
        if (content != null && content.isSelected() && toolWindow.isVisible()) {
          if (!notifySelectedContent) {
            return;
          }
          notificationMessage = "Session '" + sessionName + "': " + status;
        }
        else {
          notificationMessage = "Session <a href=''>'" + sessionName + "'</a>: " + status;
        }

        if (ourNotificationGroup == null) {
          ourNotificationGroup = NotificationGroup.toolWindowGroup("Android Session Restarted", executor.getToolWindowId());
        }

        ourNotificationGroup
          .createNotification("", notificationMessage, type, new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
              if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                final RunContentManager contentManager = ExecutionManager.getInstance(project).getContentManager();

                for (RunContentDescriptor d : contentManager.getAllDescriptors()) {
                  if (sessionName.equals(d.getDisplayName())) {
                    final Content content = d.getAttachedContent();
                    content.getManager().setSelectedContent(content);
                    toolWindow.activate(null, true, true);
                    break;
                  }
                }
              }
            }
          }).notify(project);
      }
    });
  }

  @Nullable
  private static String getTargetPackage(AndroidTestRunConfiguration configuration, AndroidRunningState state) {
    Manifest manifest = state.getFacet().getManifest();
    assert manifest != null;
    for (Instrumentation instrumentation : manifest.getInstrumentations()) {
      PsiClass c = instrumentation.getInstrumentationClass().getValue();
      String runner = configuration.INSTRUMENTATION_RUNNER_CLASS;
      if (c != null && (runner.length() == 0 || runner.equals(c.getQualifiedName()))) {
        String targetPackage = instrumentation.getTargetPackage().getValue();
        if (targetPackage != null) {
          return targetPackage;
        }
      }
    }
    return null;
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

          final DebuggerPanelsManager manager = DebuggerPanelsManager.getInstance(myProject);
          AndroidDebugState st =
            new AndroidDebugState(myProject, new RemoteConnection(true, "localhost", debugPort, false),
                                  myRunningState, device);
          RunContentDescriptor debugDescriptor = null;
          final ProcessHandler processHandler = myRunningState.getProcessHandler();
          processHandler.detachProcess();
          try {
            synchronized (myDebugLock) {
              assert myRunDescriptor != null;
              debugDescriptor = manager.attachVirtualMachine(new ExecutionEnvironmentBuilder(myEnvironment)
                                                               .executor(myExecutor)
                                                               .runner(AndroidDebugRunner.this)
                                                               .contentToReuse(myRunDescriptor)
                                                               .build(), st, st.getRemoteConnection(), false);
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

          myRunningState.getProcessHandler().putUserData(ANDROID_SESSION_INFO,
                                                         new AndroidSessionInfo(debugDescriptor, st, myExecutor.getId()));
          deactivateToolWindowWhenAddedProperty(myProject, myExecutor, debugDescriptor, "debugger connected");
        }
      });
    }
  }
}
