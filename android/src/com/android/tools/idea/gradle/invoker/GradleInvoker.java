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

import com.android.SdkConstants;
import com.android.builder.model.BaseArtifact;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.invoker.console.view.GradleConsoleView;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.CancellationTokenSource;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.builder.model.AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY;
import static com.android.tools.idea.gradle.AndroidGradleModel.getIdeSetupTasks;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.lastGradleSyncFailed;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static org.jetbrains.android.util.AndroidCommonUtils.isInstrumentationTestConfiguration;
import static org.jetbrains.android.util.AndroidCommonUtils.isTestConfiguration;

/**
 * Invokes Gradle tasks directly. Results of tasks execution are displayed in both the "Messages" tool window and the new "Gradle Console"
 * tool window.
 */
public class GradleInvoker {
  private static final Logger LOG = Logger.getInstance(GradleInvoker.class);

  @NotNull private final Project myProject;

  @NotNull private final Collection<BeforeGradleInvocationTask> myBeforeTasks = Sets.newLinkedHashSet();
  @NotNull private final Collection<AfterGradleInvocationTask> myAfterTasks = Sets.newLinkedHashSet();
  @NotNull private final Map<ExternalSystemTaskId, CancellationTokenSource> myCancellationMap = Maps.newConcurrentMap();

  public static GradleInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleInvoker.class);
  }

  public GradleInvoker(@NotNull Project project) {
    myProject = project;
  }

  @VisibleForTesting
  void addBeforeGradleInvocationTask(@NotNull BeforeGradleInvocationTask task) {
    myBeforeTasks.add(task);
  }

  public void addAfterGradleInvocationTask(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.add(task);
  }

  public void removeAfterGradleInvocationTask(@NotNull AfterGradleInvocationTask task) {
    myAfterTasks.remove(task);
  }

  @NotNull
  AfterGradleInvocationTask[] getAfterInvocationTasks() {
    return myAfterTasks.toArray(new AfterGradleInvocationTask[myAfterTasks.size()]);
  }

  public void cleanProject() {
    setProjectBuildMode(BuildMode.CLEAN);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    // "Clean" also generates sources.
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), BuildMode.SOURCE_GEN, TestCompileType.NONE);
    tasks.add(0, GradleBuilds.CLEAN_TASK_NAME);
    executeTasks(tasks, Collections.singletonList(createGenerateSourcesOnlyProperty()));
  }

  public void assembleTranslate() {
    setProjectBuildMode(BuildMode.ASSEMBLE_TRANSLATE);
    executeTasks(Lists.newArrayList(GradleBuilds.ASSEMBLE_TRANSLATE_TASK_NAME));
  }

  public void generateSources(boolean cleanProject) {
    BuildMode buildMode = BuildMode.SOURCE_GEN;
    setProjectBuildMode(buildMode);

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.NONE);
    if (cleanProject) {
      tasks.add(0, GradleBuilds.CLEAN_TASK_NAME);
    }
    executeTasks(tasks, Collections.singletonList(createGenerateSourcesOnlyProperty()));
  }

  @NotNull
  private static String createGenerateSourcesOnlyProperty() {
    return createProjectProperty(PROPERTY_GENERATE_SOURCES_ONLY, true);
  }

  /**
   * Execute Gradle tasks that compile the relevant Java sources.
   *
   * @param modules         Modules that need to be compiled
   * @param testCompileType Kind of tests that the caller is interested in. Use {@link TestCompileType#NONE} if compiling just the
   *                        main sources, {@link TestCompileType#JAVA_TESTS} if class files for running unit tests are needed.
   */
  public void compileJava(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    BuildMode buildMode = BuildMode.COMPILE_JAVA;
    setProjectBuildMode(buildMode);
    List<String> tasks = findTasksToExecute(modules, buildMode, testCompileType);
    executeTasks(tasks);
  }

  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType) {
    assemble(modules, testCompileType, Collections.emptyList());
  }

  public void assemble(@NotNull Module[] modules, @NotNull TestCompileType testCompileType, @NotNull List<String> arguments) {
    BuildMode buildMode = BuildMode.ASSEMBLE;
    setProjectBuildMode(buildMode);
    List<String> tasks = findTasksToExecute(modules, buildMode, testCompileType);
    executeTasks(tasks, arguments);
  }

  public void rebuild() {
    BuildMode buildMode = BuildMode.REBUILD;
    setProjectBuildMode(buildMode);
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> tasks = findTasksToExecute(moduleManager.getModules(), buildMode, TestCompileType.NONE);
    executeTasks(tasks);
  }

  private void setProjectBuildMode(@NotNull BuildMode buildMode) {
    BuildSettings.getInstance(myProject).setBuildMode(buildMode);
  }

  @NotNull
  public static List<String> findCleanTasksForModules(@NotNull Module[] modules) {
    List<String> tasks = Lists.newArrayList();
    for (Module module : modules) {
      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet == null) {
        continue;
      }
      String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
      addTaskIfSpecified(tasks, gradlePath, GradleBuilds.CLEAN_TASK_NAME);
    }
    return tasks;
  }

  @NotNull
  public static List<String> findTasksToExecute(@NotNull Module[] modules,
                                                @NotNull BuildMode buildMode,
                                                @NotNull TestCompileType testCompileType) {
    List<String> tasks = Lists.newArrayList();

    if (BuildMode.ASSEMBLE == buildMode) {
      Project project = modules[0].getProject();
      if (lastGradleSyncFailed(project)) {
        // If last Gradle sync failed, just call "assemble" at the top-level. Without a model there are no other tasks we can call.
        return Collections.singletonList(GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME);
      }
    }

    for (Module module : modules) {
      if (GradleBuilds.BUILD_SRC_FOLDER_NAME.equals(module.getName())) {
        // "buildSrc" is a special case handled automatically by Gradle.
        continue;
      }
      findAndAddGradleBuildTasks(module, buildMode, tasks, testCompileType);
    }
    if (buildMode == BuildMode.REBUILD && !tasks.isEmpty()) {
      tasks.add(0, GradleBuilds.CLEAN_TASK_NAME);
    }

    if (tasks.isEmpty()) {
      // Unlikely to happen.
      String format = "Unable to find Gradle tasks for project '%1$s' using BuildMode %2$s";
      LOG.info(String.format(format, modules[0].getProject().getName(), buildMode.name()));
    }
    return tasks;
  }

  public void executeTasks(@NotNull List<String> gradleTasks) {
    executeTasks(gradleTasks, Collections.emptyList());
  }

  public void executeTasks(@NotNull List<String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments) {
    if (buildMode != null) {
      setProjectBuildMode(buildMode);
    }
    executeTasks(tasks, commandLineArguments);
  }

  public void executeTasks(@NotNull List<String> gradleTasks, @NotNull List<String> commandLineArguments) {
    ExternalSystemTaskId id = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, EXECUTE_TASK, myProject);

    List<String> jvmArguments = Lists.newArrayList();

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
        File androidHomePath = IdeSdks.getAndroidSdkPath();
        // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
        if (androidHomePath != null) {
          jvmArguments.add(AndroidGradleSettings.createAndroidHomeJvmArg(androidHomePath.getPath()));
        }
      }
    }

    executeTasks(gradleTasks, jvmArguments, commandLineArguments, id, null, false);
  }

  /**
   * Asks to execute target gradle tasks.
   *
   * @param gradleTasks          names of the tasks to execute
   * @param jvmArguments         arguments for the JVM running the gradle tasks.
   * @param commandLineArguments command line arguments to use for the target tasks execution
   * @param taskId               id of the request to execute given gradle tasks (if any), e.g. there is a possible case
   *                             that this call implies from IDE run configuration, so, it assigns a unique id to the request
   *                             to execute target tasks
   * @param taskListener         a listener interested in target tasks processing
   * @param waitForCompletion    a flag which hints whether current method should return control flow before target tasks are executed
   */
  public void executeTasks(@NotNull List<String> gradleTasks,
                           @NotNull List<String> jvmArguments,
                           @NotNull List<String> commandLineArguments,
                           @NotNull ExternalSystemTaskId taskId,
                           @Nullable ExternalSystemTaskNotificationListener taskListener,
                           boolean waitForCompletion) {
    LOG.info("About to execute Gradle tasks: " + gradleTasks);
    if (gradleTasks.isEmpty()) {
      return;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (BeforeGradleInvocationTask listener : myBeforeTasks) {
        //noinspection TestOnlyProblems
        listener.execute(gradleTasks);
      }
      if (!myBeforeTasks.isEmpty()) {
        // In some unit tests we are using 'before tasks' to verify that the correct Gradle tasks are invoked, but those tests do not
        // require a build. Keeping this functionality for now.
        return;
      }
    }

    GradleTaskExecutionContext context =
      new GradleTaskExecutionContext(this, myProject, gradleTasks, jvmArguments, commandLineArguments, myCancellationMap, taskId,
                                     taskListener);
    GradleTasksExecutor executor = new GradleTasksExecutor(context);

    saveAllFilesSafely();

    if (ApplicationManager.getApplication().isDispatchThread()) {
      executor.queue();
    }
    else if (waitForCompletion) {
      executor.queueAndWaitForCompletion();
    }
    else {
      invokeAndWaitIfNeeded((Runnable)executor::queue);
    }
  }

  /**
   * Saves all edited documents. This method can be called from any thread.
   */
  public static void saveAllFilesSafely() {
    invokeAndWaitIfNeeded((Runnable)() -> FileDocumentManager.getInstance().saveAllDocuments());
  }

  public void clearConsoleAndBuildMessages() {
    GradleConsoleView.getInstance(myProject).clear();
    GradleTasksExecutor.clearMessageView(myProject);
  }

  private static void findAndAddGradleBuildTasks(@NotNull Module module,
                                                 @NotNull BuildMode buildMode,
                                                 @NotNull List<String> tasks,
                                                 @NotNull TestCompileType testCompileType) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet == null) {
      return;
    }

    String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
    if (isEmpty(gradlePath)) {
      // Gradle project path is never, ever null. If the path is empty, it shows as ":". We had reports of this happening. It is likely that
      // users manually added the Android-Gradle facet to a project. After all it is likely not to be a Gradle module. Better quit and not
      // build the module.
      String msg = String.format("Module '%1$s' does not have a Gradle path. It is likely that this module was manually added by the user.",
                                 module.getName());
      LOG.info(msg);
      return;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null) {
      JpsAndroidModuleProperties properties = androidFacet.getProperties();

      AndroidGradleModel androidGradleModel = AndroidGradleModel.get(module);

      switch (buildMode) {
        case CLEAN: // Intentional fall-through.
        case SOURCE_GEN:
          addAfterSyncTasks(tasks, gradlePath, properties);
          addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidGradleModel);
          break;
        case ASSEMBLE:
          tasks.add(createBuildTask(gradlePath, properties.ASSEMBLE_TASK_NAME));

          // Add assemble tasks for tests.
          if (testCompileType != TestCompileType.NONE) {
            for (BaseArtifact artifact : getArtifactsForTestCompileType(testCompileType, androidGradleModel)) {
              addTaskIfSpecified(tasks, gradlePath, artifact.getAssembleTaskName());
            }
          }
          break;
        default:
          addAfterSyncTasks(tasks, gradlePath, properties);
          addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidGradleModel);

          // When compiling for unit tests, run only COMPILE_JAVA_TEST_TASK_NAME, which will run javac over main and test code. If the
          // Jack compiler is enabled in Gradle, COMPILE_JAVA_TASK_NAME will end up running e.g. compileDebugJavaWithJack, which produces
          // no *.class files and would be just a waste of time.
          if (testCompileType != TestCompileType.JAVA_TESTS) {
            addTaskIfSpecified(tasks, gradlePath, properties.COMPILE_JAVA_TASK_NAME);
          }

          // Add compile tasks for tests.
          for (BaseArtifact artifact : getArtifactsForTestCompileType(testCompileType, androidGradleModel)) {
            addTaskIfSpecified(tasks, gradlePath, artifact.getCompileTaskName());
          }
          break;
      }
    }
    else {
      JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
      if (javaFacet != null && javaFacet.getConfiguration().BUILDABLE) {
        String gradleTaskName = javaFacet.getGradleTaskName(buildMode);
        if (gradleTaskName != null) {
          tasks.add(createBuildTask(gradlePath, gradleTaskName));
        }
        if (testCompileType == TestCompileType.JAVA_TESTS) {
          tasks.add(createBuildTask(gradlePath, JavaGradleFacet.TEST_CLASSES_TASK_NAME));
        }
      }
    }
  }

  private static void addAfterSyncTasksForTestArtifacts(@NotNull List<String> tasks,
                                                        @NotNull String gradlePath,
                                                        @NotNull TestCompileType testCompileType,
                                                        @Nullable AndroidGradleModel androidGradleModel) {
    Collection<BaseArtifact> testArtifacts = getArtifactsForTestCompileType(testCompileType, androidGradleModel);
    for (BaseArtifact artifact : testArtifacts) {
      for (String taskName : getIdeSetupTasks(artifact)) {
        addTaskIfSpecified(tasks, gradlePath, taskName);
      }
    }
  }

  @NotNull
  private static Collection<BaseArtifact> getArtifactsForTestCompileType(@NotNull TestCompileType testCompileType,
                                                                         @Nullable AndroidGradleModel androidGradleModel) {
    if (androidGradleModel == null) {
      return Collections.emptyList();
    }
    BaseArtifact testArtifact = null;
    switch (testCompileType) {
      case NONE:
        // TestCompileType.NONE means clean / compile all / rebuild all, so we need use all test artifacts.
        return androidGradleModel.getTestArtifactsInSelectedVariant();
      case ANDROID_TESTS:
        testArtifact = androidGradleModel.getAndroidTestArtifactInSelectedVariant();
        break;
      case JAVA_TESTS:
        testArtifact = androidGradleModel.getUnitTestArtifactInSelectedVariant();
    }
    return testArtifact != null ? ImmutableList.of(testArtifact) : Collections.emptyList();
  }

  private static void addAfterSyncTasks(@NotNull List<String> tasks,
                                        @NotNull String gradlePath,
                                        @NotNull JpsAndroidModuleProperties properties) {
    // Make sure all the generated sources, unpacked aars and mockable jars are in place. They are usually up to date, since we
    // generate them at sync time, so Gradle will just skip those tasks. The generated files can be missing if this is a "Rebuild
    // Project" run or if the user cleaned the project from the command line. The mockable jar is necessary to run unit tests, but the
    // compilation tasks don't depend on it, so we have to call it explicitly.
    for (String taskName : properties.AFTER_SYNC_TASK_NAMES) {
      addTaskIfSpecified(tasks, gradlePath, taskName);
    }
  }

  private static void addTaskIfSpecified(@NotNull List<String> tasks, @NotNull String gradlePath, @Nullable String gradleTaskName) {
    if (isNotEmpty(gradleTaskName)) {
      String buildTask = createBuildTask(gradlePath, gradleTaskName);
      if (!tasks.contains(buildTask)) {
        tasks.add(buildTask);
      }
    }
  }

  @NotNull
  public static String createBuildTask(@NotNull String gradleProjectPath, @NotNull String taskName) {
    if (gradleProjectPath.equals(SdkConstants.GRADLE_PATH_SEPARATOR)) {
      // Prevent double colon when dealing with root module (e.g. "::assemble");
      return gradleProjectPath + taskName;
    }
    return gradleProjectPath + SdkConstants.GRADLE_PATH_SEPARATOR + taskName;
  }

  @NotNull
  public static TestCompileType getTestCompileType(@Nullable String runConfigurationId) {
    if (runConfigurationId != null) {
      if (isInstrumentationTestConfiguration(runConfigurationId)) {
        return TestCompileType.ANDROID_TESTS;
      }
      if (isTestConfiguration(runConfigurationId)) {
        return TestCompileType.JAVA_TESTS;
      }
    }
    return TestCompileType.NONE;
  }

  public void cancelTask(@NotNull ExternalSystemTaskId id) {
    CancellationTokenSource token = myCancellationMap.remove(id);
    if (token != null) {
      token.cancel();
    }
  }

  public enum TestCompileType {
    NONE,            // don't compile any tests
    ANDROID_TESTS,   // compile Android, on-device tests
    JAVA_TESTS       // compile Java unit-tests, either in a pure Java module or Android module
  }

  @VisibleForTesting
  interface BeforeGradleInvocationTask {
    void execute(@NotNull List<String> tasks);
  }

  public interface AfterGradleInvocationTask {
    void execute(@NotNull GradleInvocationResult result);
  }
}
