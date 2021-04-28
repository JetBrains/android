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

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.BUNDLE;
import static com.android.tools.idea.gradle.util.BuildMode.CLEAN;
import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.gradle.util.GradleBuilds.CLEAN_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.convert;
import static com.intellij.openapi.ui.Messages.CANCEL;
import static com.intellij.openapi.ui.Messages.NO;
import static com.intellij.openapi.ui.Messages.YES;
import static com.intellij.openapi.ui.Messages.YesNoCancelResult;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.filters.AndroidReRunBuildFilter;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionOutputLinkFilter;
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionUtil;
import com.android.tools.idea.gradle.project.build.output.BuildOutputParserManager;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.tracer.Trace;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.build.BuildConsoleUtils;
import com.intellij.build.BuildEventDispatcher;
import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.SkippedResultImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemEventDispatcher;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Ref;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.xdebugger.XDebugSession;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import org.gradle.tooling.BuildAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;


/**
 * Invokes Gradle tasks directly. Results of tasks execution are displayed in both the "Messages" tool window and the new "Gradle Console"
 * tool window.
 */
public class GradleBuildInvoker {
  @NotNull private final Project myProject;
  @NotNull private final FileDocumentManager myDocumentManager;
  @NotNull private final GradleTasksExecutorFactory myTaskExecutorFactory;

  @NotNull private final Set<AfterGradleInvocationTask> myAfterTasks = new LinkedHashSet<>();
  @NotNull private final List<String> myOneTimeGradleOptions = new ArrayList<>();
  @NotNull private final Multimap<String, String> myLastBuildTasks = ArrayListMultimap.create();
  @NotNull private final BuildStopper myBuildStopper = new BuildStopper();
  @NotNull private final NativeDebugSessionFinder myNativeDebugSessionFinder;

  @NotNull
  public static GradleBuildInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleBuildInvoker.class);
  }

  public GradleBuildInvoker(@NotNull Project project) {
    this(project, FileDocumentManager.getInstance());
  }

  @NonInjectable
  @VisibleForTesting
  public GradleBuildInvoker(@NotNull Project project, @NotNull FileDocumentManager documentManager) {
    this(project, documentManager, new GradleTasksExecutorFactory(), new NativeDebugSessionFinder(project));
  }

  @NonInjectable
  @VisibleForTesting
  protected GradleBuildInvoker(@NotNull Project project,
                               @NotNull FileDocumentManager documentManager,
                               @NotNull GradleTasksExecutorFactory tasksExecutorFactory,
                               @NotNull NativeDebugSessionFinder nativeDebugSessionFinder) {
    myProject = project;
    myDocumentManager = documentManager;
    myTaskExecutorFactory = tasksExecutorFactory;
    myNativeDebugSessionFinder = nativeDebugSessionFinder;
  }

  public void cleanProject() {
    if (stopNativeDebugSessionOrStopBuild()) {
      return;
    }
    setProjectBuildMode(CLEAN);
    // Collect the root project path for all modules, there is one root project path per included project.
    GradleRootPathFinder pathFinder = new GradleRootPathFinder();
    Set<File> projectRootPaths = Arrays.stream(ModuleManager.getInstance(getProject()).getModules())
      .map(module -> pathFinder.getProjectRootPath(module).toFile())
      .collect(Collectors.toSet());
    for (File projectRootPath : projectRootPaths) {
      executeTasks(projectRootPath, Collections.singletonList(CLEAN_TASK_NAME));
    }
  }

  @TestOnly
  public void generateSources() {
    generateSources(false /* do not clean project */, ModuleManager.getInstance(myProject).getModules());
  }

  public void generateSourcesForModules(@NotNull Module[] modules) {
    generateSources(false, modules);
  }

  private void generateSources(boolean cleanProject, @NotNull Module[] modules) {
    BuildMode buildMode = SOURCE_GEN;
    setProjectBuildMode(buildMode);

    GradleTaskFinder gradleTaskFinder = GradleTaskFinder.getInstance();
    ListMultimap<Path, String> tasks = gradleTaskFinder.findTasksToExecute(modules, buildMode, TestCompileType.NONE);
    if (cleanProject) {
      if (stopNativeDebugSessionOrStopBuild()) {
        return;
      }
      tasks.keys().elementSet().forEach(key -> tasks.get(key).add(0, CLEAN_TASK_NAME));
    }
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath), Collections.singletonList(createGenerateSourcesOnlyProperty()));
    }
  }

  /**
   * @return {@code true} if the user selects to stop the current build.
   */
  private boolean stopNativeDebugSessionOrStopBuild() {
    XDebugSession nativeDebugSession = myNativeDebugSessionFinder.findNativeDebugSession();
    if (nativeDebugSession != null) {
      Ref<Integer> yesNoCancelRef = new Ref<>();
      Application application = ApplicationManager.getApplication();
      application.invokeAndWait(() -> yesNoCancelRef.set(promptUserToStopNativeDebugSession()), ModalityState.NON_MODAL);
      @YesNoCancelResult int yesNoCancel = yesNoCancelRef.get();
      switch (yesNoCancel) {
        case YES:
          // User selected "Terminate".
          nativeDebugSession.stop();
          break;
        case CANCEL:
          // User selected "Cancel". Do not continue with build.
          return true;
      }
    }
    return false;
  }

  @YesNoCancelResult
  private int promptUserToStopNativeDebugSession() {
    // If we have a native debugging process running, we need to kill it to release the files from being held open by the OS.
    String propKey = "gradle.project.build.invoker.clean-terminates-debugger";
    // We set the property globally, rather than on a per-project basis since the debugger either keeps files open on the OS or not.
    // If the user sets the property, it is stored in <config-dir>/config/options/options.xml
    String value = PropertiesComponent.getInstance().getValue(propKey);

    if (value == null) {
      Ref<Integer> yesNoCancelRef = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        String message = "Cleaning or rebuilding your project while debugging can lead to unexpected " +
                         "behavior.\n" +
                         "You can choose to either terminate the debugger before cleaning your project " +
                         "or keep debugging while cleaning.\n" +
                         "Clicking \"Cancel\" stops Gradle from cleaning or rebuilding your project, " +
                         "and preserves your debug process.";
        MessageDialogBuilder.YesNoCancel dialogBuilder = MessageDialogBuilder.yesNoCancel("Terminate debugging", message);
        int answer = dialogBuilder.project(myProject).yesText("Terminate").noText("Do not terminate").cancelText("Cancel").doNotAsk(
          new DialogWrapper.DoNotAskOption.Adapter() {
            @Override
            public void rememberChoice(boolean isSelected, int exitCode) {
              if (isSelected) {
                PropertiesComponent.getInstance().setValue(propKey, Boolean.toString(exitCode == YES));
              }
            }
          })
          .show();
        yesNoCancelRef.set(answer);
      }, ModalityState.NON_MODAL);
      @YesNoCancelResult int answer = yesNoCancelRef.get();
      return answer;
    }
    getLogger().debug(propKey + ": " + value);
    return value.equals("true") ? YES : NO;
  }

  @NotNull
  private static String createGenerateSourcesOnlyProperty() {
    return createProjectProperty(AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY, true);
  }

  /**
   * Execute Gradle tasks that compile the relevant Java sources.
   *
   * @param modules         Modules that need to be compiled
   * @param testCompileType Kind of tests that the caller is interested in. Use {@link TestCompileType#ALL} if compiling just the
   *                        main sources, {@link TestCompileType#UNIT_TESTS} if class files for running unit tests are needed.
   */
  public void compileJava(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    BuildMode buildMode = COMPILE_JAVA;
    setProjectBuildMode(buildMode);
    ListMultimap<Path, String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, testCompileType);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath));
    }
  }

  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    assemble(modules, testCompileType, null);
  }

  public void assemble(@NotNull Module[] modules,
                       @NotNull TestCompileType testCompileType,
                       @Nullable BuildAction<?> buildAction) {
    BuildMode buildMode = ASSEMBLE;
    setProjectBuildMode(buildMode);
    ListMultimap<Path, String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, testCompileType);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath), Collections.emptyList(), buildAction);
    }
  }

  public void bundle(@NotNull Module[] modules,
                     @Nullable BuildAction<?> buildAction) {
    BuildMode buildMode = BUNDLE;
    setProjectBuildMode(buildMode);
    ListMultimap<Path, String> tasks =
      GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, TestCompileType.NONE);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath), Collections.emptyList(), buildAction);
    }
  }

  public void rebuild() {
    BuildMode buildMode = REBUILD;
    setProjectBuildMode(buildMode);
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    ListMultimap<Path, String> tasks =
      GradleTaskFinder.getInstance().findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.NONE);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath));
    }
  }

  /**
   * Execute the last run set of Gradle tasks, with the specified gradle options prepended before the tasks to run.
   */
  public void rebuildWithTempOptions(@NotNull File buildFilePath, @NotNull List<String> options) {
    myOneTimeGradleOptions.addAll(options);
    try {
      Collection<String> tasks = myLastBuildTasks.get(buildFilePath.getPath());
      if (tasks.isEmpty()) {
        // For some reason the IDE lost the Gradle tasks executed during the last build.
        rebuild();
      }
      else {
        // The use case for this is the following:
        // 1. the build fails, and the console has the message "Run with --stacktrace", which now is a hyperlink
        // 2. the user clicks the hyperlink
        // 3. the IDE re-runs the build, with the Gradle tasks that were executed when the build failed, and it adds "--stacktrace"
        //    to the command line arguments.
        List<String> tasksFromLastBuild = new ArrayList<>(tasks);
        executeTasks(buildFilePath, tasksFromLastBuild);
      }
    }
    finally {
      // Don't reuse them on the next rebuild.
      myOneTimeGradleOptions.clear();
    }
  }

  private void setProjectBuildMode(@NotNull BuildMode buildMode) {
    BuildSettings.getInstance(myProject).setBuildMode(buildMode);
  }

  /**
   * @deprecated use {@link GradleBuildInvoker#executeTasks(File, List)}
   */
  @Deprecated
  public void executeTasks(@NotNull List<String> gradleTasks) {
    File path = getBaseDirPath(myProject);
    executeTasks(path, gradleTasks, myOneTimeGradleOptions);
  }

  private void executeTasks(@NotNull File buildFilePath, @NotNull List<String> gradleTasks) {
    executeTasks(buildFilePath, gradleTasks, myOneTimeGradleOptions);
  }

  public void executeTasks(@NotNull ListMultimap<Path, String> tasks,
                           @Nullable BuildMode buildMode,
                           @NotNull List<String> commandLineArguments,
                           @Nullable BuildAction buildAction) {
    if (buildMode != null) {
      setProjectBuildMode(buildMode);
    }
    tasks.keys().elementSet().forEach(path -> executeTasks(path.toFile(), tasks.get(path), commandLineArguments, buildAction));
  }

  @VisibleForTesting
  public void executeTasks(@NotNull File buildFilePath, @NotNull List<String> gradleTasks, @NotNull List<String> commandLineArguments) {
    executeTasks(buildFilePath, gradleTasks, commandLineArguments, null);
  }

  public void executeTasks(@NotNull File buildFilePath,
                           @NotNull List<String> gradleTasks,
                           @NotNull List<String> commandLineArguments,
                           @Nullable BuildAction buildAction) {
    List<String> jvmArguments = new ArrayList<>();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Projects in tests may not have a local.properties, set ANDROID_SDK_ROOT JVM argument if that's the case.
      LocalProperties localProperties;
      try {
        localProperties = new LocalProperties(myProject);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (localProperties.getAndroidSdkPath() == null) {
        File androidHomePath = IdeSdks.getInstance().getAndroidSdkPath();
        // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
        if (androidHomePath != null) {
          jvmArguments.add(AndroidGradleSettings.createAndroidHomeJvmArg(androidHomePath.getPath()));
        }
      }
    }

    // For development we might want to forward an agent to the daemon.
    // This is a no-op in production builds.
    Trace.addVmArgs(jvmArguments);
    Request request = new Request(myProject, buildFilePath, gradleTasks);
    ExternalSystemTaskNotificationListener buildTaskListener = createBuildTaskListener(request, "Build");
    // @formatter:off
    request.setJvmArguments(jvmArguments)
           .setCommandLineArguments(commandLineArguments)
           .setBuildAction(buildAction)
           .setTaskListener(buildTaskListener);
    // @formatter:on
    executeTasks(request);
  }

  @NotNull
  public ExternalSystemTaskNotificationListener createBuildTaskListener(@NotNull Request request, String executionName) {
    BuildViewManager buildViewManager = ServiceManager.getService(myProject, BuildViewManager.class);
    // This is resource is closed when onEnd is called or an exception is generated in this function bSee b/70299236.
    // We need to keep this resource open since closing it causes BuildOutputInstantReaderImpl.myThread to stop, preventing parsers to run.
    //noinspection resource, IOResourceOpenedButNotSafelyClosed
    BuildEventDispatcher eventDispatcher = new ExternalSystemEventDispatcher(request.myTaskId, buildViewManager);
    try {
      return new ExternalSystemTaskNotificationListenerAdapter() {
        @NotNull private BuildEventDispatcher myBuildEventDispatcher = eventDispatcher;
        private boolean myBuildFailed = false;

        @Override
        public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
          AnAction restartAction = new AnAction() {
            @Override
            public void update(@NotNull AnActionEvent e) {
              e.getPresentation().setEnabled(!myBuildStopper.contains(id));
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              myBuildFailed = false;
              // Recreate the reader since the one created with the listener can be already closed (see b/73102585)
              myBuildEventDispatcher.close();
              myBuildEventDispatcher = new ExternalSystemEventDispatcher(request.myTaskId, buildViewManager);
              executeTasks(request);
            }
          };

          myBuildFailed = false;
          Presentation presentation = restartAction.getTemplatePresentation();
          presentation.setText("Restart");
          presentation.setDescription("Restart");
          presentation.setIcon(AllIcons.Actions.Compile);

          long eventTime = System.currentTimeMillis();
          DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(id, executionName, workingDir, eventTime);
          if (request.myDoNotShowBuildOutputOnFailure) {
            buildDescriptor.setActivateToolWindowWhenFailed(false);
          }
          StartBuildEventImpl event = new StartBuildEventImpl(buildDescriptor, "running...");
          event.withRestartAction(restartAction).withExecutionFilter(new AndroidReRunBuildFilter(workingDir));
          if (BuildAttributionUtil.isBuildAttributionEnabledForProject(myProject)) {
            event.withExecutionFilter(new BuildAttributionOutputLinkFilter());
          }
          myBuildEventDispatcher.onEvent(id, event);
        }

        @Override
        public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
          if (event instanceof ExternalSystemBuildEvent) {
            BuildEvent buildEvent = ((ExternalSystemBuildEvent)event).getBuildEvent();
            myBuildEventDispatcher.onEvent(event.getId(), buildEvent);
          }
          else if (event instanceof ExternalSystemTaskExecutionEvent) {
            BuildEvent buildEvent = convert(((ExternalSystemTaskExecutionEvent)event));
            myBuildEventDispatcher.onEvent(event.getId(), buildEvent);
          }
        }

        @Override
        public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
          myBuildEventDispatcher.setStdOut(stdOut);
          myBuildEventDispatcher.append(text);
        }

        @Override
        public void onEnd(@NotNull ExternalSystemTaskId id) {
          CountDownLatch eventDispatcherFinished = new CountDownLatch(1);
          myBuildEventDispatcher.invokeOnCompletion((t) -> {
            if (myBuildFailed) {
              ServiceManager.getService(myProject, BuildOutputParserManager.class).sendBuildFailureMetrics();
            }
            eventDispatcherFinished.countDown();
          });
          myBuildEventDispatcher.close();

          // The underlying output parsers are closed asynchronously. Wait for completion in tests.
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            try {
              eventDispatcherFinished.await(10, SECONDS);
            }
            catch (InterruptedException ex) {
              throw new RuntimeException("Timeout waiting for event dispatcher to finish.", ex);
            }
          }
        }

        @Override
        public void onSuccess(@NotNull ExternalSystemTaskId id) {
          addBuildAttributionLinkToTheOutput(id);
          FinishBuildEventImpl event = new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "finished",
                                                                new SuccessResultImpl());
          myBuildEventDispatcher.onEvent(id, event);
        }

        private void addBuildAttributionLinkToTheOutput(@NotNull ExternalSystemTaskId id) {
          if (BuildAttributionUtil.isBuildAttributionEnabledForProject(myProject)) {
            String buildAttributionTabLinkLine = BuildAttributionUtil.buildOutputLine();
            onTaskOutput(id, "\n" + buildAttributionTabLinkLine, true);
          }
        }

        @Override
        public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
          myBuildFailed = true;
          String title = executionName + " failed";
          DataProvider dataProvider = BuildConsoleUtils.getDataProvider(id, buildViewManager);
          FailureResult failureResult = ExternalSystemUtil.createFailureResult(title, e, GRADLE_SYSTEM_ID, myProject, dataProvider);
          myBuildEventDispatcher.onEvent(id, new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "failed", failureResult));
        }

        @Override
        public void onCancel(@NotNull ExternalSystemTaskId id) {
          super.onCancel(id);
          // Cause build view to show as skipped all pending tasks (b/73397414)
          FinishBuildEventImpl event = new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "cancelled", new SkippedResultImpl());
          myBuildEventDispatcher.onEvent(id, event);
          myBuildEventDispatcher.close();
        }
      };
    }
    catch (Exception exception) {
      eventDispatcher.close();
      throw exception;
    }
  }

  public void executeTasks(@NotNull Request request) {
    String buildFilePath = request.getBuildFilePath().getPath();
    // Remember the current build's tasks, in case they want to re-run it with transient gradle options.
    myLastBuildTasks.removeAll(buildFilePath);
    List<String> gradleTasks = request.getGradleTasks();
    myLastBuildTasks.putAll(buildFilePath, gradleTasks);

    getLogger().info("About to execute Gradle tasks: " + gradleTasks);
    if (gradleTasks.isEmpty()) {
      return;
    }
    GradleTasksExecutor executor = myTaskExecutorFactory.create(request, myBuildStopper);

    if (ApplicationManager.getApplication().isDispatchThread()) {
      myDocumentManager.saveAllDocuments();
      executor.queue();
    } else if (request.isWaitForCompletion()) {
      ApplicationManager.getApplication().invokeAndWait(myDocumentManager::saveAllDocuments);
      executor.queueAndWaitForCompletion();
    }
    else {
      TransactionGuard.getInstance().submitTransactionAndWait(() -> {
        myDocumentManager.saveAllDocuments();
        executor.queue();
      });
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleBuildInvoker.class);
  }

  public boolean stopBuild(@NotNull ExternalSystemTaskId id) {
    if (myBuildStopper.contains(id)) {
      myBuildStopper.attemptToStopBuild(id, null);
      return true;
    }
    return false;
  }

  public void add(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.add(task);
  }

  @VisibleForTesting
  @NotNull
  protected AfterGradleInvocationTask[] getAfterInvocationTasks() {
    return myAfterTasks.toArray(new AfterGradleInvocationTask[0]);
  }

  public void remove(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.remove(task);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public interface AfterGradleInvocationTask {
    void execute(@NotNull GradleInvocationResult result);
  }

  public static class Request {
    @NotNull private final Project myProject;
    @NotNull private final File myBuildFilePath;
    @NotNull private final List<String> myGradleTasks;
    @NotNull private final List<String> myJvmArguments;
    @NotNull private final List<String> myCommandLineArguments;
    @NotNull
    private final Map<String, String> myEnv;
    private boolean myPassParentEnvs = true;
    @NotNull private final ExternalSystemTaskId myTaskId;

    @Nullable private ExternalSystemTaskNotificationListener myTaskListener;
    @Nullable private BuildAction myBuildAction;
    private boolean myWaitForCompletion;
    /** If true, the build output window will not automatically be shown on failure. */
    private boolean myDoNotShowBuildOutputOnFailure = false;

    public Request(@NotNull Project project, @NotNull File buildFilePath, @NotNull String... gradleTasks) {
      this(project, buildFilePath, Arrays.asList(gradleTasks));
    }

    public Request(@NotNull Project project, @NotNull File buildFilePath, @NotNull List<String> gradleTasks) {
      this(project, buildFilePath, gradleTasks, ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, EXECUTE_TASK, project));
    }

    public Request(@NotNull Project project,
                   @NotNull File buildFilePath,
                   @NotNull List<String> gradleTasks,
                   @NotNull ExternalSystemTaskId taskId) {
      myProject = project;
      myBuildFilePath = buildFilePath;
      myGradleTasks = new ArrayList<>(gradleTasks);
      myJvmArguments = new ArrayList<>();
      myCommandLineArguments = new ArrayList<>();
      myTaskId = taskId;
      myEnv = new LinkedHashMap<>();
    }

    @NotNull
    Project getProject() {
      return myProject;
    }

    @NotNull
    List<String> getGradleTasks() {
      return myGradleTasks;
    }

    @NotNull
    List<String> getJvmArguments() {
      return myJvmArguments;
    }

    @NotNull
    public Request setJvmArguments(@NotNull List<String> jvmArguments) {
      myJvmArguments.clear();
      myJvmArguments.addAll(jvmArguments);
      return this;
    }

    @NotNull
    List<String> getCommandLineArguments() {
      return myCommandLineArguments;
    }

    @NotNull
    public Request setCommandLineArguments(@NotNull List<String> commandLineArguments) {
      myCommandLineArguments.clear();
      myCommandLineArguments.addAll(commandLineArguments);
      return this;
    }

    public Request withEnvironmentVariables(Map<String, String> envs) {
      myEnv.putAll(envs);
      return this;
    }

    public Map<String, String> getEnv() {
      return Collections.unmodifiableMap(myEnv);
    }

    public Request passParentEnvs(boolean passParentEnvs) {
      myPassParentEnvs = passParentEnvs;
      return this;
    }

    public boolean isPassParentEnvs() {
      return myPassParentEnvs;
    }

    @Nullable
    public ExternalSystemTaskNotificationListener getTaskListener() {
      return myTaskListener;
    }

    @NotNull
    public Request setTaskListener(@Nullable ExternalSystemTaskNotificationListener taskListener) {
      myTaskListener = taskListener;
      return this;
    }

    @NotNull
    ExternalSystemTaskId getTaskId() {
      return myTaskId;
    }

    @NotNull
    File getBuildFilePath() {
      return myBuildFilePath;
    }

    boolean isWaitForCompletion() {
      return myWaitForCompletion;
    }

    @NotNull
    public Request waitForCompletion() {
      myWaitForCompletion = true;
      return this;
    }

    @Nullable
    public BuildAction getBuildAction() {
      return myBuildAction;
    }

    @NotNull
    public Request setBuildAction(@Nullable BuildAction buildAction) {
      myBuildAction = buildAction;
      return this;
    }

    /**
     * If called, do not show automatically the build output window when errors are found.
     */
    @NotNull
    public Request doNotShowBuildOutputOnFailure() {
      myDoNotShowBuildOutputOnFailure = true;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request that = (Request)o;
      // We only care about this fields because 'equals' is used for testing only. Production code does not care.
      return Objects.equals(myBuildFilePath, that.myBuildFilePath) &&
             Objects.equals(myGradleTasks, that.myGradleTasks) &&
             Objects.equals(myJvmArguments, that.myJvmArguments) &&
             Objects.equals(myCommandLineArguments, that.myCommandLineArguments) &&
             myDoNotShowBuildOutputOnFailure == that.myDoNotShowBuildOutputOnFailure;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myBuildFilePath, myGradleTasks, myJvmArguments, myCommandLineArguments);
    }

    @Override
    public String toString() {
      return "RequestSettings{" +
             "myBuildFilePath=" + myBuildFilePath +
             ", myGradleTasks=" + myGradleTasks +
             ", myJvmArguments=" + myJvmArguments +
             ", myCommandLineArguments=" + myCommandLineArguments +
             ", myBuildAction=" + myBuildAction +
             ", myDoNotShowBuildOutputOnFailure=" + myDoNotShowBuildOutputOnFailure +
             '}';
    }
  }
}
