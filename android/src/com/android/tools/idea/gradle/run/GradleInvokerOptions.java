/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.startup.GradleSpecificInitializer.ENABLE_EXPERIMENTAL_PROFILING;

public class GradleInvokerOptions {
  @NotNull public final List<String> tasks;
  @Nullable public final BuildMode buildMode;
  @NotNull public final List<String> commandLineArguments;

  private GradleInvokerOptions(@NotNull List<String> tasks, @Nullable BuildMode buildMode, @NotNull List<String> commandLineArguments) {
    this.tasks = tasks;
    this.buildMode = buildMode;
    this.commandLineArguments = commandLineArguments;
  }

  public static GradleInvokerOptions create(@NotNull Project project,
                                            @Nullable DataContext context,
                                            @NotNull RunConfiguration configuration,
                                            @NotNull ExecutionEnvironment env,
                                            @Nullable String userGoal) {
    final Module[] modules = getModules(project, context, configuration);
    if (modules.length == 0) {
      throw new IllegalStateException("Unable to determine list of modules to build");
    }

    GradleInvoker.TestCompileType testCompileType = getTestCompileType(configuration);

    List<AndroidDevice> targetDevices = getTargetDevices(env);

    List<String> cmdLineArgs = Lists.newArrayList();
    cmdLineArgs.addAll(getDeviceSpecificArguments(targetDevices));
    cmdLineArgs.addAll(getProfilingOptions(configuration));

    InstantRunBuildOptions buildOptions = null;
    if (configuration instanceof AndroidRunConfigurationBase &&
        ((AndroidRunConfigurationBase)configuration).supportsInstantRun() &&
        InstantRunUtils.isInstantRunEnabled(env) &&
        InstantRunSettings.isInstantRunEnabled()) {
      buildOptions = InstantRunBuildOptions.createAndReset(modules[0], env, targetDevices);
      if (buildOptions != null) {
        InstantRunManager.LOG.info(buildOptions.toString());
      }
    }

    return create(testCompileType, buildOptions, new GradleModuleTasksProvider(modules), RunAsValidityService.getInstance(),
                  userGoal, cmdLineArgs);
  }

  @NotNull
  private static List<String> getProfilingOptions(@NotNull RunConfiguration configuration) {
    if (System.getProperty(ENABLE_EXPERIMENTAL_PROFILING) == null) {
      return Collections.emptyList();
    }

    if (!(configuration instanceof AndroidRunConfigurationBase)) {
      return Collections.emptyList();
    }

    Properties profilerProperties = ((AndroidRunConfigurationBase)configuration).getProfilerState().toProperties();
    try {
      File propertiesFile = File.createTempFile("profiler", ".properties");
      propertiesFile.deleteOnExit(); // TODO: It'd be nice to clean this up sooner than at exit.

      Writer writer = new OutputStreamWriter(new FileOutputStream(propertiesFile), Charsets.UTF_8);
      profilerProperties.store(writer, "Android Studio Profiler Gradle Plugin Properties");
      writer.close();

      return Collections
        .singletonList(AndroidGradleSettings.createProjectProperty("android.profiler.properties", propertiesFile.getAbsolutePath()));
    }
    catch (IOException e) {
      Throwables.propagate(e);
      return Collections.emptyList();
    }
  }

  @VisibleForTesting()
  static GradleInvokerOptions create(@NotNull GradleInvoker.TestCompileType testCompileType,
                                     @Nullable InstantRunBuildOptions buildOptions,
                                     @NotNull GradleTasksProvider gradleTasksProvider,
                                     @NotNull RunAsValidator runAsValidator,
                                     @Nullable String userGoal,
                                     @Nullable List<String> additionalArguments) {
    if (!StringUtil.isEmpty(userGoal)) {
      return new GradleInvokerOptions(Collections.singletonList(userGoal), null, Collections.<String>emptyList());
    }

    if (testCompileType == GradleInvoker.TestCompileType.JAVA_TESTS) {
      BuildMode buildMode = BuildMode.COMPILE_JAVA;
      return new GradleInvokerOptions(gradleTasksProvider.getUnitTestTasks(buildMode), buildMode, Collections.<String>emptyList());
    }

    List<String> cmdLineArgs = Lists.newArrayList();
    List<String> tasks = Lists.newArrayList();

    if (additionalArguments != null) {
      cmdLineArgs.addAll(additionalArguments);
    }

    if (buildOptions != null) {
      // Inject instant run attributes
      // Note that these are specifically not injected for the unit or instrumentation tests
      if (testCompileType == GradleInvoker.TestCompileType.NONE) {
        boolean incrementalBuild = canBuildIncrementally(buildOptions, runAsValidator);

        cmdLineArgs.add(getInstantDevProperty(buildOptions, incrementalBuild));
        cmdLineArgs.add("-Pandroid.injected.coldswap.mode=default"); // TODO: remove if gradle doesn't need this

        if (buildOptions.cleanBuild) {
          tasks.addAll(gradleTasksProvider.getCleanAndGenerateSourcesTasks());
        }
        else if (incrementalBuild) {
          return new GradleInvokerOptions(gradleTasksProvider.getIncrementalDexTasks(), null, cmdLineArgs);
        }
      }
    }

    BuildMode buildMode = BuildMode.ASSEMBLE;
    tasks.addAll(gradleTasksProvider.getTasksFor(buildMode, testCompileType));
    return new GradleInvokerOptions(tasks, buildMode, cmdLineArgs);
  }

  private static boolean canBuildIncrementally(@NotNull InstantRunBuildOptions options, @NotNull RunAsValidator runAsValidator) {
    if (options.cleanBuild              // e.g. build ids changed
        || options.needsFullBuild) {    // e.g. manifest changed
      return false;
    }

    if (!options.isAppRunning) { // freeze-swap or dexswap scenario
      AndroidDevice device = options.devices.get(0); // Instant Run only supports launching to a single device
      AndroidVersion version = device.getVersion();
      if (!version.isGreaterOrEqualThan(21)) { // don't support cold swap on API < 21
        return false;
      }

      if (!runAsValidator.hasWorkingRunAs(device)) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  private static String getInstantDevProperty(@NotNull InstantRunBuildOptions buildOptions, boolean incrementalBuild) {
    StringBuilder sb = new StringBuilder(50);
    sb.append("-P" + OPTIONAL_COMPILATION_STEPS + "=INSTANT_DEV");

    if (needsRestartOnly(incrementalBuild, buildOptions)) {
      sb.append(",RESTART_ONLY");
    }

    FileChangeListener.Changes changes = buildOptions.fileChanges;
    if (incrementalBuild && !changes.nonSourceChanges) {
      if (changes.localResourceChanges) {
        sb.append(",LOCAL_RES_ONLY");
      }
      if (changes.localJavaChanges) {
        sb.append(",LOCAL_JAVA_ONLY");
      }
    }

    return sb.toString();
  }

  // Returns whether the RESTART_ONLY flag needs to be added
  private static boolean needsRestartOnly(boolean incrementalBuild, InstantRunBuildOptions buildOptions) {
    // we need RESTART_ONLY for all full builds
    if (!incrementalBuild) {
      return true;
    }

    // using multiple processes, or attempting to update when the app is not running requires cold swap patches
    if (buildOptions.usesMultipleProcesses || !buildOptions.isAppRunning) {
      return true;
    }

    return false;
  }

  @NotNull
  private static Module[] getModules(@NotNull Project project, @Nullable DataContext context, @Nullable RunConfiguration configuration) {
    if (configuration instanceof ModuleRunProfile) {
      // ModuleBasedConfiguration includes Android and JUnit run configurations, including "JUnit: Rerun Failed Tests",
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      return  ((ModuleRunProfile)configuration).getModules();
    }
    else {
      return Projects.getModulesToBuildFromSelection(project, context);
    }
  }

  @NotNull
  private static Module[] getAffectedModules(@NotNull Project project, @NotNull Module[] modules) {
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = compilerManager.createModulesCompileScope(modules, true, true);
    return scope.getAffectedModules();
  }

  @NotNull
  private static GradleInvoker.TestCompileType getTestCompileType(@Nullable RunConfiguration runConfiguration) {
    String id = runConfiguration != null ? runConfiguration.getType().getId() : null;
    return GradleInvoker.getTestCompileType(id);
  }

  @NotNull
  static List<String> getDeviceSpecificArguments(@NotNull List<AndroidDevice> devices) {
    if (devices.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> properties = new ArrayList<>(2);

    // Find the minimum value of the build API level and pass it to Gradle as a property
    AndroidVersion minVersion = devices.get(0).getVersion();
    for (int i = 1; i < devices.size(); i++) {
      AndroidDevice androidDevice = devices.get(i);
      if (androidDevice.getVersion().compareTo(minVersion) < 0) {
        minVersion = androidDevice.getVersion();
      }
    }
    properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_API, Integer.toString(minVersion.getFeatureLevel())));

    // If we are building for only one device, pass the density.
    if (devices.size() == 1) {
      Density density = Density.getEnum(devices.get(0).getDensity());
      if (density != null) {
        properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_DENSITY, density.getResourceValue()));
      }
    }

    // Collect the set of all abis supported by all the devices
    Set<String> abis =
      devices.stream().map(AndroidDevice::getAbis)
        .flatMap(Collection::stream)
        .map(Abi::toString)
        .collect(Collectors.toCollection(TreeSet::new));
    if (!abis.isEmpty()) {
      properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_ABI, Joiner.on(',').join(abis)));
    }

    return properties;
  }

  @NotNull
  private static List<AndroidDevice> getTargetDevices(@NotNull ExecutionEnvironment env) {
    DeviceFutures deviceFutures = env.getCopyableUserData(AndroidRunConfigurationBase.DEVICE_FUTURES_KEY);
    return deviceFutures == null ? Collections.emptyList() : deviceFutures.getDevices();
  }

  static class InstantRunBuildOptions {
    public final boolean cleanBuild;
    public final boolean needsFullBuild;
    public final boolean isAppRunning;
    public final boolean usesMultipleProcesses;
    @NotNull private final FileChangeListener.Changes fileChanges;
    @NotNull public final List<AndroidDevice> devices;

    InstantRunBuildOptions(boolean cleanBuild,
                           boolean needsFullBuild,
                           boolean isAppRunning,
                           boolean usesMultipleProcesses,
                           @NotNull FileChangeListener.Changes changes,
                           @NotNull List<AndroidDevice> devices) {
      this.cleanBuild = cleanBuild;
      this.needsFullBuild = needsFullBuild;
      this.isAppRunning = isAppRunning;
      this.usesMultipleProcesses = usesMultipleProcesses;
      this.fileChanges = changes;
      this.devices = devices;
    }

    @Nullable
    static InstantRunBuildOptions createAndReset(@NotNull Module module, @NotNull ExecutionEnvironment env, List<AndroidDevice> targetDevices) {
      if (targetDevices.size() != 1 ||  // IR only supports launching on 1 device
          !InstantRunGradleUtils.getIrSupportStatus(module, targetDevices.get(0).getVersion()).success) {
        return null;
      }

      Project project = module.getProject();
      FileChangeListener.Changes changes = InstantRunManager.get(project).getChangesAndReset();

      return new InstantRunBuildOptions(InstantRunUtils.needsCleanBuild(env),
                                        InstantRunUtils.needsFullBuild(env),
                                        InstantRunUtils.isAppRunning(env),
                                        InstantRunManager.usesMultipleProcesses(module),
                                        changes,
                                        targetDevices);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
        .add("cleanBuild", cleanBuild)
        .add("needsFullBuild", needsFullBuild)
        .add("isAppRunning", isAppRunning)
        .add("multiProcess", usesMultipleProcesses)
        .add("javaFileChange", fileChanges.localJavaChanges)
        .add("resFileChange", fileChanges.localResourceChanges)
        .add("nonSrcFileChange", fileChanges.nonSourceChanges)
        .toString();
    }
  }

  public interface GradleTasksProvider {
    @NotNull
    List<String> getCleanAndGenerateSourcesTasks();

    @NotNull
    List<String> getUnitTestTasks(@NotNull BuildMode buildMode);

    @NotNull
    List<String> getIncrementalDexTasks();

    @NotNull
    List<String> getTasksFor(@NotNull BuildMode buildMode, @NotNull GradleInvoker.TestCompileType testCompileType);
  }

  static class GradleModuleTasksProvider implements GradleTasksProvider {
    private final Module[] myModules;

    GradleModuleTasksProvider(@NotNull Module[] modules) {
      myModules = modules;
      if (myModules.length == 0) {
        throw new IllegalArgumentException("No modules provided");
      }
    }

    @NotNull
    @Override
    public List<String> getCleanAndGenerateSourcesTasks() {
      List<String> tasks = Lists.newArrayList();

      tasks.addAll(GradleInvoker.findCleanTasksForModules(myModules));
      tasks.addAll(GradleInvoker.findTasksToExecute(myModules, BuildMode.SOURCE_GEN, GradleInvoker.TestCompileType.NONE));

      return tasks;
    }

    @NotNull
    @Override
    public List<String> getUnitTestTasks(@NotNull BuildMode buildMode) {
      // Make sure all "intermediates/classes" directories are up-to-date.
      Module[] affectedModules = getAffectedModules(myModules[0].getProject(), myModules);
      return GradleInvoker.findTasksToExecute(affectedModules, buildMode, GradleInvoker.TestCompileType.JAVA_TESTS);
    }

    @NotNull
    @Override
    public List<String> getIncrementalDexTasks() {
      Module module = myModules[0];
      AndroidGradleModel model = AndroidGradleModel.get(module);
      if (model == null) {
        throw new IllegalStateException("Attempted to obtain incremental dex task for module that does not have a Gradle facet");
      }

      return Collections.singletonList(InstantRunGradleUtils.getIncrementalDexTask(model, module));
    }

    @NotNull
    @Override
    public List<String> getTasksFor(@NotNull BuildMode buildMode, @NotNull GradleInvoker.TestCompileType testCompileType) {
      return GradleInvoker.findTasksToExecute(myModules, buildMode, testCompileType);
    }
  }
}
