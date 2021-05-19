/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager.setJdkAsProjectJdk;
import static com.android.tools.idea.gradle.project.build.BuildStatus.CANCELED;
import static com.android.tools.idea.gradle.project.build.BuildStatus.FAILED;
import static com.android.tools.idea.gradle.project.build.BuildStatus.SUCCESS;
import static com.android.tools.idea.gradle.project.sync.common.CommandLineArgs.isInTestingMode;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleBuilds.PARALLEL_BUILD_OPTION;
import static com.android.tools.idea.gradle.util.GradleUtil.attemptToUseEmbeddedGradle;
import static com.android.tools.idea.gradle.util.GradleUtil.getOrCreateGradleExecutionSettings;
import static com.android.tools.idea.gradle.util.GradleUtil.hasCause;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.ui.MessageType.ERROR;
import static com.intellij.openapi.ui.MessageType.INFO;
import static com.intellij.openapi.util.text.StringUtil.formatDuration;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.BuildSummary;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager;
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionUtil;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.common.AndroidSupportVersionUtilKt;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.Function;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.events.OperationType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

class GradleTasksExecutorImpl extends GradleTasksExecutor {
  private static final long ONE_MINUTE_MS = 60L /*sec*/ * 1000L /*millisec*/;

  @NonNls private static final String APP_ICON_ID = "compiler";

  private static final String GRADLE_RUNNING_MSG_TITLE = "Gradle Running";
  private static final String PASSWORD_KEY_SUFFIX = ".password=";

  @NotNull private final GradleBuildInvoker.Request myRequest;
  @NotNull private final BuildStopper myBuildStopper;
  @NotNull private final SettableFuture<GradleInvocationResult> myResultFuture;
  @NotNull private final ExternalSystemTaskNotificationListener myListener;

  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  private volatile int myErrorCount;

  @NotNull private volatile ProgressIndicator myProgressIndicator = new EmptyProgressIndicator();

  GradleTasksExecutorImpl(@NotNull GradleBuildInvoker.Request request,
                          @NotNull BuildStopper buildStopper,
                          @NotNull ExternalSystemTaskNotificationListener listener,
                          @NotNull SettableFuture<GradleInvocationResult> resultFuture) {
    super(request.getProject());
    myRequest = request;
    myBuildStopper = buildStopper;
    myListener = listener;
    myResultFuture = resultFuture;
  }

  @Override
  @Nullable
  public NotificationInfo getNotificationInfo() {
    return new NotificationInfo(myErrorCount > 0 ? "Gradle Invocation (errors)" : "Gradle Invocation (success)",
                                "Gradle Invocation Finished", myErrorCount + " Errors", true);
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      myProgressIndicator = indicator;

      ProjectManager projectManager = ProjectManager.getInstance();
      Project project = myRequest.getProject();
      CloseListener closeListener = new CloseListener();
      projectManager.addProjectManagerListener(project, closeListener);

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
        myResultFuture.set(invokeGradleTasks());
      }
      finally {
        try {
          myProgressIndicator.stop();
          projectManager.removeProjectManagerListener(project, closeListener);
        }
        finally {
          if (acquired) {
            semaphore.release();
          }
        }
      }
    }
    catch (Throwable t) {
      myResultFuture.setException(t);
      throw t;
    }
  }

  private void addIndicatorDelegate() {
    if (myProgressIndicator instanceof ProgressIndicatorEx) {
      ProgressIndicatorEx indicator = (ProgressIndicatorEx)myProgressIndicator;
      indicator.addStateDelegate(new ProgressIndicatorStateDelegate(myRequest.getTaskId(), myBuildStopper));
    }
  }

  private static void setUpBuildAttributionManager(LongRunningOperation operation,
                                                   BuildAttributionManager buildAttributionManager,
                                                   boolean skipIfNull) {
    if (skipIfNull && buildAttributionManager == null) {
      return;
    }
    operation.addProgressListener(buildAttributionManager, OperationType.PROJECT_CONFIGURATION, OperationType.TASK, OperationType.TEST);
    buildAttributionManager.onBuildStart();
  }

  @NotNull
  private GradleInvocationResult invokeGradleTasks() {

    Project project = myRequest.getProject();
    GradleExecutionSettings executionSettings = getOrCreateGradleExecutionSettings(project);

    AtomicReference<Object> model = new AtomicReference<>(null);

    String gradleRootProjectPath = myRequest.getRootProjectPath().getPath();
    Function<ProjectConnection, GradleInvocationResult> executeTasksFunction = connection -> {
      Stopwatch stopwatch = Stopwatch.createStarted();

      BuildAction<?> buildAction = myRequest.getBuildAction();
      boolean isRunBuildAction = buildAction != null;

      List<String> gradleTasks = myRequest.getGradleTasks();
      String executingTasksText = "Executing tasks: " + gradleTasks + " in project " + gradleRootProjectPath;
      addToEventLog(executingTasksText, INFO);

      Throwable buildError = null;
      ExternalSystemTaskId id = myRequest.getTaskId();
      ExternalSystemTaskNotificationListener taskListener = myListener;
      CancellationTokenSource cancellationTokenSource = GradleConnector.newCancellationTokenSource();
      myBuildStopper.register(id, cancellationTokenSource);

      taskListener.onStart(id, gradleRootProjectPath);
      taskListener.onTaskOutput(id, executingTasksText + System.lineSeparator() + System.lineSeparator(), true);

      BuildMode buildMode = BuildSettings.getInstance(myProject).getBuildMode();
      GradleBuildState buildState = GradleBuildState.getInstance(myProject);
      buildState.buildStarted(new BuildContext(project, gradleTasks, buildMode));

      BuildAttributionManager buildAttributionManager = null;
      boolean enableBuildAttribution = BuildAttributionUtil.isBuildAttributionEnabledForProject(myProject);
      File attributionFileDir = null;

      try {
        AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
        List<String> commandLineArguments = Lists.newArrayList(buildConfiguration.getCommandLineOptions());

        if (!commandLineArguments.contains(PARALLEL_BUILD_OPTION) &&
            CompilerConfiguration.getInstance(project).isParallelCompilationEnabled()) {
          commandLineArguments.add(PARALLEL_BUILD_OPTION);
        }

        commandLineArguments.add(createProjectProperty(AndroidProject.PROPERTY_INVOKED_FROM_IDE, true));

        AndroidSupportVersionUtilKt.addAndroidSupportVersionArg(commandLineArguments);

        if (enableBuildAttribution) {
          attributionFileDir = BuildAttributionUtil.getAgpAttributionFileDir(myRequest.getRootProjectPath());
          commandLineArguments.add(createProjectProperty(AndroidProject.PROPERTY_ATTRIBUTION_FILE_LOCATION,
                                                         attributionFileDir.getAbsolutePath()));
        }
        commandLineArguments.addAll(myRequest.getCommandLineArguments());

        // Inject embedded repository if it's enabled by user.
        if (StudioFlags.USE_DEVELOPMENT_OFFLINE_REPOS.get() && !isInTestingMode()) {
          GradleInitScripts.getInstance().addLocalMavenRepoInitScriptCommandLineArg(commandLineArguments);
          attemptToUseEmbeddedGradle(project);
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
        LongRunningOperation operation = isRunBuildAction ? connection.action(buildAction) : connection.newBuild();
        operation.addProgressListener(new GradleToolingApiMemoryUsageFixingProgressListener(), OperationType.TASK);
        prepare(operation, id, executionSettings, new ExternalSystemTaskNotificationListenerAdapter() {
          @Override
          public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
            if (myBuildStopper.contains(id)) {
              taskListener.onStatusChange(event);
            }
          }

          @Override
          public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
            if (myBuildStopper.contains(id)) {
              taskListener.onTaskOutput(id, text, stdOut);
            }
          }
        }, connection);

        if (enableBuildAttribution) {
          buildAttributionManager = ServiceManager.getService(myProject, BuildAttributionManager.class);
          setUpBuildAttributionManager(operation, buildAttributionManager,
                                       // In some tests we don't care about build attribution being setup
                                       ApplicationManager.getApplication().isUnitTestMode());
        }

        if (isRunBuildAction) {
          ((BuildActionExecuter<?>)operation).forTasks(toStringArray(gradleTasks));
        }
        else {
          ((BuildLauncher)operation).forTasks(toStringArray(gradleTasks));
        }

        operation.withCancellationToken(cancellationTokenSource.token());

        if (isRunBuildAction) {
          model.set(((BuildActionExecuter<?>)operation).run());
        }
        else {
          ((BuildLauncher)operation).run();
        }

        buildState.buildFinished(SUCCESS);
        taskListener.onSuccess(id);
        if (buildAttributionManager != null) {
          buildAttributionManager.onBuildSuccess(attributionFileDir);
        }
      }
      catch (BuildException e) {
        buildError = e;
      }
      catch (Throwable e) {
        buildError = e;
        handleTaskExecutionError(e);
      }
      finally {
        Application application = ApplicationManager.getApplication();
        if (buildError != null) {
          if (buildAttributionManager != null) {
            final File finalAttributionFileDir = attributionFileDir;
            final BuildAttributionManager finalBuildAttributionManager = buildAttributionManager;
            application.invokeLater(() -> {
              if (!project.isDisposed()) {
                finalBuildAttributionManager.onBuildFailure(finalAttributionFileDir);
              }
            });
          }

          if (wasBuildCanceled(buildError)) {
            buildState.buildFinished(CANCELED);
            taskListener.onCancel(id);
          }
          else {
            buildState.buildFinished(FAILED);
            GradleProjectResolverExtension projectResolverChain = GradleProjectResolver.createProjectResolverChain();
            ExternalSystemException userFriendlyError =
              projectResolverChain.getUserFriendlyError(null, buildError, gradleRootProjectPath, null);
            taskListener.onFailure(id, userFriendlyError);
          }
        }
        taskListener.onEnd(id);
        myBuildStopper.remove(id);

        if (GuiTestingService.getInstance().isGuiTestingMode()) {
          String testOutput = application.getUserData(GuiTestingService.GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY);
          if (isNotEmpty(testOutput)) {
            application.putUserData(GuiTestingService.GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY, null);
          }
        }

        application.invokeLater(() -> notifyGradleInvocationCompleted(buildState, stopwatch.elapsed(MILLISECONDS)));

        if (!getProject().isDisposed()) {
          GradleInvocationResult result =
            new GradleInvocationResult(myRequest.getRootProjectPath(), myRequest.getGradleTasks(), buildError, model.get());
          RuntimeException error = null;
          for (GradleBuildInvoker.AfterGradleInvocationTask task : ((GradleBuildInvokerImpl)(GradleBuildInvoker.getInstance(getProject())))
            .getAfterInvocationTasks()) {
            try {
              task.execute(result);
            }
            catch (ProcessCanceledException e) {
              // Ignore process cancellation exceptions.
              // We must run all post invocation tasks in order to ensure all relevant locks are released.
            }
            catch (RuntimeException e) {
              if (error == null) {
                // Stash the first non-PCE exception to re-throw after all post invocation tasks had a chance to execute.
                error = e;
              }
            }
          }
          if (error != null) {
            throw error;
          }
          return result;
        }
      }
      return new GradleInvocationResult(myRequest.getRootProjectPath(), myRequest.getGradleTasks(), null);
    };

    if (GuiTestingService.getInstance().isGuiTestingMode()) {
      // We use this task in GUI tests to simulate errors coming from Gradle project sync.
      Application application = ApplicationManager.getApplication();
      Runnable task = application.getUserData(GuiTestingService.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY);
      if (task != null) {
        application.putUserData(GuiTestingService.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, null);
        task.run();
      }
    }

    try {
      return myHelper.execute(gradleRootProjectPath, executionSettings,
                              myRequest.getTaskId(), myListener, null, executeTasksFunction);
    }
    catch (ExternalSystemException e) {
      if (e.getOriginalReason().startsWith("com.intellij.openapi.progress.ProcessCanceledException")) {
        getLogger().info("Gradle execution cancelled.", e);
        return new GradleInvocationResult(myRequest.getRootProjectPath(), myRequest.getGradleTasks(), e);
      }
      else {
        throw e;
      }
    }
  }

  private static boolean wasBuildCanceled(@NotNull Throwable buildError) {
    return hasCause(buildError, BuildCancelledException.class) || hasCause(buildError, ProcessCanceledException.class);
  }

  private void handleTaskExecutionError(@NotNull Throwable e) {
    if (myProgressIndicator.isCanceled()) {
      getLogger().info("Failed to complete Gradle execution. Project may be closing or already closed.", e);
      return;
    }
    Throwable rootCause = getRootCause(e);
    String error = nullToEmpty(rootCause.getMessage());
    if (error.contains("Build cancelled")) {
      return;
    }
    Runnable showErrorTask = () -> {
      myErrorCount++;

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
          invokeLaterIfNeeded(
            () -> ApplicationManager.getApplication().runWriteAction(() -> setJdkAsProjectJdk(myRequest.getProject(), jdkHome)));
        }
      }
    };
    invokeLaterIfProjectAlive(myRequest.getProject(), showErrorTask);
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleBuildInvoker.class);
  }

  private void notifyGradleInvocationCompleted(@NotNull GradleBuildState buildState, long durationMillis) {
    Project project = myRequest.getProject();
    if (!project.isDisposed()) {
      String statusMsg = createStatusMessage(buildState, durationMillis);
      MessageType messageType = myErrorCount > 0 ? ERROR : INFO;
      if (durationMillis > ONE_MINUTE_MS) {
        BALLOON_NOTIFICATION.createNotification(statusMsg, messageType).notify(project);
      }
      else {
        addToEventLog(statusMsg, messageType);
      }
      getLogger().info(statusMsg);
    }
  }

  @NotNull
  private String createStatusMessage(@NotNull GradleBuildState buildState, long durationMillis) {
    String message = "Gradle build " + formatBuildStatusFromState(buildState);
    if (myErrorCount > 0) {
      message += String.format(Locale.US, " with %d error(s)", myErrorCount);
    }
    message = message + " in " + formatDuration(durationMillis);
    return message;
  }

  @NotNull
  private static String formatBuildStatusFromState(@NotNull GradleBuildState state) {
    BuildSummary summary = state.getSummary();
    if (summary != null) {
      switch (summary.getStatus()) {
        case SUCCESS:
          return "finished";
        case FAILED:
          return "failed";
        case CANCELED:
          return "cancelled";
      }
    }
    return "finished";
  }

  private void addToEventLog(@NotNull String message, @NotNull MessageType type) {
    LOGGING_NOTIFICATION.createNotification(message, type).notify(myProject);
  }

  private void attemptToStopBuild() {
    myBuildStopper.attemptToStopBuild(myRequest.getTaskId(), myProgressIndicator);
  }

  private class CloseListener implements ContentManagerListener, VetoableProjectManagerListener {
    private boolean myIsApplicationExitingOrProjectClosing;
    private boolean myUserAcceptedCancel;

    @Override
    public void projectOpened(@NotNull Project project) {
    }

    @Override
    public boolean canClose(@NotNull Project project) {
      if (!project.equals(myProject)) {
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
    public void projectClosing(@NotNull Project project) {
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
    }
  }
}
