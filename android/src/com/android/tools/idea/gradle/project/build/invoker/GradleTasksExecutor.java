/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker;

<<<<<<< HEAD
=======
import com.android.builder.model.AndroidProject;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.fd.FlightRecorder;
import com.android.tools.idea.fd.InstantRunBuildProgressListener;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.gradle.output.parser.BuildOutputParser;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.AfterGradleInvocationTask;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
>>>>>>> goog/upstream-ij17
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
<<<<<<< HEAD
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.MessageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
=======
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBusConnection;
import net.jcip.annotations.GuardedBy;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleBuilds.CONFIGURE_ON_DEMAND_OPTION;
import static com.android.tools.idea.gradle.util.GradleBuilds.PARALLEL_BUILD_OPTION;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.run.editor.ProfilerState.ENABLE_EXPERIMENTAL_PROFILING;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.application.ModalityState.NON_MODAL;
import static com.intellij.openapi.ui.MessageType.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.jetbrains.android.AndroidPlugin.*;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;
>>>>>>> goog/upstream-ij17

/**
 * Invokes Gradle tasks as a IDEA task in the background.
 */
public abstract class GradleTasksExecutor extends Task.Backgroundable {
  @NotNull public static final NotificationGroup LOGGING_NOTIFICATION = NotificationGroup.logOnlyGroup("Gradle Build (Logging)");
  @NotNull public static final NotificationGroup BALLOON_NOTIFICATION = NotificationGroup.balloonGroup("Gradle Build (Balloon)");

<<<<<<< HEAD
  protected static final Key<Key<?>> CONTENT_ID_KEY = Key.create("CONTENT_ID");

=======
>>>>>>> goog/upstream-ij17
  protected GradleTasksExecutor(@Nullable Project project) {
    super(project, "Gradle Build Running", true);
  }

  /**
   * Regular {@link #queue()} method might return immediately if current task is executed in a separate non-calling thread.
   * <p/>
   * However, sometimes we want to wait for the task completion, e.g. consider a use-case when we execute an IDE run configuration.
   * It opens dedicated run/debug tool window and displays execution output there. However, it is shown as finished as soon as
   * control flow returns. That's why we don't want to return control flow until the actual task completion.
   * <p/>
   * This method allows to achieve that target - it executes gradle tasks under the IDE 'progress management system' (shows progress
   * bar at the bottom) in a separate thread and doesn't return control flow to the calling thread until all target tasks are actually
   * executed.
   */
  public abstract void queueAndWaitForCompletion();

<<<<<<< HEAD
  public static void clearMessageView(@NotNull Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!project.isDisposed()) {
        removeUnpinnedBuildMessages(project, null);
      }
    });
  }

  protected static void removeUnpinnedBuildMessages(@NotNull Project project, @Nullable Content toKeep) {
    if (project.isInitialized()) {
      MessageView messageView = MessageView.SERVICE.getInstance(project);
      Content[] contents = messageView.getContentManager().getContents();
      for (Content content : contents) {
        if (content.isPinned() || content == toKeep) {
          continue;
        }
        if (content.getUserData(CONTENT_ID_KEY) != null) { // the content was added by me
          messageView.getContentManager().removeContent(content, true);
        }
=======
  static class Factory {
    @NotNull
    GradleTasksExecutor create(@NotNull GradleBuildInvoker.Request request,
                               @NotNull BuildStopper buildStopper) {
      return new GradleTasksExecutorImpl(request, buildStopper);
    }
  }

  private static class GradleTasksExecutorImpl extends GradleTasksExecutor {
    private static final long ONE_MINUTE_MS = 60L /*sec*/ * 1000L /*millisec*/;

    @NonNls private static final String APP_ICON_ID = "compiler";

    private static final String GRADLE_RUNNING_MSG_TITLE = "Gradle Running";
    private static final String PASSWORD_KEY_SUFFIX = ".password=";

    public static final String ANDROID_ADDITIONAL_PLUGINS = "android.additional.plugins";
    public static final String COM_ANDROID_TOOLS_PROFILER = "com.android.tools.profiler";

    @NotNull private final Object myCompletionLock = new Object();

    @NotNull private final GradleBuildInvoker.Request myRequest;
    @NotNull private final BuildStopper myBuildStopper;

    @GuardedBy("myCompletionLock")
    private int myCompletionCounter;

    @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

    private volatile int myErrorCount;
    private volatile int myWarningCount;

    @NotNull private volatile ProgressIndicator myProgressIndicator = new EmptyProgressIndicator();

    private CloseListener myCloseListener;

    @VisibleForTesting
    GradleTasksExecutorImpl(@NotNull GradleBuildInvoker.Request request,
                            @NotNull BuildStopper buildStopper) {
      super(request.getProject());
      myRequest = request;
      myBuildStopper = buildStopper;
    }

    @Override
    public String getProcessId() {
      return "GradleTaskInvocation";
    }

    @Override
    @Nullable
    public NotificationInfo getNotificationInfo() {
      return new NotificationInfo(myErrorCount > 0 ? "Gradle Invocation (errors)" : "Gradle Invocation (success)",
                                  "Gradle Invocation Finished", myErrorCount + " Errors, " + myWarningCount + " Warnings", true);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      if (IdeInfo.getInstance().isAndroidStudio()) {
        // See https://code.google.com/p/android/issues/detail?id=169743
        clearStoredGradleJvmArgs(getProject());
      }

      myProgressIndicator = indicator;

      Project project = myRequest.getProject();
      myCloseListener = new CloseListener();
      MessageBusConnection busConnection = project.getMessageBus().connect();
      busConnection.subscribe(ProjectManager.TOPIC, myCloseListener);
      Semaphore semaphore = ((CompilerManagerImpl)CompilerManager.getInstance(project)).getCompilationSemaphore();
      boolean acquired = false;
      try {
        try {
          while (!acquired) {
            acquired = semaphore.tryAcquire(300, MILLISECONDS);
            if (myProgressIndicator.isCanceled()) {
              // Give up obtaining the semaphore, let compile work begin in order to stop gracefully on cancel event.
              break;
            }
          }
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        addIndicatorDelegate();
        invokeGradleTasks();
      }
      finally {
        try {
          myProgressIndicator.stop();
          busConnection.disconnect();
        }
        finally {
          if (acquired) {
            semaphore.release();
          }
        }
      }
    }

    private void addIndicatorDelegate() {
      if (myProgressIndicator instanceof ProgressIndicatorEx) {
        ProgressIndicatorEx indicator = (ProgressIndicatorEx)myProgressIndicator;
        indicator.addStateDelegate(new ProgressIndicatorStateDelegate(myRequest.getTaskId(), myBuildStopper));
      }
    }

    private void invokeGradleTasks() {
      Project project = myRequest.getProject();
      GradleExecutionSettings executionSettings = getOrCreateGradleExecutionSettings(project, myRequest.isUseEmbeddedGradle());

      Function<ProjectConnection, Void> executeTasksFunction = connection -> {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<String> gradleTasks = myRequest.getGradleTasks();
        String executingTasksText = "Executing tasks: " + gradleTasks;
        addToEventLog(executingTasksText, INFO);

        StringBuilder output = new StringBuilder();

        Throwable buildError = null;
        InstantRunBuildProgressListener instantRunProgressListener = null;
        ExternalSystemTaskId id = myRequest.getTaskId();
        ExternalSystemTaskNotificationListener taskListener = myRequest.getTaskListener();
        CancellationTokenSource cancellationTokenSource = myBuildStopper.createAndRegisterTokenSource(id);
        if (taskListener != null) {
          taskListener.onStart(id, myRequest.getBuildFilePath().getPath());
          taskListener.onTaskOutput(
            id, executingTasksText + SystemProperties.getLineSeparator() + SystemProperties.getLineSeparator(), true);
        }
        try {
          AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
          List<String> commandLineArguments = Lists.newArrayList(buildConfiguration.getCommandLineOptions());

          if (buildConfiguration.USE_CONFIGURATION_ON_DEMAND && !commandLineArguments.contains(CONFIGURE_ON_DEMAND_OPTION)) {
            commandLineArguments.add(CONFIGURE_ON_DEMAND_OPTION);
          }

          if (!commandLineArguments.contains(PARALLEL_BUILD_OPTION) &&
              CompilerWorkspaceConfiguration.getInstance(project).PARALLEL_COMPILATION) {
            commandLineArguments.add(PARALLEL_BUILD_OPTION);
          }

          commandLineArguments.add(createProjectProperty(AndroidProject.PROPERTY_INVOKED_FROM_IDE, true));
          commandLineArguments.addAll(myRequest.getCommandLineArguments());

          GradleInitScripts initScripts = GradleInitScripts.getInstance();
          initScripts.addLocalMavenRepoInitScriptCommandLineArgTo(commandLineArguments);

          attemptToUseEmbeddedGradle(project);

          if (System.getProperty(ENABLE_EXPERIMENTAL_PROFILING) != null) {
            initScripts.addProfilerClasspathInitScriptCommandLineArgTo(commandLineArguments);
            commandLineArguments.add(createProjectProperty(ANDROID_ADDITIONAL_PLUGINS, COM_ANDROID_TOOLS_PROFILER));
          }

          // Don't include passwords in the log
          String logMessage = "Build command line options: " + commandLineArguments;
          if (logMessage.contains(PASSWORD_KEY_SUFFIX)) {
            List<String> replaced = new ArrayList<>(commandLineArguments.size());
            for (String option : commandLineArguments) {
              // -Pandroid.injected.signing.store.password=, -Pandroid.injected.signing.key.password=
              int index = option.indexOf(".password=");
              if (index == -1) {
                replaced.add(option);
              }
              else {
                replaced.add(option.substring(0, index + PASSWORD_KEY_SUFFIX.length()) + "*********");
              }
            }
            logMessage = replaced.toString();
          }
          getLogger().info(logMessage);

          executionSettings
            .withVmOptions(myRequest.getJvmArguments())
            .withArguments(commandLineArguments)
            .withEnvironmentVariables(myRequest.getEnv())
            .passParentEnvs(myRequest.isPassParentEnvs());
          BuildLauncher launcher = connection.newBuild();

          prepare(launcher, id, executionSettings, new ExternalSystemTaskNotificationListenerAdapter() {
            @Override
            public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
              if (taskListener != null) {
                if (myBuildStopper.contains(id)) {
                  taskListener.onStatusChange(event);
                }
              }
            }

            @Override
            public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
              output.append(text);
              if (taskListener != null) {
                if (myBuildStopper.contains(id)) {
                  taskListener.onTaskOutput(id, text, stdOut);
                }
              }
            }
          }, connection);

          File javaHome = IdeSdks.getInstance().getJdkPath();
          if (javaHome != null) {
            launcher.setJavaHome(javaHome);
          }

          launcher.forTasks(toStringArray(gradleTasks));
          launcher.withCancellationToken(cancellationTokenSource.token());

          if (InstantRunSettings.isInstantRunEnabled() && InstantRunSettings.isRecorderEnabled()) {
            instantRunProgressListener = new InstantRunBuildProgressListener();
            launcher.addProgressListener(instantRunProgressListener);
          }

          launcher.run();
        }
        catch (BuildException e) {
          buildError = e;
        }
        catch (Throwable e) {
          buildError = e;
          handleTaskExecutionError(e);
        }
        finally {
          if (myBuildStopper.contains(id) && taskListener != null) {
            if (buildError != null) {
              Throwable rootCause = getRootCause(buildError);
              taskListener.onFailure(id, new ExternalSystemException(ExceptionUtil.getMessage(rootCause)));
            }
            else {
              taskListener.onSuccess(id);
            }
            taskListener.onEnd(id);
          }
          myBuildStopper.remove(id);
          String gradleOutput = output.toString();
          if (instantRunProgressListener != null) {
            FlightRecorder.get(myProject).saveBuildOutput(gradleOutput, instantRunProgressListener);
          }
          Application application = ApplicationManager.getApplication();
          if (isGuiTestingMode()) {
            String testOutput = application.getUserData(GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY);
            if (isNotEmpty(testOutput)) {
              gradleOutput = testOutput;
              application.putUserData(GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY, null);
            }
          }
          executeAfterGradleTasks(gradleOutput, stopwatch, buildError);
        }
        return null;
      };

      if (isGuiTestingMode()) {
        // We use this task in GUI tests to simulate errors coming from Gradle project sync.
        Application application = ApplicationManager.getApplication();
        Runnable task = application.getUserData(EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY);
        if (task != null) {
          application.putUserData(EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, null);
          task.run();
        }
      }

      myHelper.execute(myRequest.getBuildFilePath().getPath(), executionSettings, executeTasksFunction);
    }

    private void executeAfterGradleTasks(@NotNull String gradleOutput,
                                         @NotNull Stopwatch stopwatch,
                                         @Nullable Throwable buildError) {
      Application application = ApplicationManager.getApplication();

      List<Message> buildMessages = new ArrayList<>();
      collectMessages(gradleOutput, buildMessages).doWhenDone(() -> {
        stopwatch.stop();
        add(buildMessages);

        application.invokeLater(() -> notifyGradleInvocationCompleted(stopwatch.elapsed(MILLISECONDS)));
        if (getProject().isDisposed()) {
          return;
        }

        boolean buildSuccessful = buildError == null;
        GradleInvocationResult result = new GradleInvocationResult(myRequest.getGradleTasks(), buildMessages, buildSuccessful);
        for (AfterGradleInvocationTask task : GradleBuildInvoker.getInstance(getProject()).getAfterInvocationTasks()) {
          task.execute(result);
        }
      });
    }

    @NotNull
    private static ActionCallback collectMessages(@NotNull String gradleOutput, @NotNull List<Message> messages) {
      ActionCallback callback = new ActionCallback();

      Runnable task = () -> {
        Iterable<PatternAwareOutputParser> parsers = JpsServiceManager.getInstance().getExtensions(PatternAwareOutputParser.class);
        List<Message> compilerMessages = new BuildOutputParser(parsers).parseGradleOutput(gradleOutput, true);
        messages.addAll(compilerMessages);
        callback.setDone();
      };

      Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode()) {
        task.run();
      }
      else {
        application.executeOnPooledThread(task);
      }
      return callback;
    }

    private void add(@NotNull List<Message> buildMessages) {
      Runnable addMessageTask = () -> {
        for (Message message : buildMessages) {
          incrementErrorOrWarningCount(message);
        }
      };
      ApplicationManager.getApplication().invokeLater(addMessageTask, NON_MODAL);
    }

    private void handleTaskExecutionError(@NotNull Throwable e) {
      if (myProgressIndicator.isCanceled()) {
        getLogger().info("Failed to complete Gradle execution. Project may be closing or already closed.", e);
        return;
      }
      //noinspection ThrowableResultOfMethodCallIgnored
      Throwable rootCause = getRootCause(e);
      String error = nullToEmpty(rootCause.getMessage());
      if (error.contains("Build cancelled")) {
        return;
      }
      Runnable showErrorTask = () -> {
        String msg = "Failed to complete Gradle execution.";
        if (isEmpty(error)) {
          // Unlikely that 'error' is null or empty, since now we catch the real exception.
          msg += " Cause: unknown.";
        }
        else {
          msg += "\n\nCause:\n" + error;
        }
        incrementErrorOrWarningCount(new Message(Message.Kind.ERROR, msg, SourceFilePosition.UNKNOWN));

        // This is temporary. Once we have support for hyperlinks in "Messages" window, we'll show the error message the with a
        // hyperlink to set the JDK home.
        // For now we show the "Select SDK" dialog, but only giving the option to set the JDK path.
        if (IdeInfo.getInstance().isAndroidStudio() && error.startsWith("Supplied javaHome is not a valid folder")) {
          IdeSdks ideSdks = IdeSdks.getInstance();
          File androidHome = ideSdks.getAndroidSdkPath();
          String androidSdkPath = androidHome != null ? androidHome.getPath() : null;
          SelectSdkDialog selectSdkDialog = new SelectSdkDialog(null, androidSdkPath);
          selectSdkDialog.setModal(true);
          if (selectSdkDialog.showAndGet()) {
            String jdkHome = selectSdkDialog.getJdkHome();
            invokeLaterIfNeeded(() -> ApplicationManager.getApplication().runWriteAction(() -> ideSdks.setJdkPath(new File(jdkHome))));
          }
        }
      };
      invokeLaterIfProjectAlive(myRequest.getProject(), showErrorTask);
    }

    private void incrementErrorOrWarningCount(@NotNull Message message) {
      Message.Kind kind = message.getKind();
      if (kind == Message.Kind.WARNING) {
        myWarningCount++;
      }
      else if (kind == Message.Kind.ERROR) {
        myErrorCount++;
      }
    }

    @NotNull
    private static Logger getLogger() {
      return Logger.getInstance(GradleBuildInvoker.class);
    }

    private void notifyGradleInvocationCompleted(long durationMillis) {
      Project project = myRequest.getProject();
      if (!project.isDisposed()) {
        String statusMsg = createStatusMessage(durationMillis);
        MessageType messageType = myErrorCount > 0 ? ERROR : myWarningCount > 0 ? WARNING : INFO;
        if (durationMillis > ONE_MINUTE_MS) {
          BALLOON_NOTIFICATION.createNotification(statusMsg, messageType).notify(project);
        }
        else {
          addToEventLog(statusMsg, messageType);
        }
      }
    }

    @NotNull
    private String createStatusMessage(long durationMillis) {
      String message = "Gradle build finished";
      if (myErrorCount > 0) {
        if (myWarningCount > 0) {
          message += String.format(" with %d error(s) and %d warning(s)", myErrorCount, myWarningCount);
        }
        else {
          message += String.format(" with %d error(s)", myErrorCount);
        }
      }
      else if (myWarningCount > 0) {
        message += String.format(" with %d warnings(s)", myWarningCount);
      }
      message = message + " in " + formatDuration(durationMillis);
      return message;
    }

    private void addToEventLog(@NotNull String message, @NotNull MessageType type) {
      LOGGING_NOTIFICATION.createNotification(message, type).notify(myProject);
    }

    private void attemptToStopBuild() {
      myBuildStopper.attemptToStopBuild(myRequest.getTaskId(), myProgressIndicator);
    }

    /**
     * Regular {@link #queue()} method might return immediately if current task is executed in a separate non-calling thread.
     * <p/>
     * However, sometimes we want to wait for the task completion, e.g. consider a use-case when we execute an IDE run configuration.
     * It opens dedicated run/debug tool window and displays execution output there. However, it is shown as finished as soon as
     * control flow returns. That's why we don't want to return control flow until the actual task completion.
     * <p/>
     * This method allows to achieve that target - it executes gradle tasks under the IDE 'progress management system' (shows progress
     * bar at the bottom) in a separate thread and doesn't return control flow to the calling thread until all target tasks are actually
     * executed.
     */
    @Override
    public void queueAndWaitForCompletion() {
      int counterBefore;
      synchronized (myCompletionLock) {
        counterBefore = myCompletionCounter;
      }
      invokeLaterIfNeeded(this::queue);
      synchronized (myCompletionLock) {
        while (true) {
          if (myCompletionCounter > counterBefore) {
            break;
          }
          try {
            myCompletionLock.wait();
          }
          catch (InterruptedException e) {
            // Just stop waiting.
            break;
          }
        }
      }
    }

    @Override
    public void onSuccess() {
      super.onSuccess();
      onCompletion();
    }

    @Override
    public void onCancel() {
      super.onCancel();
      onCompletion();
    }

    private void onCompletion() {
      synchronized (myCompletionLock) {
        myCompletionCounter++;
        myCompletionLock.notifyAll();
      }
    }

    private class CloseListener extends ContentManagerAdapter implements VetoableProjectManagerListener {

      private boolean myIsApplicationExitingOrProjectClosing;
      private boolean myUserAcceptedCancel;

      @Override
      public boolean canClose(@NotNull Project project) {
        if (project != myProject) {
          return true;
        }

        if (shouldPromptUser()) {
          myUserAcceptedCancel = askUserToCancelGradleExecution();
          if (!myUserAcceptedCancel) {
            return false; // veto closing
          }
          attemptToStopBuild();
          return true;
        }
        return !myProgressIndicator.isRunning();
      }

      @Override
      public void projectClosing(Project project) {
        if (project.equals(myProject)) {
          myIsApplicationExitingOrProjectClosing = true;
        }
      }

      private boolean shouldPromptUser() {
        return !myUserAcceptedCancel && !myIsApplicationExitingOrProjectClosing && myProgressIndicator.isRunning();
      }

      private boolean askUserToCancelGradleExecution() {
        String msg = "Gradle is running. Proceed with Project closing?";
        int result = Messages.showYesNoDialog(myProject, msg, GRADLE_RUNNING_MSG_TITLE, Messages.getQuestionIcon());
        return result == Messages.YES;
      }
    }

    private class ProgressIndicatorStateDelegate extends TaskExecutionProgressIndicator {
      ProgressIndicatorStateDelegate(@NotNull ExternalSystemTaskId taskId,
                                     @NotNull BuildStopper buildStopper) {
        super(taskId, buildStopper);
      }

      @Override
      void onCancel() {
        stopAppIconProgress();
      }

      @Override
      public void stop() {
        super.stop();
        stopAppIconProgress();
      }

      private void stopAppIconProgress() {
        invokeLaterIfNeeded(() -> {
          AppIcon appIcon = AppIcon.getInstance();
          Project project = myRequest.getProject();
          if (appIcon.hideProgress(project, APP_ICON_ID)) {
            if (myErrorCount > 0) {
              appIcon.setErrorBadge(project, String.valueOf(myErrorCount));
              appIcon.requestAttention(project, true);
            }
            else {
              appIcon.setOkBadge(project, true);
              appIcon.requestAttention(project, false);
            }
          }
        });
>>>>>>> goog/upstream-ij17
      }
    }
  }
}
