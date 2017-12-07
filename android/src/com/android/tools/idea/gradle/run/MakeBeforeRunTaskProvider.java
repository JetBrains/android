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
package com.android.tools.idea.gradle.run;

import com.android.builder.model.TestedTargetVariant;
import com.android.ddmlib.IDevice;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.fd.InstantRunBuilder;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Ordering;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.ThreeState;
import icons.AndroidIcons;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleProjects.getModulesToBuildFromSelection;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.android.tools.idea.run.editor.ProfilerState.ANDROID_ADVANCED_PROFILING_TRANSFORMS;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * Provides the "Gradle-aware Make" task for Run Configurations, which
 * <ul>
 * <li>is only available in Android Studio</li>
 * <li>delegates to the regular "Make" if the project is not an Android Gradle project</li>
 * <li>otherwise, invokes Gradle directly, to build the project</li>
 * </ul>
 */
public class MakeBeforeRunTaskProvider extends BeforeRunTaskProvider<MakeBeforeRunTask> {
  @NotNull public static final Key<MakeBeforeRunTask> ID = Key.create("Android.Gradle.BeforeRunTask");

  public static final String TASK_NAME = "Gradle-aware Make";

  @NotNull private final Project myProject;
  @NotNull private final AndroidProjectInfo myAndroidProjectInfo;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final GradleTaskRunnerFactory myTaskRunnerFactory;

  public MakeBeforeRunTaskProvider(@NotNull Project project,
                                   @NotNull AndroidProjectInfo androidProjectInfo,
                                   @NotNull GradleProjectInfo gradleProjectInfo) {
    myProject = project;
    myAndroidProjectInfo = androidProjectInfo;
    myGradleProjectInfo = gradleProjectInfo;
    myTaskRunnerFactory = new GradleTaskRunnerFactory(myProject, GradleVersions.getInstance());
  }

  @Override
  public Key<MakeBeforeRunTask> getId() {
    return ID;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }

  @Nullable
  @Override
  public Icon getTaskIcon(MakeBeforeRunTask task) {
    return AndroidIcons.Android;
  }

  @Override
  public String getName() {
    return TASK_NAME;
  }

  @Override
  public String getDescription(MakeBeforeRunTask task) {
    String goal = task.getGoal();
    return isEmpty(goal) ? TASK_NAME : "gradle " + goal;
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Nullable
  @Override
  public MakeBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    // "Gradle-aware Make" is only available in Android Studio.
    if (configurationTypeIsSupported(runConfiguration)) {
      MakeBeforeRunTask task = new MakeBeforeRunTask();
      if (runConfiguration instanceof PreferGradleMake) {
        // For Android configurations, we want to replace the default make, so this new task needs to be enabled.
        // In AndroidRunConfigurationType#configureBeforeTaskDefaults we disable the default make, which is
        // enabled by default. For other configurations we leave it disabled, so we don't end up with two different
        // make steps executed by default. If the task is added to the run configuration manually, it will be
        // enabled by the UI layer later.
        task.setEnabled(true);
      }
      return task;
    }
    else {
      return null;
    }
  }

  private boolean configurationTypeIsSupported(@NotNull RunConfiguration runConfiguration) {
    if (myAndroidProjectInfo.isApkProject()) {
      return false;
    }
    return runConfiguration instanceof PreferGradleMake || isUnitTestConfiguration(runConfiguration);
  }

  private static boolean isUnitTestConfiguration(@NotNull RunConfiguration runConfiguration) {
    return runConfiguration instanceof JUnitConfiguration ||
           // Avoid direct dependency on the TestNG plugin:
           runConfiguration.getClass().getSimpleName().equals("TestNGConfiguration");
  }

  @Override
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull MakeBeforeRunTask task) {
    GradleEditTaskDialog dialog = new GradleEditTaskDialog(myProject);
    dialog.setGoal(task.getGoal());
    dialog.setAvailableGoals(createAvailableTasks());
    if (!dialog.showAndGet()) {
      // since we allow tasks without any arguments (assumed to be equivalent to assembling the app),
      // we need a way to specify that a task is not valid. This is because of the current restriction
      // of this API, where the return value from configureTask is ignored.
      task.setInvalid();
      return false;
    }

    task.setGoal(dialog.getGoal());
    return true;
  }

  @NotNull
  private List<String> createAvailableTasks() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<String> gradleTasks = new ArrayList<>();
    for (Module module : moduleManager.getModules()) {
      GradleFacet facet = GradleFacet.getInstance(module);
      if (facet == null) {
        continue;
      }

      GradleModuleModel gradleModuleModel = facet.getGradleModuleModel();
      if (gradleModuleModel == null) {
        continue;
      }

      gradleTasks.addAll(gradleModuleModel.getTaskNames());
    }
    return gradleTasks;
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull MakeBeforeRunTask task) {
    return task.isValid();
  }

  @Override
  public boolean executeTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, MakeBeforeRunTask task) {
    if (!myAndroidProjectInfo.requiresAndroidModel() || !myGradleProjectInfo.isDirectGradleBuildEnabled()) {
      CompileStepBeforeRun regularMake = new CompileStepBeforeRun(myProject);
      return regularMake.executeTask(context, configuration, env, new CompileStepBeforeRun.MakeBeforeRunTask());
    }

    AtomicReference<String> errorMsgRef = new AtomicReference<>();

    if (AndroidGradleBuildConfiguration.getInstance(myProject).SYNC_PROJECT_BEFORE_BUILD) {
      // If the model needs a sync, we need to sync "synchronously" before running.
      // See: https://code.google.com/p/android/issues/detail?id=70718
      GradleSyncState syncState = GradleSyncState.getInstance(myProject);
      if (syncState.isSyncNeeded() != ThreeState.NO) {

        GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
        request.runInBackground = false;

        GradleSyncInvoker.getInstance().requestProjectSync(myProject, request, new GradleSyncListener.Adapter() {
          @Override
          public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
            errorMsgRef.set(errorMessage);
          }
        });
      }
    }

    String errorMsg = errorMsgRef.get();
    if (errorMsg != null) {
      // Sync failed. There is no point on continuing, because most likely the model is either not there, or has stale information,
      // including the path of the APK.
      getLog().info("Unable to launch '" + TASK_NAME + "' task. Project sync failed with message: " + errorMsg);
      return false;
    }

    if (myProject.isDisposed()) {
      return false;
    }

    // Some configurations (e.g. native attach) don't require a build while running the configuration
    if (configuration instanceof RunConfigurationBase && ((RunConfigurationBase)configuration).excludeCompileBeforeLaunchOption()) {
      return true;
    }

    // Note: this before run task provider may be invoked from a context such as Java unit tests, in which case it doesn't have
    // the android run config context
    AndroidRunConfigContext runConfigContext = env.getCopyableUserData(AndroidRunConfigContext.KEY);
    DeviceFutures deviceFutures = runConfigContext == null ? null : runConfigContext.getTargetDevices();
    List<AndroidDevice> targetDevices = deviceFutures == null ? Collections.emptyList() : deviceFutures.getDevices();
    List<String> cmdLineArgs = getCommonArguments(configuration, targetDevices);

    BeforeRunBuilder builder =
      createBuilder(env, getModules(myProject, context, configuration), configuration, runConfigContext, task.getGoal());

    GradleTaskRunner.DefaultGradleTaskRunner runner = myTaskRunnerFactory.createTaskRunner(configuration);

    try {
      boolean success = builder.build(runner, cmdLineArgs);

      if (configuration instanceof AndroidRunConfigurationBase) {
        Object model = runner.getModel();
        if (model != null && model instanceof OutputBuildAction.PostBuildProjectModels) {
          ((AndroidRunConfigurationBase)configuration).setOutputModel(new PostBuildModel((OutputBuildAction.PostBuildProjectModels)model));
        }
        else {
          getLog().info("Couldn't get post build models.");
        }
      }

      getLog().info("Gradle invocation complete, success = " + success);
      return success;
    }
    catch (InvocationTargetException e) {
      getLog().info("Unexpected error while launching gradle before run tasks", e);
      return false;
    }
    catch (InterruptedException e) {
      getLog().info("Interrupted while launching gradle before run tasks");
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(MakeBeforeRunTask.class);
  }

  /**
   * Returns the list of arguments to Gradle that are common to both instant and non-instant builds.
   */
  @NotNull
  private static List<String> getCommonArguments(@NotNull RunConfiguration configuration, @NotNull List<AndroidDevice> targetDevices) {
    List<String> cmdLineArgs = new ArrayList<>();
    cmdLineArgs.addAll(getDeviceSpecificArguments(targetDevices));
    cmdLineArgs.addAll(getProfilingOptions(configuration, targetDevices));
    return cmdLineArgs;
  }

  @NotNull
  public static List<String> getDeviceSpecificArguments(@NotNull List<AndroidDevice> devices) {
    if (devices.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> properties = new ArrayList<>(2);

    // Find the minimum value of the build API level and pass it to Gradle as a property
    List<AndroidVersion> versionLists = devices.stream().map(AndroidDevice::getVersion).collect(Collectors.toList());
    AndroidVersion minVersion = Ordering.natural().min(versionLists);
    properties.add(createProjectProperty(PROPERTY_BUILD_API, Integer.toString(minVersion.getApiLevel())));
    if(minVersion.getCodename() != null) {
      properties.add(createProjectProperty(PROPERTY_BUILD_API_CODENAME, minVersion.getCodename()));
    }

    // If we are building for only one device, pass the density and the ABI
    if (devices.size() == 1) {
      AndroidDevice device = devices.get(0);
      Density density = Density.getEnum(device.getDensity());
      if (density != null) {
        properties.add(createProjectProperty(PROPERTY_BUILD_DENSITY, density.getResourceValue()));
      }

      // Note: the abis are returned in their preferred order which should be maintained while passing it on to Gradle.
      List<String> abis = device.getAbis().stream().map(Abi::toString).collect(Collectors.toList());
      if (!abis.isEmpty()) {
        properties.add(createProjectProperty(PROPERTY_BUILD_ABI, Joiner.on(',').join(abis)));
      }
    }

    return properties;
  }

  @NotNull
  private static List<String> getProfilingOptions(@NotNull RunConfiguration configuration, @NotNull List<AndroidDevice> devices) {
    if (!StudioFlags.PROFILER_ENABLED.get() || !(configuration instanceof AndroidRunConfigurationBase) || devices.isEmpty()) {
      return Collections.emptyList();
    }

    // Find the minimum API version in case both a pre-O and post-O devices are selected.
    // TODO: if a post-O app happened to be transformed, the agent needs to account for that.
    List<AndroidVersion> versionLists = devices.stream().map(AndroidDevice::getVersion).collect(Collectors.toList());
    AndroidVersion minVersion = Ordering.natural().min(versionLists);
    List<String> arguments = new LinkedList<>();
    ProfilerState state = ((AndroidRunConfigurationBase)configuration).getProfilerState();
    if (state.ADVANCED_PROFILING_ENABLED && minVersion.getFeatureLevel() >= AndroidVersion.VersionCodes.LOLLIPOP &&
        (minVersion.getFeatureLevel() < AndroidVersion.VersionCodes.O || !StudioFlags.PROFILER_USE_JVMTI.get())) {
      File file = EmbeddedDistributionPaths.getInstance().findEmbeddedProfilerTransform(minVersion);
      arguments.add(createProjectProperty(ANDROID_ADVANCED_PROFILING_TRANSFORMS, file.getAbsolutePath()));

      Properties profilerProperties = state.toProperties();
      profilerProperties.setProperty(StudioFlags.PROFILER_NETWORK_REQUEST_PAYLOAD.getId(),
                                     String.valueOf(StudioFlags.PROFILER_NETWORK_REQUEST_PAYLOAD.get()));
      try {
        File propertiesFile = createTempFile("profiler", ".properties");
        propertiesFile.deleteOnExit(); // TODO: It'd be nice to clean this up sooner than at exit.

        Writer writer = new OutputStreamWriter(new FileOutputStream(propertiesFile), Charsets.UTF_8);
        profilerProperties.store(writer, "Android Studio Profiler Gradle Plugin Properties");
        writer.close();

        arguments.add(AndroidGradleSettings.createJvmArg("android.profiler.properties", propertiesFile.getAbsolutePath()));
      }
      catch (IOException e) {
        Throwables.propagate(e);
      }
    }
    return arguments;
  }

  private static BeforeRunBuilder createBuilder(@NotNull ExecutionEnvironment env,
                                                @NotNull Module[] modules,
                                                @NotNull RunConfiguration configuration,
                                                @Nullable AndroidRunConfigContext runConfigContext,
                                                @Nullable String userGoal) {
    if (modules.length == 0) {
      throw new IllegalStateException("Unable to determine list of modules to build");
    }

    if (!isEmpty(userGoal)) {
      ListMultimap<Path, String> tasks = ArrayListMultimap.create();
      StreamEx.of(modules)
        .map(module -> ExternalSystemApiUtil.getExternalRootProjectPath(module))
        .nonNull()
        .distinct()
        .map(path -> Paths.get(path))
        .forEach(path -> tasks.put(path, userGoal));
      return new DefaultGradleBuilder(tasks, null);
    }

    GradleModuleTasksProvider gradleTasksProvider = new GradleModuleTasksProvider(modules);

    TestCompileType testCompileType = TestCompileType.get(configuration.getType().getId());
    if (testCompileType == TestCompileType.UNIT_TESTS) {
      BuildMode buildMode = BuildMode.COMPILE_JAVA;
      return new DefaultGradleBuilder(gradleTasksProvider.getUnitTestTasks(buildMode), buildMode);
    }

    InstantRunContext irContext = env.getCopyableUserData(InstantRunContext.KEY);
    DeviceFutures deviceFutures = runConfigContext == null ? null : runConfigContext.getTargetDevices();
    if (deviceFutures == null || irContext == null) {
      return new DefaultGradleBuilder(gradleTasksProvider.getTasksFor(BuildMode.ASSEMBLE, testCompileType), BuildMode.ASSEMBLE);
    }

    List<AndroidDevice> targetDevices = deviceFutures.getDevices();
    assert targetDevices.size() == 1 : "instant run context available, but deploying to > 1 device";
    return new InstantRunBuilder(getLaunchedDevice(targetDevices.get(0)), irContext, runConfigContext, gradleTasksProvider);
  }

  @NotNull
  private static Module[] getModules(@NotNull Project project, @Nullable DataContext context, @Nullable RunConfiguration configuration) {
    if (configuration instanceof ModuleRunProfile) {
      // If running JUnit tests for "whole project", all the modules should be compiled, but getModules() return an empty array.
      // See http://b.android.com/230678
      if (configuration instanceof AndroidJUnitConfiguration) {
        return ((AndroidJUnitConfiguration)configuration).getModulesToCompile();
      }
      // ModuleBasedConfiguration includes Android and JUnit run configurations, including "JUnit: Rerun Failed Tests",
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      return ((ModuleRunProfile)configuration).getModules();
    }
    else {
      return getModulesToBuildFromSelection(project, context);
    }
  }

  @Nullable
  public static IDevice getLaunchedDevice(@NotNull AndroidDevice device) {
    if (!device.getLaunchedDevice().isDone()) {
      // If we don't have access to the device (this happens if the AVD is still launching)
      return null;
    }

    try {
      return device.getLaunchedDevice().get(1, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
    catch (ExecutionException | TimeoutException e) {
      return null;
    }
  }

  @VisibleForTesting
  static class GradleTaskRunnerFactory {
    @NotNull private final Project myProject;
    @NotNull private final GradleVersions myGradleVersions;

    GradleTaskRunnerFactory(@NotNull Project project, @NotNull GradleVersions gradleVersions) {
      myProject = project;
      myGradleVersions = gradleVersions;
    }

    @NotNull
    GradleTaskRunner.DefaultGradleTaskRunner createTaskRunner(@NotNull RunConfiguration configuration) {
      if (configuration instanceof AndroidRunConfigurationBase) {
        GradleVersion version = myGradleVersions.getGradleVersion(myProject);
        if (version != null && version.isAtLeast(3, 5, 0)) {
          // This APIs are supported by Gradle 3.5.0+ only.
          Module selectedModule = ((AndroidRunConfigurationBase)configuration).getConfigurationModule().getModule();
          return GradleTaskRunner.newBuildActionRunner(myProject, new OutputBuildAction(getConcernedGradlePaths(selectedModule)));
        }
      }
      return GradleTaskRunner.newRunner(myProject);
    }

    /**
     * Get the gradle paths for the given module and all the tested projects (if it is a test app).
     * These paths will be used by the BuildAction run after build to know all the needed models.
     */
    @NotNull
    private static Collection<String> getConcernedGradlePaths(@Nullable Module module) {
      if (module == null) {
        return Collections.emptySet();
      }

      Collection<String> gradlePaths = new HashSet<>();
      gradlePaths.add(getGradlePath(module));
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        IdeAndroidProject androidProject = androidModel.getAndroidProject();
        if (androidProject.getProjectType() == PROJECT_TYPE_TEST) {
          for (TestedTargetVariant testedVariant : androidModel.getSelectedVariant().getTestedTargetVariants()) {
            gradlePaths.add(testedVariant.getTargetProjectPath());
          }
        }
      }

      return gradlePaths;
    }
  }
}
