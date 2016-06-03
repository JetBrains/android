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
package com.android.tools.idea.gradle.invoker;

import com.android.builder.model.AndroidProject;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.invoker.console.view.GradleConsoleToolWindowFactory;
import com.android.tools.idea.gradle.invoker.console.view.GradleConsoleView;
import com.android.tools.idea.gradle.invoker.messages.GradleBuildTreeViewPanel;
import com.android.tools.idea.gradle.output.parser.BuildOutputParser;
import com.android.tools.idea.gradle.service.notification.errors.AbstractSyncErrorHandler;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
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
import com.intellij.util.Consumer;
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
import javax.swing.event.HyperlinkEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleBuilds.CONFIGURE_ON_DEMAND_OPTION;
import static com.android.tools.idea.gradle.util.GradleBuilds.PARALLEL_BUILD_OPTION;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.android.tools.idea.startup.AndroidStudioInitializer.ENABLE_EXPERIMENTAL_PROFILING;
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
import static org.jetbrains.android.AndroidPlugin.*;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;

/**
 * Invokes Gradle tasks as a IDEA task in the background.
 */
public class GradleTasksExecutor extends Task.Backgroundable {
  private static final ExternalSystemTaskNotificationListener GRADLE_LISTENER = new ExternalSystemTaskNotificationListenerAdapter() {
  };

  private static final long ONE_MINUTE_MS = 60L /*sec*/ * 1000L /*millisec*/;
  private static final Logger LOG = Logger.getInstance(GradleInvoker.class);

  @NotNull public static final NotificationGroup LOGGING_NOTIFICATION = NotificationGroup.logOnlyGroup("Gradle Build (Logging)");
  @NotNull public static final NotificationGroup BALLOON_NOTIFICATION = NotificationGroup.balloonGroup("Gradle Build (Balloon)");

  // Dummy objects used for mapping {@link AbstractSyncErrorHandler} to the 'build messages' environment.
  private static final Notification DUMMY_NOTIFICATION = new Notification("dummy", "dummy", "dummy", NotificationType.ERROR);
  private static final Object DUMMY_EVENT_SOURCE = new Object();

  @NonNls private static final String CONTENT_NAME = "Gradle Build";
  @NonNls private static final String APP_ICON_ID = "compiler";

  private static final Key<Key<?>> CONTENT_ID_KEY = Key.create("CONTENT_ID");
  private static final int BUFFER_SIZE = 2048;

  private static final String GRADLE_RUNNING_MSG_TITLE = "Gradle Running";
  private static final String PASSWORD_KEY_SUFFIX = ".password=";

  public static final String ANDROID_ADDITIONAL_PLUGINS = "android.additional.plugins";
  public static final String COM_ANDROID_TOOLS_PROFILER = "com.android.tools.profiler";

  @NotNull private final Key<Key<?>> myContentId = Key.create("compile_content");

  @NotNull private final Object myMessageViewLock = new Object();
  @NotNull private final Object myCompletionLock = new Object();

  @GuardedBy("myCompletionLock")
  private int myCompletionCounter;

  @NotNull private final GradleTaskExecutionContext myContext;

  @GuardedBy("myMessageViewLock")
  @Nullable private GradleBuildTreeViewPanel myErrorTreeView;

  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  private volatile int myErrorCount;
  private volatile int myWarningCount;

  @NotNull private volatile ProgressIndicator myIndicator = new EmptyProgressIndicator();

  private volatile boolean myMessageViewIsPrepared;
  private volatile boolean myMessagesAutoActivated;

  private CloseListener myCloseListener;

  GradleTasksExecutor(@NotNull GradleTaskExecutionContext context) {
    super(context.getProject(), "Gradle Build Running", true);
    myContext = context;
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
    if (isAndroidStudio()) {
      // See https://code.google.com/p/android/issues/detail?id=169743
      clearStoredGradleJvmArgs(getNotNullProject());
    }

    myIndicator = indicator;

    ProjectManager projectManager = ProjectManager.getInstance();
    Project project = getNotNullProject();
    myCloseListener = new CloseListener();
    projectManager.addProjectManagerListener(project, myCloseListener);

    Semaphore semaphore = ((CompilerManagerImpl)CompilerManager.getInstance(project)).getCompilationSemaphore();
    boolean acquired = false;
    try {
      try {
        while (!acquired) {
          acquired = semaphore.tryAcquire(300, MILLISECONDS);
          if (indicator.isCanceled()) {
            // Give up obtaining the semaphore, let compile work begin in order to stop gracefully on cancel event.
            break;
          }
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      if (!isHeadless()) {
        addIndicatorDelegate();
      }
      invokeGradleTasks();
    }
    finally {
      try {
        indicator.stop();
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
    if (myIndicator instanceof ProgressIndicatorEx) {
      ProgressIndicatorEx indicator = (ProgressIndicatorEx)myIndicator;
      indicator.addStateDelegate(new ProgressIndicatorStateDelegate());
    }
  }

  private void closeView() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        synchronized (myMessageViewLock) {
          if (myErrorTreeView != null && !getNotNullProject().isDisposed()) {
            addStatisticsMessage(CompilerBundle.message("statistics.error.count", myErrorCount));
            addStatisticsMessage(CompilerBundle.message("statistics.warnings.count", myWarningCount));

            addMessage(new Message(Message.Kind.INFO, "See complete output in console", SourceFilePosition.UNKNOWN), new OpenGradleConsole());
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
    Project project = getNotNullProject();
    GradleExecutionSettings executionSettings = getGradleExecutionSettings(project);

    Function<ProjectConnection, Void> executeTasksFunction = connection -> {
      Stopwatch stopwatch = Stopwatch.createStarted();

      GradleConsoleView consoleView = GradleConsoleView.getInstance(project);
      consoleView.clear();

      addMessage(new Message(Message.Kind.INFO, "Gradle tasks " + myContext.getGradleTasks(), SourceFilePosition.UNKNOWN), null);

      String executingTasksText = "Executing tasks: " + myContext.getGradleTasks();
      consoleView.print(executingTasksText + SystemProperties.getLineSeparator() + SystemProperties.getLineSeparator(), NORMAL_OUTPUT);
      addToEventLog(executingTasksText, INFO);

      GradleOutputForwarder output = new GradleOutputForwarder(consoleView);

      BuildException buildError = null;
      ExternalSystemTaskId id = myContext.getTaskId();
      CancellationTokenSource cancellationTokenSource = GradleConnector.newCancellationTokenSource();
      try {
        AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
        List<String> commandLineArgs = Lists.newArrayList(buildConfiguration.getCommandLineOptions());

        if (buildConfiguration.USE_CONFIGURATION_ON_DEMAND && !commandLineArgs.contains(CONFIGURE_ON_DEMAND_OPTION)) {
          commandLineArgs.add(CONFIGURE_ON_DEMAND_OPTION);
        }

        if (!commandLineArgs.contains(PARALLEL_BUILD_OPTION) &&
            CompilerWorkspaceConfiguration.getInstance(project).PARALLEL_COMPILATION) {
          commandLineArgs.add(PARALLEL_BUILD_OPTION);
        }

        commandLineArgs.add(createProjectProperty(AndroidProject.PROPERTY_INVOKED_FROM_IDE, true));
        commandLineArgs.addAll(myContext.getCommandLineArgs());
        addLocalMavenRepoInitScriptCommandLineOption(commandLineArgs);
        attemptToUseEmbeddedGradle(project);

        if (System.getProperty(ENABLE_EXPERIMENTAL_PROFILING) != null) {
          addProfilerClassPathInitScriptCommandLineOption(commandLineArgs);
          commandLineArgs.add(createProjectProperty(ANDROID_ADDITIONAL_PLUGINS, COM_ANDROID_TOOLS_PROFILER));
        }

        // Don't include passwords in the log
        String logMessage = "Build command line options: " + commandLineArgs;
        if (logMessage.contains(PASSWORD_KEY_SUFFIX)) {
          List<String> replaced = Lists.newArrayListWithExpectedSize(commandLineArgs.size());
          for (String option : commandLineArgs) {
            // -Pandroid.injected.signing.store.password=, -Pandroid.injected.signing.key.password=
            int index = option.indexOf(".password=");
            if (index == -1) {
              replaced.add(option);
            } else {
              replaced.add(option.substring(0, index + PASSWORD_KEY_SUFFIX.length()) + "*********");
            }
          }
          logMessage = replaced.toString();
        }
        LOG.info(logMessage);

        List<String> jvmArgs = Lists.newArrayList(myContext.getJvmArgs());
        BuildLauncher launcher = connection.newBuild();
        prepare(launcher, id, executionSettings, GRADLE_LISTENER, jvmArgs, commandLineArgs, connection);

        File javaHome = IdeSdks.getJdkPath();
        if (javaHome != null) {
          launcher.setJavaHome(javaHome);
        }

        myContext.storeCancellationInfoFor(id, cancellationTokenSource);
        launcher.forTasks(toStringArray(myContext.getGradleTasks()));
        launcher.withCancellationToken(cancellationTokenSource.token());

        GradleOutputForwarder.Listener outputListener = null;
        if (myContext.getTaskNotificationListener() != null) {
          outputListener = (contentType, data, offset, length) -> {
            if (myContext.isActive(id)) {
              myContext.getTaskNotificationListener().onTaskOutput(id, new String(data, offset, length), contentType != ERROR_OUTPUT);
            }
          };
        }
        output.attachTo(launcher, outputListener);

        launcher.addProgressListener((ProgressListener)event -> {
          if (myContext.isActive(id)) {
            ExternalSystemTaskNotificationListener listener = myContext.getTaskNotificationListener();
            if (listener != null) {
              listener.onStatusChange(GradleProgressEventConverter.convert(id, event));
            }
          }
        });

        launcher.run();
      }
      catch (BuildException e) {
        buildError = e;
      }
      catch (Throwable e) {
        handleTaskExecutionError(e);
      }
      finally {
        myContext.dropCancellationInfoFor(id);
        String gradleOutput = output.toString();
        Application application = ApplicationManager.getApplication();
        if (isGuiTestingMode()) {
          String testOutput = application.getUserData(GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY);
          if (isNotEmpty(testOutput)) {
            gradleOutput = testOutput;
            application.putUserData(GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY, null);
          }
        }
        List<Message> buildMessages = Lists.newArrayList(showMessages(gradleOutput));
        if (myErrorCount == 0 && buildError != null && !hasCause(buildError, BuildCancelledException.class)) {
          // Gradle throws BuildCancelledException when we cancel task execution. We don't want to force showing 'Messages' tool
          // window for that situation though.
          showBuildException(buildError, output.getStdErr(), buildMessages);
        }
        output.close();

        stopwatch.stop();

        application.invokeLater(() -> notifyGradleInvocationCompleted(stopwatch.elapsed(MILLISECONDS)));

        if (buildError == null || !hasCause(buildError, BuildCancelledException.class)) {
          // Gradle throws BuildCancelledException when we cancel task execution. We don't want to force showing 'Messages' tool
          // window for that situation though.
          application.invokeLater(this::showMessages);
        }

        boolean buildSuccessful = buildError == null;
        GradleInvocationResult result = new GradleInvocationResult(myContext.getGradleTasks(), buildMessages, buildSuccessful);
        for (GradleInvoker.AfterGradleInvocationTask task : myContext.getGradleInvoker().getAfterInvocationTasks()) {
          task.execute(result);
        }
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

    File projectDirPath = getBaseDirPath(project);
    myHelper.execute(projectDirPath.getPath(), executionSettings, executeTasksFunction);
  }

  private void handleTaskExecutionError(@NotNull Throwable e) {
    if (myIndicator.isCanceled()) {
      LOG.info("Failed to complete Gradle execution. Project may be closing or already closed.", e);
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
      if (isAndroidStudio() && error.startsWith("Supplied javaHome is not a valid folder")) {
        File androidHome = IdeSdks.getAndroidSdkPath();
        String androidSdkPath = androidHome != null ? androidHome.getPath() : null;
        SelectSdkDialog selectSdkDialog = new SelectSdkDialog(null, androidSdkPath);
        selectSdkDialog.setModal(true);
        if (selectSdkDialog.showAndGet()) {
          String jdkHome = selectSdkDialog.getJdkHome();
          invokeLaterIfNeeded(() -> ApplicationManager.getApplication().runWriteAction(() -> {
            IdeSdks.setJdkPath(new File(jdkHome));
          }));
        }
      }
    };
    invokeLaterIfProjectAlive(getNotNullProject(), showErrorTask);
  }

  @NotNull
  private List<Message> showMessages(@NotNull String gradleOutput) {
    Iterable<PatternAwareOutputParser> parsers = JpsServiceManager.getInstance().getExtensions(PatternAwareOutputParser.class);
    List<Message> compilerMessages = new BuildOutputParser(parsers).parseGradleOutput(gradleOutput);
    for (Message msg : compilerMessages) {
      addMessage(msg, null);
    }
    return compilerMessages;
  }

  /**
   * Something went wrong while invoking Gradle but the output parsers did not create any build messages. We show the stack trace in the
   * "Messages" view.
   */
  private void showBuildException(@NotNull BuildException e, @NotNull String stdErr, @NotNull List<Message> buildMessages) {
    // There are no error messages to present. Show some feedback indicating that something went wrong.
    if (!stdErr.trim().isEmpty()) {
      // Show the contents of stderr as a compiler error.
      Message msg = new Message(Message.Kind.ERROR, stdErr, SourceFilePosition.UNKNOWN);
      buildMessages.add(msg);
      addMessage(msg, null);
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
        addMessage(msg, null);
      }
      finally {
        try {
          close(out, true /* swallowIOException */);
        } catch (IOException ex) {
          // Cannot happen
        }
      }
    }
  }

  private void addMessage(@NotNull Message message, @Nullable Navigatable navigatable) {
    prepareMessageView();
    switch (message.getKind()) {
      case WARNING:
        myWarningCount++;
        break;
      case ERROR:
        myErrorCount++;
      default:
        // do nothing.
    }
    Runnable addMessageTask = () -> {
      openMessageView();
      add(message, navigatable);
    };
    invokeLaterIfNeeded(addMessageTask);
  }

  private void prepareMessageView() {
    if (!myIndicator.isRunning() || myMessageViewIsPrepared) {
      return;
    }
    myMessageViewIsPrepared = true;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!getNotNullProject().isDisposed()) {
        synchronized (myMessageViewLock) {
          // Clear messages from the previous compilation
          if (myErrorTreeView == null) {
            // If message view != null, the contents has already been cleared.
            removeUnpinnedBuildMessages(getNotNullProject(), null);
          }
        }
      }
    });
  }

  static void clearMessageView(@NotNull Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!project.isDisposed()) {
        removeUnpinnedBuildMessages(project, null);
      }
    });
  }

  private static void removeUnpinnedBuildMessages(@NotNull Project project, @Nullable Content toKeep) {
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
      }
    }
  }

  private void openMessageView() {
    if (myIndicator.isCanceled()) {
      return;
    }

    Project project = getNotNullProject();
    JComponent component;
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        return;
      }
      //noinspection ConstantConditions
      myErrorTreeView = new GradleBuildTreeViewPanel(project);
      myErrorTreeView.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
        @Override
        public void stopProcess() {
          stopBuild();
        }

        @Override
        public boolean isProcessStopped() {
          return !myIndicator.isRunning();
        }
      });
      component = myErrorTreeView.getComponent();
    }

    Content content = ContentFactory.SERVICE.getInstance().createContent(component, CONTENT_NAME, true);
    content.putUserData(CONTENT_ID_KEY, myContentId);

    MessageView messageView = getMessageView();
    ContentManager contentManager = messageView.getContentManager();
    contentManager.addContent(content);

    myCloseListener.setContent(contentManager, content);

    removeUnpinnedBuildMessages(getNotNullProject(), content);
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
      if (myErrorTreeView != null && !getNotNullProject().isDisposed()) {
        Message.Kind messageKind = message.getKind();
        int type = translateMessageKind(messageKind);
        LinkAwareMessageData messageData = prepareMessage(message);
        if (navigatable == null) {
          VirtualFile file = findFileFrom(message);

          List<SourceFilePosition> sourceFilePositions = message.getSourceFilePositions();
          assert !sourceFilePositions.isEmpty();
          SourcePosition position = sourceFilePositions.get(0).getPosition();
          int line = position.getStartLine();
          int column = position.getStartColumn();

          myErrorTreeView.addMessage(type, messageData.textLines, file, line, column, messageData.hyperlinkListener);
        }
        else {
          myErrorTreeView.addMessage(type, messageData.textLines, null, navigatable, null, null, messageData.hyperlinkListener);
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
  private LinkAwareMessageData prepareMessage(@NotNull Message message) {
    List<String> rawTextLines;
    String text = message.getText();
    if (text.indexOf('\n') == -1) {
      rawTextLines = Collections.singletonList(text);
    }
    else {
      rawTextLines = Lists.newArrayList(on('\n').split(text));
    }
    if (message.getKind() != Message.Kind.ERROR) {
      //noinspection unchecked
      return new LinkAwareMessageData(toStringArray(rawTextLines), null);
    }

    // The general idea is to adapt existing gradle output enhancers (AbstractSyncErrorHandler) to the 'gradle build' process.
    // Their are built in assumption that they enhance external system's NotificationData by custom html hyperlinks markup and
    // corresponding listeners. So, what we do here is just providing fake NotificationData to the handlers and extract the
    // data added by them (if any).
    List<String> enhancedTextLines = null; // Text lines with added hyperlinks, i.e. hold text to actually show to end-user
    List<String> linesBuffer = Lists.newArrayListWithExpectedSize(1);
    linesBuffer.add("");
    NotificationData dummyData =
      new NotificationData("", message.getText(), NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    String previousMessage = dummyData.getMessage();
    for (AbstractSyncErrorHandler handler : AbstractSyncErrorHandler.EP_NAME.getExtensions()) {
      // We experienced that AbstractSyncErrorHandler often look to the first line only (because gradle output is sequential, line-by-line.
      // That's why we roll through all message lines and offer every of them to the handler.
      for (int i = 0; i < rawTextLines.size(); i++) {
        String line = rawTextLines.get(i);

        // This logic comes from gradle itself. Corresponding 'clearing' code remains at BuildFailureParser.parse()
        String prefixToStrip = "> ";
        line = trimStart(line, prefixToStrip);

        linesBuffer.set(0, line);
        boolean handled = handler.handleError(linesBuffer, new ExternalSystemException(message.getText()), dummyData, getNotNullProject());
        if (handled) {
          // Extract text added by the handler and store it at the 'enhancedTextLines' collection.
          String currentMessage = dummyData.getMessage();
          if (currentMessage.length() > previousMessage.length()) {
            int j = previousMessage.length();
            if (currentMessage.charAt(j) == '\n') {
              j++;
            }
            String addedText = currentMessage.substring(j);
            if (enhancedTextLines == null) {
              enhancedTextLines = Lists.newArrayList(rawTextLines);
            }
            enhancedTextLines.add(addedText);
            previousMessage = currentMessage;
          }
        }
      }
    }
    List<String> textLinesToUse;
    Consumer<String> hyperlinkListener;
    if (enhancedTextLines == null) {
      textLinesToUse = rawTextLines;
      hyperlinkListener = null;
    }
    else {
      textLinesToUse = enhancedTextLines;
      // AbstractSyncErrorHandler add hyperlinks (which we derived earlier and stored in 'enhancedTextLines') and hyperlink listeners
      // (facaded by the NotificationData.getListener()). So, what we do here is just delegating 'activate link' event
      // to those hyperlink listeners added by AbstractSyncErrorHandler.
      hyperlinkListener = url -> {
        HyperlinkEvent event = new HyperlinkEvent(DUMMY_EVENT_SOURCE, HyperlinkEvent.EventType.ACTIVATED, null, url);
        dummyData.getListener().hyperlinkUpdate(DUMMY_NOTIFICATION, event);
      };
    }
    return new LinkAwareMessageData(toStringArray(textLinesToUse), hyperlinkListener);
  }

  @Nullable
  private VirtualFile findFileFrom(@NotNull Message message) {
    SourceFile source = message.getSourceFilePositions().get(0).getFile();
    if (source.getSourceFile() != null) {
      return findFileByIoFile(source.getSourceFile(), true);
    }
    if (source.getDescription() != null) {
      String gradlePath = source.getDescription();
      Module module = findModuleByGradlePath(getNotNullProject(), gradlePath);
      if (module != null) {
        AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
        // if we got here facet is not null;
        assert facet != null;
        GradleModel gradleModel = facet.getGradleModel();
        return gradleModel != null ? gradleModel.getBuildFile() : null;
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
        LOG.info("Unknown message kind: " + kind);
        return 0;
    }
  }

  private void notifyGradleInvocationCompleted(long durationMillis) {
    Project project = getNotNullProject();
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

  @NotNull
  private ToolWindowManager getToolWindowManager() {
    return ToolWindowManager.getInstance(getNotNullProject());
  }

  private void showMessages() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null && !getNotNullProject().isDisposed()) {
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
    return MessageView.SERVICE.getInstance(getNotNullProject());
  }

  @NotNull
  private Project getNotNullProject() {
    assert myProject != null;
    return myProject;
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

  private void cancel() {
    if (!myIndicator.isCanceled()) {
      stopBuild();
      myIndicator.cancel();
    }
  }

  private void stopBuild() {
    ExternalSystemTaskId taskId = myContext.getTaskId();
    if (myIndicator.isRunning()) {
      myIndicator.setText("Stopping Gradle build...");
    }
    GradleInvoker.getInstance(getNotNullProject()).cancelTask(taskId);
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
        cancel();
        return true;
      }
      return !myIndicator.isRunning();
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
          Project project = getNotNullProject();
          if (myErrorTreeView != null && !project.isDisposed()) {
            Disposer.dispose(myErrorTreeView);
            myErrorTreeView = null;
            if (myIndicator.isRunning()) {
              cancel();
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
      if (event.getContent() == myContent && !myIndicator.isCanceled() && shouldPromptUser()) {
        myUserAcceptedCancel = askUserToCancelGradleExecution();
        if (!myUserAcceptedCancel) {
          event.consume(); // veto closing
        }
      }
    }

    private boolean shouldPromptUser() {
      return !myUserAcceptedCancel && !myIsApplicationExitingOrProjectClosing && myIndicator.isRunning();
    }

    private boolean askUserToCancelGradleExecution() {
      String msg = "Gradle is running. Proceed with Project closing?";
      int result = Messages.showYesNoDialog(myProject, msg, GRADLE_RUNNING_MSG_TITLE, Messages.getQuestionIcon());
      return result == Messages.YES;
    }
  }

  private class ProgressIndicatorStateDelegate extends AbstractProgressIndicatorExBase {
    @Override
    public void cancel() {
      super.cancel();
      closeView();
      stopAppIconProgress();
    }

    @Override
    public void stop() {
      super.stop();
      if (!isCanceled()) {
        closeView();
      }
      stopAppIconProgress();
    }

    private void stopAppIconProgress() {
      invokeLaterIfNeeded(() -> {
        AppIcon appIcon = AppIcon.getInstance();
        Project project = getNotNullProject();
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

  /**
   * 'Parameter object' pattern for preparing data to be stored at the 'build messages' output tree structure.
   */
  private static class LinkAwareMessageData {
    /** Target node text split by lines. */
    @NotNull final String[] textLines;
    /** A listener to use for the target text's hyperlinks (if any). Is expected to receives link's href value as an argument. */
    @Nullable final Consumer<String> hyperlinkListener;

    LinkAwareMessageData(@NotNull String[] textLines, @Nullable Consumer<String> hyperlinkListener) {
      this.textLines = textLines;
      this.hyperlinkListener = hyperlinkListener;
    }
  }
}
