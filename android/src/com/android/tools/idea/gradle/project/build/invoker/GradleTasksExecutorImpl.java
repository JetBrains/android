/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.builder.model.AndroidProject;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.fd.FlightRecorder;
import com.android.tools.idea.fd.InstantRunBuildProgressListener;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.gradle.output.parser.BuildOutputParser;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.console.view.GradleConsoleToolWindowFactory;
import com.android.tools.idea.gradle.project.build.console.view.GradleConsoleView;
import com.android.tools.idea.gradle.project.build.invoker.messages.GradleBuildTreeViewPanel;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.*;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.MessageCategory;
import net.jcip.annotations.GuardedBy;
import org.gradle.tooling.*;
import org.gradle.tooling.events.ProgressListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.execution.GradleProgressEventConverter;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.tools.idea.gradle.project.build.BuildStatus.*;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleBuilds.CONFIGURE_ON_DEMAND_OPTION;
import static com.android.tools.idea.gradle.util.GradleBuilds.PARALLEL_BUILD_OPTION;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.google.common.base.Splitter.on;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.io.Closeables.close;
import static com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT;
import static com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT;
import static com.intellij.openapi.application.ModalityState.NON_MODAL;
import static com.intellij.openapi.ui.MessageType.*;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;

class GradleTasksExecutorImpl extends GradleTasksExecutor {
  private static final ExternalSystemTaskNotificationListener GRADLE_LISTENER = new ExternalSystemTaskNotificationListenerAdapter() {};

  private static final long ONE_MINUTE_MS = 60L /*sec*/ * 1000L /*millisec*/;

  @NonNls private static final String CONTENT_NAME = "Gradle Build";
  @NonNls private static final String APP_ICON_ID = "compiler";

  private static final int BUFFER_SIZE = 2048;

  private static final String GRADLE_RUNNING_MSG_TITLE = "Gradle Running";
  private static final String PASSWORD_KEY_SUFFIX = ".password=";

  @NotNull private final Key<Key<?>> myContentId = Key.create("compile_content");

  @NotNull private final Object myMessageViewLock = new Object();
  @NotNull private final Object myCompletionLock = new Object();

  @NotNull private final GradleBuildInvoker.Request myRequest;
  @NotNull private final BuildStopper myBuildStopper;

  @GuardedBy("myCompletionLock")
  private int myCompletionCounter;

  @GuardedBy("myMessageViewLock")
  @Nullable
  private GradleBuildTreeViewPanel myErrorTreeView;

  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  private volatile int myErrorCount;
  private volatile int myWarningCount;

  @NotNull private volatile ProgressIndicator myProgressIndicator = new EmptyProgressIndicator();

  private volatile boolean myMessageViewIsPrepared;
  private volatile boolean myMessagesAutoActivated;

  private CloseListener myCloseListener;

  GradleTasksExecutorImpl(@NotNull GradleBuildInvoker.Request request, @NotNull BuildStopper buildStopper) {
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

    ProjectManager projectManager = ProjectManager.getInstance();
    Project project = myRequest.getProject();
    myCloseListener = new CloseListener();
    projectManager.addProjectManagerListener(project, myCloseListener);

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
        projectManager.removeProjectManagerListener(project, myCloseListener);
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

  private void closeView() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        synchronized (myMessageViewLock) {
          if (myErrorTreeView != null && !myRequest.getProject().isDisposed()) {
            addStatisticsMessage(CompilerBundle.message("statistics.error.count", myErrorCount));
            addStatisticsMessage(CompilerBundle.message("statistics.warnings.count", myWarningCount));

            addMessage(new Message(Message.Kind.INFO, "See complete output in console", SourceFilePosition.UNKNOWN),
                       new OpenGradleConsole());
            myErrorTreeView.selectFirstMessage();
          }
        }
      }

      private void addStatisticsMessage(@NotNull String text) {
        addMessage(new Message(Message.Kind.STATISTICS, text, SourceFilePosition.UNKNOWN), null);
      }
    }, NON_MODAL);
  }

  private void invokeGradleTasks() {
    Project project = myRequest.getProject();
    GradleExecutionSettings executionSettings = getOrCreateGradleExecutionSettings(project);

    AtomicReference<Object> model = new AtomicReference<>(null);

    Function<ProjectConnection, Void> executeTasksFunction = connection -> {
      Stopwatch stopwatch = Stopwatch.createStarted();

      GradleConsoleView consoleView = GradleConsoleView.getInstance(myProject);
      consoleView.clear();

      BuildAction<?> buildAction = myRequest.getBuildAction();
      boolean isRunBuildAction = buildAction != null;

      List<String> gradleTasks = myRequest.getGradleTasks();
      addMessage(new Message(Message.Kind.INFO, "Gradle tasks " + gradleTasks, SourceFilePosition.UNKNOWN), null);

      String executingTasksText = "Executing tasks: " + gradleTasks;
      consoleView.print(executingTasksText + SystemProperties.getLineSeparator() + SystemProperties.getLineSeparator(), NORMAL_OUTPUT);
      addToEventLog(executingTasksText, INFO);

      GradleOutputForwarder output = new GradleOutputForwarder(consoleView);

      Throwable buildError = null;
      InstantRunBuildProgressListener instantRunProgressListener = null;
      ExternalSystemTaskId id = myRequest.getTaskId();
      CancellationTokenSource cancellationTokenSource = myBuildStopper.createAndRegisterTokenSource(id);
      BuildMode buildMode = BuildSettings.getInstance(myProject).getBuildMode();

      GradleBuildState buildState = GradleBuildState.getInstance(myProject);
      buildState.buildStarted(new BuildContext(project, gradleTasks, buildMode));

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
        initScripts.addLocalMavenRepoInitScriptCommandLineArg(commandLineArguments);

        attemptToUseEmbeddedGradle(project);

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

        List<String> jvmArguments = new ArrayList<>(myRequest.getJvmArguments());

        LongRunningOperation operation = isRunBuildAction ? connection.action(buildAction) : connection.newBuild();

        prepare(operation, id, executionSettings, GRADLE_LISTENER, jvmArguments, commandLineArguments, connection);

        File javaHome = IdeSdks.getInstance().getJdkPath();
        if (javaHome != null) {
          operation.setJavaHome(javaHome);
        }

        if (isRunBuildAction) {
          ((BuildActionExecuter)operation).forTasks(toStringArray(gradleTasks));
        }
        else {
          ((BuildLauncher)operation).forTasks(toStringArray(gradleTasks));
        }

        operation.withCancellationToken(cancellationTokenSource.token());

        GradleOutputForwarder.Listener outputListener = null;
        ExternalSystemTaskNotificationListener taskListener = myRequest.getTaskListener();
        if (taskListener != null) {
          outputListener = (contentType, data, offset, length) -> {
            if (myBuildStopper.contains(id)) {
              taskListener.onTaskOutput(id, new String(data, offset, length), contentType != ERROR_OUTPUT);
            }
          };
        }
        output.attachTo(operation, outputListener);

        if (taskListener != null) {
          operation.addProgressListener((ProgressListener)event -> {
            if (myBuildStopper.contains(id)) {
              taskListener.onStatusChange(GradleProgressEventConverter.convert(id, event));
            }
          });
        }

        if (InstantRunSettings.isInstantRunEnabled() && InstantRunSettings.isRecorderEnabled()) {
          instantRunProgressListener = new InstantRunBuildProgressListener();
          operation.addProgressListener(instantRunProgressListener);
        }

        if (isRunBuildAction) {
          model.set(((BuildActionExecuter)operation).run());
        }
        else {
          ((BuildLauncher)operation).run();
        }

        buildState.buildFinished(SUCCESS);
      }
      catch (BuildException e) {
        buildError = e;
      }
      catch (Throwable e) {
        buildError = e;
        handleTaskExecutionError(e);
      }
      finally {
        if (buildError != null) {
          if (wasBuildCanceled(buildError)) {
            buildState.buildFinished(CANCELED);
          }
          else {
            buildState.buildFinished(FAILED);
          }
        }

        myBuildStopper.remove(id);
        String gradleOutput = output.toString();
        if (instantRunProgressListener != null) {
          FlightRecorder.get(myProject).saveBuildOutput(gradleOutput, instantRunProgressListener);
        }
        Application application = ApplicationManager.getApplication();
        if (GuiTestingService.getInstance().isGuiTestingMode()) {
          String testOutput = application.getUserData(GuiTestingService.GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY);
          if (isNotEmpty(testOutput)) {
            gradleOutput = testOutput;
            application.putUserData(GuiTestingService.GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY, null);
          }
        }
        showGradleOutput(gradleOutput, output, stopwatch, buildError, model.get());
      }
      return null;
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

    File buildFilePath = myRequest.getBuildFilePath();
    File projectDirPath = buildFilePath != null ? buildFilePath : getBaseDirPath(project);

    myHelper.execute(projectDirPath.getPath(), executionSettings, executeTasksFunction);
  }

  private void showGradleOutput(@NotNull String gradleOutput,
                                @NotNull GradleOutputForwarder output,
                                @NotNull Stopwatch stopwatch,
                                @Nullable Throwable buildError,
                                @Nullable Object model) {
    Application application = ApplicationManager.getApplication();

    List<Message> buildMessages = new ArrayList<>();
    collectMessages(gradleOutput, buildMessages).doWhenDone(() -> {
      boolean hasError = false;
      for (Message message : buildMessages) {
        if (message.getKind() == Message.Kind.ERROR) {
          hasError = true;
          break;
        }
      }

      if (!hasError && myErrorCount == 0 && buildError != null && buildError instanceof BuildException) {
        addBuildExceptionAsMessage((BuildException)buildError, output.getStdErr(), buildMessages);
      }
      output.close();
      stopwatch.stop();

      add(buildMessages);

      if (!myProgressIndicator.isCanceled()) {
        closeView();
      }

      application.invokeLater(() -> notifyGradleInvocationCompleted(stopwatch.elapsed(MILLISECONDS)));

      if (buildError == null || !wasBuildCanceled(buildError)) {
        // Gradle throws BuildCancelledException when we cancel task execution. We don't want to force showing 'Messages' tool
        // window for that situation though.
        application.invokeLater(this::showMessages);
      }

      if (getProject().isDisposed()) {
        return;
      }

      GradleInvocationResult result = new GradleInvocationResult(myRequest.getGradleTasks(), buildMessages, buildError, model);
      for (GradleBuildInvoker.AfterGradleInvocationTask task : GradleBuildInvoker.getInstance(getProject()).getAfterInvocationTasks()) {
        task.execute(result);
      }
    });
  }

  private static boolean wasBuildCanceled(@NotNull Throwable buildError) {
    return hasCause(buildError, BuildCancelledException.class);
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
    prepareMessageView();
    Runnable addMessageTask = () -> {
      openMessageView();
      for (Message message : buildMessages) {
        incrementErrorOrWarningCount(message);
        if (shouldShow(message)) {
          add(message, null);
        }
      }
    };
    TransactionGuard.submitTransaction(myProject, addMessageTask);
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
      addMessage(new Message(Message.Kind.ERROR, msg, SourceFilePosition.UNKNOWN), null);
      showMessages();

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

  /**
   * Something went wrong while invoking Gradle but the output parsers did not create any build messages. We show the stack trace in the
   * "Messages" view.
   */
  private static void addBuildExceptionAsMessage(@NotNull BuildException e,
                                                 @NotNull String stdErr,
                                                 @NotNull List<Message> buildMessages) {
    // There are no error messages to present. Show some feedback indicating that something went wrong.
    if (!stdErr.trim().isEmpty()) {
      // Show the contents of stderr as a compiler error.
      Message msg = new Message(Message.Kind.ERROR, stdErr, SourceFilePosition.UNKNOWN);
      buildMessages.add(msg);
    }
    else {
      // Since we have nothing else to show, just print the stack trace of the caught exception.
      ByteArrayOutputStream out = new ByteArrayOutputStream(BUFFER_SIZE);
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        e.printStackTrace(new PrintStream(out));
        String message = "Internal error:" + SystemProperties.getLineSeparator() + out.toString();
        Message msg = new Message(Message.Kind.ERROR, message, SourceFilePosition.UNKNOWN);
        buildMessages.add(msg);
      }
      finally {
        try {
          close(out, true /* swallowIOException */);
        }
        catch (IOException ex) {
          // Cannot happen
        }
      }
    }
  }

  private void addMessage(@NotNull Message message, @Nullable Navigatable navigatable) {
    prepareMessageView();
    incrementErrorOrWarningCount(message);
    if (shouldShow(message)) {
      Runnable addMessageTask = () -> {
        openMessageView();
        add(message, navigatable);
      };
      invokeLaterIfNeeded(addMessageTask);
    }
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

  private static boolean shouldShow(@NotNull Message message) {
    Message.Kind kind = message.getKind();
    return kind != Message.Kind.SIMPLE && kind != Message.Kind.UNKNOWN;
  }

  private void prepareMessageView() {
    if (!myProgressIndicator.isRunning() || myMessageViewIsPrepared) {
      return;
    }
    myMessageViewIsPrepared = true;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!myRequest.getProject().isDisposed()) {
        synchronized (myMessageViewLock) {
          // Clear messages from the previous compilation
          if (myErrorTreeView == null) {
            // If message view != null, the contents has already been cleared.
            removeUnpinnedBuildMessages(myRequest.getProject(), null);
          }
        }
      }
    });
  }

  private void openMessageView() {
    if (myProgressIndicator.isCanceled()) {
      return;
    }

    Project project = myRequest.getProject();
    JComponent component;
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        return;
      }
      myErrorTreeView = new GradleBuildTreeViewPanel(project);
      ExternalSystemTaskId id = myRequest.getTaskId();
      myErrorTreeView.setProcessController(new BuildProcessController(id, myBuildStopper, myProgressIndicator));
      component = myErrorTreeView.getComponent();
    }

    Content content = ContentFactory.SERVICE.getInstance().createContent(component, CONTENT_NAME, true);
    content.putUserData(CONTENT_ID_KEY, myContentId);

    MessageView messageView = getMessageView();
    ContentManager contentManager = messageView.getContentManager();
    contentManager.addContent(content);

    myCloseListener.setContent(contentManager, content);

    removeUnpinnedBuildMessages(myRequest.getProject(), content);
    contentManager.setSelectedContent(content);
  }

  private void activateGradleConsole() {
    ToolWindow window = getToolWindowManager().getToolWindow(GradleConsoleToolWindowFactory.ID);
    if (window != null) {
      window.activate(null, false);
    }
  }

  private void add(@NotNull Message message, @Nullable Navigatable navigatable) {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null && !myRequest.getProject().isDisposed()) {
        Message.Kind messageKind = message.getKind();
        int type = translateMessageKind(messageKind);
        String[] textLines = splitIntoLines(message);
        if (navigatable == null) {
          VirtualFile file = findFileFrom(message);

          List<SourceFilePosition> sourceFilePositions = message.getSourceFilePositions();
          assert !sourceFilePositions.isEmpty();
          SourcePosition position = sourceFilePositions.get(0).getPosition();
          int line = position.getStartLine();
          int column = position.getStartColumn();

          myErrorTreeView.addMessage(type, textLines, file, line, column, null);
        }
        else {
          myErrorTreeView.addMessage(type, textLines, null, navigatable, null, null, null);
        }

        boolean autoActivate = !myMessagesAutoActivated && type == MessageCategory.ERROR;
        if (autoActivate) {
          myMessagesAutoActivated = true;
          activateMessageView();
        }
      }
    }
  }

  @NotNull
  private static String[] splitIntoLines(@NotNull Message message) {
    String text = message.getText();
    if (text.indexOf('\n') == -1) {
      return new String[]{text};
    }
    return toStringArray(on('\n').splitToList(text));
  }

  @Nullable
  private VirtualFile findFileFrom(@NotNull Message message) {
    SourceFile source = message.getSourceFilePositions().get(0).getFile();
    if (source.getSourceFile() != null) {
      return findFileByIoFile(source.getSourceFile(), true);
    }
    if (source.getDescription() != null) {
      String gradlePath = source.getDescription();
      Module module = findModuleByGradlePath(myRequest.getProject(), gradlePath);
      if (module != null) {
        GradleFacet facet = GradleFacet.getInstance(module);
        // if we got here facet is not null;
        assert facet != null;
        GradleModuleModel gradleModuleModel = facet.getGradleModuleModel();
        return gradleModuleModel != null ? gradleModuleModel.getBuildFile() : null;
      }
    }
    return null;
  }

  private static int translateMessageKind(@NotNull Message.Kind kind) {
    switch (kind) {
      case INFO:
        return MessageCategory.INFORMATION;
      case WARNING:
        return MessageCategory.WARNING;
      case ERROR:
        return MessageCategory.ERROR;
      case STATISTICS:
        return MessageCategory.STATISTICS;
      case SIMPLE:
        return MessageCategory.SIMPLE;
      default:
        getLogger().warn("Unknown message kind: " + kind);
        return 0;
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
      getLogger().info(statusMsg);
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
      message += String.format(" with %d warning(s)", myWarningCount);
    }
    message = message + " in " + formatDuration(durationMillis);
    return message;
  }

  private void addToEventLog(@NotNull String message, @NotNull MessageType type) {
    LOGGING_NOTIFICATION.createNotification(message, type).notify(myProject);
  }

  @NotNull
  private ToolWindowManager getToolWindowManager() {
    return ToolWindowManager.getInstance(myRequest.getProject());
  }

  private void showMessages() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null && !myRequest.getProject().isDisposed()) {
        MessageView messageView = getMessageView();
        Content[] contents = messageView.getContentManager().getContents();
        for (Content content : contents) {
          if (content.getUserData(CONTENT_ID_KEY) != null) {
            messageView.getContentManager().setSelectedContent(content);
            return;
          }
        }
      }
    }
  }

  @NotNull
  private MessageView getMessageView() {
    return MessageView.SERVICE.getInstance(myRequest.getProject());
  }

  private void activateMessageView() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        ToolWindow window = getToolWindowManager().getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (window != null) {
          window.activate(null, false);
        }
      }
    }
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

  private class CloseListener extends ContentManagerAdapter implements ProjectManagerListener {
    private ContentManager myContentManager;
    @Nullable private Content myContent;

    private boolean myIsApplicationExitingOrProjectClosing;
    private boolean myUserAcceptedCancel;

    @Override
    public void projectOpened(Project project) {
    }

    @Override
    public boolean canCloseProject(Project project) {
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
    public void projectClosed(Project project) {
      if (project.equals(myProject) && myContent != null) {
        myContentManager.removeContent(myContent, true);
      }
    }

    @Override
    public void projectClosing(Project project) {
      if (project.equals(myProject)) {
        myIsApplicationExitingOrProjectClosing = true;
      }
    }

    void setContent(@NotNull ContentManager contentManager, @Nullable Content content) {
      myContent = content;
      myContentManager = contentManager;
      contentManager.addContentManagerListener(this);
    }

    @Override
    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        synchronized (myMessageViewLock) {
          Project project = myRequest.getProject();
          if (myErrorTreeView != null && !project.isDisposed()) {
            Disposer.dispose(myErrorTreeView);
            myErrorTreeView = null;
            if (myProgressIndicator.isRunning()) {
              attemptToStopBuild();
            }
            AppIcon appIcon = AppIcon.getInstance();
            if (appIcon.hideProgress(project, APP_ICON_ID)) {
              //noinspection ConstantConditions
              appIcon.setErrorBadge(project, null);
            }
          }
        }
        myContentManager.removeContentManagerListener(this);
        if (myContent != null) {
          myContent.release();
        }
        myContent = null;
      }
    }

    @Override
    public void contentRemoveQuery(ContentManagerEvent event) {
      if (event.getContent() == myContent && !myProgressIndicator.isCanceled() && shouldPromptUser()) {
        myUserAcceptedCancel = askUserToCancelGradleExecution();
        if (!myUserAcceptedCancel) {
          event.consume(); // veto closing
        }
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
      closeView();
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

    @Override
    protected void onProgressChange() {
      prepareMessageView();
    }
  }

  private class OpenGradleConsole implements Navigatable {
    @Override
    public void navigate(boolean requestFocus) {
      activateGradleConsole();
    }

    @Override
    public boolean canNavigate() {
      return true;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }
  }
}
