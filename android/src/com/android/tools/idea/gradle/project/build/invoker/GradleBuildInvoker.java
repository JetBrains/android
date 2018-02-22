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

import com.android.tools.idea.gradle.filters.AndroidReRunBuildFilter;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.build.output.GradleBuildOutputParser;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.impl.*;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.build.output.BuildOutputParser;
import com.intellij.build.output.JavacOutputParser;
import com.intellij.build.output.KotlincOutputParser;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Ref;
import com.intellij.xdebugger.XDebugSession;
import org.gradle.tooling.BuildAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.android.builder.model.AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.BuildMode.*;
import static com.android.tools.idea.gradle.util.GradleBuilds.CLEAN_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.convert;
import static com.intellij.openapi.ui.Messages.*;


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

  public GradleBuildInvoker(@NotNull Project project, @NotNull FileDocumentManager documentManager) {
    this(project, documentManager, new GradleTasksExecutorFactory(), new NativeDebugSessionFinder(project));
  }

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
    // "Clean" also generates sources.
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    File projectPath = getBaseDirPath(myProject);
    ListMultimap<Path, String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(projectPath, modules, SOURCE_GEN,
                                                                                         TestCompileType.NONE);
    tasks.keys().elementSet().forEach(key -> tasks.get(key).add(0, CLEAN_TASK_NAME));
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath), Collections.singletonList(createGenerateSourcesOnlyProperty()));
    }
  }

  public void cleanAndGenerateSources() {
    generateSources(true /* clean project */);
  }

  public void generateSources() {
    generateSources(false /* do not clean project */);
  }

  private void generateSources(boolean cleanProject) {
    BuildMode buildMode = SOURCE_GEN;
    setProjectBuildMode(buildMode);

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    File projectPath = getBaseDirPath(myProject);
    GradleTaskFinder gradleTaskFinder = GradleTaskFinder.getInstance();
    ListMultimap<Path, String> tasks = gradleTaskFinder.findTasksToExecute(projectPath, modules, buildMode, TestCompileType.NONE);
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
    return createProjectProperty(PROPERTY_GENERATE_SOURCES_ONLY, true);
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
    File projectPath = getBaseDirPath(myProject);
    ListMultimap<Path, String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(projectPath, modules, buildMode, testCompileType);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath));
    }
  }

  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    assemble(modules, testCompileType, Collections.emptyList(), null);
  }

  public void assemble(@NotNull Module[] modules,
                       @NotNull TestCompileType testCompileType,
                       @NotNull List<String> arguments,
                       @Nullable BuildAction<?> buildAction) {
    BuildMode buildMode = ASSEMBLE;
    setProjectBuildMode(buildMode);
    File projectPath = getBaseDirPath(myProject);
    ListMultimap<Path, String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(projectPath, modules, buildMode, testCompileType);
    for (Path rootPath : tasks.keySet()) {
      executeTasks(rootPath.toFile(), tasks.get(rootPath), arguments, buildAction);
    }
  }

  public void rebuild() {
    BuildMode buildMode = REBUILD;
    setProjectBuildMode(buildMode);
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    File projectPath = getBaseDirPath(myProject);
    ListMultimap<Path, String> tasks =
      GradleTaskFinder.getInstance().findTasksToExecute(projectPath, moduleManager.getModules(), buildMode, TestCompileType.NONE);
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
  public void executeTasks(@NotNull List<String> gradleTasks) {
    File path = getBaseDirPath(myProject);
    executeTasks(path, gradleTasks, myOneTimeGradleOptions);
  }

  public void executeTasks(@NotNull File buildFilePath, @NotNull List<String> gradleTasks) {
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

  public void executeTasks(@NotNull File buildFilePath, @NotNull List<String> gradleTasks, @NotNull List<String> commandLineArguments) {
    executeTasks(buildFilePath, gradleTasks, commandLineArguments, null);
  }

  public void executeTasks(@NotNull File buildFilePath,
                           @NotNull List<String> gradleTasks,
                           @NotNull List<String> commandLineArguments,
                           @Nullable BuildAction buildAction) {
    List<String> jvmArguments = new ArrayList<>();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Projects in tests may not have a local.properties, set ANDROID_HOME JVM argument if that's the case.
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
    List<BuildOutputParser> buildOutputParsers =
      Arrays.asList(new JavacOutputParser(), new KotlincOutputParser(), new GradleBuildOutputParser());

    // This is resource is closed when onEnd is called or an exception is generated in this function bSee b/70299236.
    // We need to keep this resource open since closing it causes BuildOutputInstantReaderImpl.myThread to stop, preventing parsers to run.
    //noinspection resource, IOResourceOpenedButNotSafelyClosed
    BuildOutputInstantReaderImpl buildOutputInstantReader = new BuildOutputInstantReaderImpl(request.myTaskId, buildViewManager,
                                                                                             buildOutputParsers);
    try {
      return new ExternalSystemTaskNotificationListenerAdapter() {
        @NotNull private BuildOutputInstantReaderImpl myReader = buildOutputInstantReader;

        @Override
        public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
          AnAction restartAction = new AnAction() {
            @Override
            public void update(@NotNull AnActionEvent e) {
              super.update(e);
              e.getPresentation().setEnabled(!myBuildStopper.contains(id));
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
              // Recreate the reader since the one created with the listener can be already closed (see b/73102585)
              myReader.close();
              // noinspection resource, IOResourceOpenedButNotSafelyClosed
              myReader = new BuildOutputInstantReaderImpl(request.myTaskId, buildViewManager, buildOutputParsers);
              executeTasks(request);
            }
          };

          Presentation presentation = restartAction.getTemplatePresentation();
          presentation.setText("Restart");
          presentation.setDescription("Restart");
          presentation.setIcon(AllIcons.Actions.Compile);

          long eventTime = System.currentTimeMillis();
          StartBuildEventImpl event = new StartBuildEventImpl(new DefaultBuildDescriptor(id, executionName, workingDir, eventTime),
                                                              "running...");
          event.withRestartAction(restartAction).withExecutionFilter(new AndroidReRunBuildFilter(workingDir));
          buildViewManager.onEvent(event);
        }

        @Override
        public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
          if (event instanceof ExternalSystemTaskExecutionEvent) {
            BuildEvent buildEvent = convert(((ExternalSystemTaskExecutionEvent)event));
            buildViewManager.onEvent(buildEvent);
          }
        }

        @Override
        public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
          buildViewManager.onEvent(new OutputBuildEventImpl(id, text, stdOut));
          myReader.append(text);
        }

        @Override
        public void onEnd(@NotNull ExternalSystemTaskId id) {
          myReader.close();
        }

        @Override
        public void onSuccess(@NotNull ExternalSystemTaskId id) {
          FinishBuildEventImpl event = new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "completed successfully",
                                                                new SuccessResultImpl());
          buildViewManager.onEvent(event);
        }

        @Override
        public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
          String title = executionName + " failed";
          FailureResult failureResult = ExternalSystemUtil.createFailureResult(title, e, GRADLE_SYSTEM_ID, myProject);
          buildViewManager.onEvent(new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "build failed", failureResult));
        }

        @Override
        public void onCancel(@NotNull ExternalSystemTaskId id) {
          super.onCancel(id);
          // Cause build view to show as skipped all pending tasks (b/73397414)
          FinishBuildEventImpl event = new FinishBuildEventImpl(id, null, System.currentTimeMillis(), "cancelled", new SkippedResultImpl());
          buildViewManager.onEvent(event);
          myReader.close();
        }
      };
    }
    catch (Exception ignored) {
      buildOutputInstantReader.close();
      throw ignored;
    }
  }

  public void executeTasks(@NotNull Request request) {
    String buildFilePath = request.myBuildFilePath.getPath();
    // Remember the current build's tasks, in case they want to re-run it with transient gradle options.
    myLastBuildTasks.removeAll(buildFilePath);
    List<String> gradleTasks = request.getGradleTasks();
    myLastBuildTasks.putAll(buildFilePath, gradleTasks);

    getLogger().info("About to execute Gradle tasks: " + gradleTasks);
    if (gradleTasks.isEmpty()) {
      return;
    }
    GradleTasksExecutor executor = myTaskExecutorFactory.create(request, myBuildStopper);
    Runnable executeTasksTask = () -> {
      myDocumentManager.saveAllDocuments();
      executor.queue();
    };

    if (ApplicationManager.getApplication().isDispatchThread()) {
      executeTasksTask.run();
    }
    else if (request.isWaitForCompletion()) {
      executor.queueAndWaitForCompletion();
    }
    else {
      TransactionGuard.getInstance().submitTransactionAndWait(executeTasksTask);
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
    return myAfterTasks.toArray(new AfterGradleInvocationTask[myAfterTasks.size()]);
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
             Objects.equals(myCommandLineArguments, that.myCommandLineArguments);
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
             '}';
    }
  }
}
