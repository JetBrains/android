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
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.BuildAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import static com.android.builder.model.AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.BuildMode.*;
import static com.android.tools.idea.gradle.util.GradleBuilds.CLEAN_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
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
  @NotNull private final List<String> myLastBuildTasks = new ArrayList<>();
  @NotNull private final BuildStopper myBuildStopper = new BuildStopper();
  @NotNull
  public static GradleBuildInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleBuildInvoker.class);
  }
  public GradleBuildInvoker(@NotNull Project project, @NotNull FileDocumentManager documentManager) {
    this(project, documentManager, new GradleTasksExecutorFactory());
  }
  @VisibleForTesting
  protected GradleBuildInvoker(@NotNull Project project,
                               @NotNull FileDocumentManager documentManager,
                               @NotNull GradleTasksExecutorFactory tasksExecutorFactory) {
    myProject = project;
    myDocumentManager = documentManager;
    myTaskExecutorFactory = tasksExecutorFactory;
  }
  public void cleanProject() {
    setProjectBuildMode(CLEAN);
    // "Clean" also generates sources.
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    List<String> tasks = new ArrayList<>(GradleTaskFinder.getInstance().findTasksToExecute(modules, SOURCE_GEN, TestCompileType.ALL));
    addCleanTask(tasks);
    executeTasks(tasks, Collections.singletonList(createGenerateSourcesOnlyProperty()));
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
    List<String> tasks = new ArrayList<>(GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, TestCompileType.ALL));
    if (cleanProject) {
      addCleanTask(tasks);
    }
    executeTasks(tasks, Collections.singletonList(createGenerateSourcesOnlyProperty()));
  }
  private static void addCleanTask(@NotNull List<String> tasks) {
    tasks.add(0, CLEAN_TASK_NAME);
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
    List<String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, testCompileType);
    executeTasks(tasks);
  }
  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    assemble(modules, testCompileType, Collections.emptyList());
  }
  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType, @Nullable BuildAction<?> buildAction) {
    assemble(modules, testCompileType, Collections.emptyList(), buildAction);
  }
  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType, @NotNull List<String> arguments) {
    assemble(modules, testCompileType, arguments, null);
  }
  public void assemble(@NotNull Module[] modules,
                       @NotNull TestCompileType testCompileType,
                       @NotNull List<String> arguments,
                       @Nullable BuildAction<?> buildAction) {
    BuildMode buildMode = ASSEMBLE;
    setProjectBuildMode(buildMode);
    List<String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(modules, buildMode, testCompileType);
    executeTasks(tasks, arguments, buildAction);
  }
  public void rebuild() {
    BuildMode buildMode = REBUILD;
    setProjectBuildMode(buildMode);
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = GradleTaskFinder.getInstance().findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.ALL);
    executeTasks(tasks);
  }
  /**
   * Execute the last run set of Gradle tasks, with the specified gradle options prepended before the tasks to run.
   */
  public void rebuildWithTempOptions(@NotNull List<String> options) {
    myOneTimeGradleOptions.addAll(options);
    try {
      if (myLastBuildTasks.isEmpty()) {
        // For some reason the IDE lost the Gradle tasks executed during the last build.
        rebuild();
      }
      else {
        // The use case for this is the following:
        // 1. the build fails, and the console has the message "Run with --stacktrace", which now is a hyperlink
        // 2. the user clicks the hyperlink
        // 3. the IDE re-runs the build, with the Gradle tasks that were executed when the build failed, and it adds "--stacktrace"
        //    to the command line arguments.
        List<String> tasksFromLastBuild = new ArrayList<>();
        tasksFromLastBuild.addAll(myLastBuildTasks);
        executeTasks(tasksFromLastBuild);
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
  public void executeTasks(@NotNull List<String> gradleTasks) {
    executeTasks(gradleTasks, myOneTimeGradleOptions);
  }
  public void executeTasks(@NotNull List<String> tasks,
                           @Nullable BuildMode buildMode,
                           @NotNull List<String> commandLineArguments,
                           @Nullable BuildAction buildAction) {
    if (buildMode != null) {
      setProjectBuildMode(buildMode);
    }
    executeTasks(tasks, commandLineArguments, buildAction);
  }
  public void executeTasks(@NotNull List<String> gradleTasks, @NotNull List<String> commandLineArguments) {
    executeTasks(gradleTasks, commandLineArguments, null);
  }
  private void executeTasks(@NotNull List<String> gradleTasks,
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
    Request request = new Request(myProject, gradleTasks);
    // @formatter:off
    request.setJvmArguments(jvmArguments)
           .setCommandLineArguments(commandLineArguments)
           .setBuildAction(buildAction);
    // @formatter:on
    executeTasks(request);
  }
  public void executeTasks(@NotNull Request request) {
    // Remember the current build's tasks, in case they want to re-run it with transient gradle options.
    myLastBuildTasks.clear();
    List<String> gradleTasks = request.getGradleTasks();
    myLastBuildTasks.addAll(gradleTasks);
    getLogger().info("About to execute Gradle tasks: " + gradleTasks);
    if (gradleTasks.isEmpty()) {
      return;
    }
    GradleTasksExecutor executor = myTaskExecutorFactory.create(request, myBuildStopper);
    Runnable executeTasksTask = () -> {
      myDocumentManager.saveAllDocuments();
      if (StudioFlags.GRADLE_INVOCATIONS_INDEXING_AWARE.get()) {
        DumbService.getInstance(myProject).runWhenSmart(executor::queue);
      }
      else {
        executor.queue();
      }
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
  public void stopBuild(@NotNull ExternalSystemTaskId id) {
    myBuildStopper.attemptToStopBuild(id, null);
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
    @NotNull private final List<String> myGradleTasks;
    @NotNull private final List<String> myJvmArguments;
    @NotNull private final List<String> myCommandLineArguments;
    @NotNull private final ExternalSystemTaskId myTaskId;
    @Nullable private ExternalSystemTaskNotificationListener myTaskListener;
    @Nullable private File myBuildFilePath;
    @Nullable private BuildAction myBuildAction;
    private boolean myWaitForCompletion;
    public Request(@NotNull Project project, @NotNull String... gradleTasks) {
      this(project, Arrays.asList(gradleTasks));
    }
    public Request(@NotNull Project project, @NotNull List<String> gradleTasks) {
      this(project, gradleTasks, ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, EXECUTE_TASK, project));
    }
    public Request(@NotNull Project project, @NotNull List<String> gradleTasks, @NotNull ExternalSystemTaskId taskId) {
      myProject = project;
      myGradleTasks = new ArrayList<>(gradleTasks);
      myJvmArguments = new ArrayList<>();
      myCommandLineArguments = new ArrayList<>();
      myTaskId = taskId;
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
    @Nullable
    File getBuildFilePath() {
      return myBuildFilePath;
    }
    @NotNull
    public Request setBuildFilePath(@Nullable File buildFilePath) {
      myBuildFilePath = buildFilePath;
      return this;
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
    public String toString() {
      return "RequestSettings{" +
             "myGradleTasks=" + myGradleTasks +
             ", myJvmArguments=" + myJvmArguments +
             ", myCommandLineArguments=" + myCommandLineArguments +
             ", myBuildAction=" + myBuildAction +
             '}';
    }
  }
}