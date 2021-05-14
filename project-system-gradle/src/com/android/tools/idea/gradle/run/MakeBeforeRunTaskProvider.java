/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.builder.model.AndroidProject.PROPERTY_APK_SELECT_CONFIG;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_ABI;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_API;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_API_CODENAME;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_DENSITY;
import static com.android.builder.model.AndroidProject.PROPERTY_BUILD_WITH_STABLE_IDS;
import static com.android.builder.model.AndroidProject.PROPERTY_DEPLOY_AS_INSTANT_APP;
import static com.android.builder.model.AndroidProject.PROPERTY_EXTRACT_INSTANT_APK;
import static com.android.builder.model.AndroidProject.PROPERTY_INJECTED_DYNAMIC_MODULES_LIST;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.run.GradleApkProvider.POST_BUILD_MODEL;
import static com.android.tools.idea.run.editor.ProfilerState.ANDROID_ADVANCED_PROFILING_TRANSFORMS;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_RUN_SYNC_NEEDED_BEFORE_RUNNING;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static java.util.Collections.emptyList;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidDeviceSpec;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.PreferGradleMake;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.stats.RunStats;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.StudioIcons;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.Icon;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public MakeBeforeRunTaskProvider(@NotNull Project project) {
    myProject = project;
    myAndroidProjectInfo = AndroidProjectInfo.getInstance(project);
    myGradleProjectInfo = GradleProjectInfo.getInstance(project);
    myTaskRunnerFactory = new GradleTaskRunnerFactory(myProject);
  }

  @Override
  public Key<MakeBeforeRunTask> getId() {
    return ID;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return StudioIcons.Common.ANDROID_HEAD;
  }

  @Nullable
  @Override
  public Icon getTaskIcon(MakeBeforeRunTask task) {
    return StudioIcons.Common.ANDROID_HEAD;
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
      if (configurationTypeIsEnabledByDefault(runConfiguration)) {
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

  public boolean configurationTypeIsSupported(@NotNull RunConfiguration runConfiguration) {
    if (!(ProjectSystemUtil.getProjectSystem(runConfiguration.getProject()) instanceof GradleProjectSystem)) {
      return false;
    }
    return runConfiguration instanceof PreferGradleMake || isUnitTestConfiguration(runConfiguration);
  }

  public boolean configurationTypeIsEnabledByDefault(@NotNull RunConfiguration runConfiguration) {
    return runConfiguration instanceof PreferGradleMake;
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

  /**
   * Execute the Gradle build task, returns {@code false} in case of any error.
   *
   * <p>Note: Error handling should be improved to notify user in case of {@code false} return value.
   * Currently, the caller does not expect exceptions, and there is no notification mechanism to propagate an
   * error message to the user. The current implementation uses logging (in idea.log) to report errors, whereas the
   * UI behavior is to merely stop the execution without any other sort of notification, which far from ideal.
   */
  @Override
  public boolean executeTask(@NotNull DataContext context,
                             @NotNull RunConfiguration configuration,
                             @NotNull ExecutionEnvironment env,
                             @NotNull MakeBeforeRunTask task) {
    if (Boolean.FALSE.equals(env.getUserData(GradleBuilds.BUILD_SHOULD_EXECUTE))) {
      return true;
    }

    RunStats stats = RunStats.from(env);
    try {
      stats.beginBeforeRunTasks();
      return doExecuteTask(context, configuration, env, task);
    }
    finally {
      stats.endBeforeRunTasks();
    }
  }

  @VisibleForTesting
  enum SyncNeeded {
    NOT_NEEDED(false),
    SINGLE_VARIANT_SYNC_NEEDED(false),
    FULL_SYNC_NEEDED(true),
    NATIVE_VARIANTS_SYNC_NEEDED(false);

    public final boolean isFullSync;

    SyncNeeded(boolean isFullSync) {
      this.isFullSync = isFullSync;
    }
  }

  @VisibleForTesting
  @NotNull
  SyncNeeded isSyncNeeded(Collection<String> abis) {
    // If the project has native modules, and there are any un-synced variants.
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      NdkModuleModel ndkModel = NdkModuleModel.get(module);
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (ndkModel != null && androidModel != null) {
        String selectedVariantName = androidModel.getSelectedVariant().getName();
        Set<String> availableAbis = ndkModel.getSyncedVariantAbis().stream()
          .filter(it -> it.getVariant().equals(selectedVariantName))
          .map(it -> it.getAbi())
          .collect(Collectors.toSet());
        if (!availableAbis.containsAll(abis)) {
          return SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED;
        }
      }
    }
    return SyncNeeded.NOT_NEEDED;
  }

  @Nullable
  private String runSync(@NotNull SyncNeeded syncNeeded,
                         @NotNull Set<@NotNull String> requestedAbis) {
    if (syncNeeded == SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED) {
      GradleSyncInvoker.getInstance().fetchAndMergeNativeVariants(myProject, requestedAbis);
      return null;
    }
    else {
      String result;
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_RUN_SYNC_NEEDED_BEFORE_RUNNING);
      request.runInBackground = false;
      request.forceFullVariantsSync = syncNeeded.isFullSync;

      AtomicReference<String> errorMsgRef = new AtomicReference<>();
      GradleSyncInvoker.getInstance().requestProjectSync(myProject, request, new GradleSyncListener() {
        @Override
        public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
          errorMsgRef.set(errorMessage);
        }
      });
      result = errorMsgRef.get();
      return result;
    }
  }

  private boolean doExecuteTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, MakeBeforeRunTask task) {
    AndroidRunConfigurationBase androidRunConfiguration =
      configuration instanceof AndroidRunConfigurationBase ? (AndroidRunConfigurationBase)configuration : null;
    if (!myAndroidProjectInfo.requiresAndroidModel()) {
      CompileStepBeforeRun regularMake = new CompileStepBeforeRun(myProject);
      return regularMake.executeTask(context, configuration, env, new CompileStepBeforeRun.MakeBeforeRunTask());
    }

    // Note: this run task provider may be invoked from a context such as Java unit tests, in which case it doesn't have
    // the android run config context
    DeviceFutures deviceFutures = env.getCopyableUserData(DeviceFutures.KEY);
    List<AndroidDevice> targetDevices = deviceFutures == null ? emptyList() : deviceFutures.getDevices();
    @Nullable AndroidDeviceSpec targetDeviceSpec = AndroidDeviceSpecUtil.createSpec(targetDevices);


    // Some configurations (e.g. native attach) don't require a build while running the configuration
    if (configuration instanceof RunProfileWithCompileBeforeLaunchOption &&
        ((RunProfileWithCompileBeforeLaunchOption)configuration).isExcludeCompileBeforeLaunchOption()) {
      return true;
    }

    // Compute modules to build
    Module[] modules = getModules(context, configuration);

    List<String> cmdLineArgs;
    try {
      cmdLineArgs = getCommonArguments(modules, androidRunConfiguration, targetDeviceSpec);
      cmdLineArgs.add("--stacktrace");
    }
    catch (Exception e) {
      getLog().warn("Error generating command line arguments for Gradle task", e);
      return false;
    }
    AndroidVersion targetDeviceVersion = targetDeviceSpec != null ? targetDeviceSpec.getCommonVersion() : null;
    BeforeRunBuilder builder = createBuilder(modules, configuration, targetDeviceVersion, task.getGoal());

    GradleTaskRunner.DefaultGradleTaskRunner runner = myTaskRunnerFactory.createTaskRunner(configuration);
    BuildSettings.getInstance(myProject).setRunConfigurationTypeId(configuration.getType().getId());
    try {
      boolean success = builder.build(runner, cmdLineArgs);

      if (androidRunConfiguration != null) {
        Object model = runner.getModel();
        if (model instanceof OutputBuildAction.PostBuildProjectModels) {
          androidRunConfiguration.putUserData(POST_BUILD_MODEL, new PostBuildModel((OutputBuildAction.PostBuildProjectModels)model));
        }
        else {
          getLog().info("Couldn't get post build models.");
        }
      }

      getLog().info("Gradle invocation complete, success = " + success);

      // If the model needs a sync, we need to sync "synchronously" before running.
      Set<String> targetAbis = new HashSet<>(targetDeviceSpec != null ? targetDeviceSpec.getAbis() : emptyList());
      SyncNeeded syncNeeded = isSyncNeeded(targetAbis);

      if (syncNeeded != SyncNeeded.NOT_NEEDED) {
        String errorMsg = runSync(syncNeeded, targetAbis);
        if (errorMsg != null) {
          // Sync failed. There is no point on continuing, because most likely the model is either not there, or has stale information,
          // including the path of the APK.
          getLog().info("Unable to launch '" + TASK_NAME + "' task. Project sync failed with message: " + errorMsg);
          return false;
        }
      }

      if (myProject.isDisposed()) {
        return false;
      }

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
  @VisibleForTesting
  @NotNull
  static List<String> getCommonArguments(@NotNull Module[] modules,
                                         @Nullable AndroidRunConfigurationBase configuration,
                                         @Nullable AndroidDeviceSpec targetDeviceSpec) throws IOException {
    List<String> cmdLineArgs = new ArrayList<>();
    // Always build with stable IDs to avoid push-to-device overhead.
    cmdLineArgs.add(createProjectProperty(PROPERTY_BUILD_WITH_STABLE_IDS, true));
    if (configuration != null) {
      cmdLineArgs.addAll(getDeviceSpecificArguments(modules, configuration, targetDeviceSpec));
      cmdLineArgs.addAll(getProfilingOptions(configuration, targetDeviceSpec));
    }
    return cmdLineArgs;
  }

  @VisibleForTesting
  @NotNull
  static List<String> getDeviceSpecificArguments(@NotNull Module[] modules,
                                                 @NotNull AndroidRunConfigurationBase configuration,
                                                 @Nullable AndroidDeviceSpec deviceSpec) {
    if (deviceSpec == null) {
      return emptyList();
    }

    List<String> properties = new ArrayList<>(3);
    if (useSelectApksFromBundleBuilder(modules, configuration, deviceSpec.getMinVersion())) {
      // For the bundle tool, we create a temporary json file with the device spec and
      // pass the file path to the gradle task.
      boolean collectListOfLanguages = shouldCollectListOfLanguages(modules, configuration, deviceSpec.getMinVersion());
      File deviceSpecFile = AndroidDeviceSpecUtil.writeToJsonTempFile(deviceSpec, collectListOfLanguages);
      properties.add(createProjectProperty(PROPERTY_APK_SELECT_CONFIG, deviceSpecFile.getAbsolutePath()));
      if (configuration instanceof AndroidRunConfiguration) {
        AndroidRunConfiguration androidRunConfiguration = (AndroidRunConfiguration)configuration;

        if (androidRunConfiguration.DEPLOY_AS_INSTANT) {
          properties.add(createProjectProperty(PROPERTY_EXTRACT_INSTANT_APK, true));
        }

        if (androidRunConfiguration.DEPLOY_APK_FROM_BUNDLE) {
          String featureList = getEnabledDynamicFeatureList(modules, androidRunConfiguration);
          if (!featureList.isEmpty()) {
            properties.add(createProjectProperty(PROPERTY_INJECTED_DYNAMIC_MODULES_LIST, featureList));
          }
        }
      }
    }
    else {
      // For non bundle tool deploy tasks, we have one argument per device spec property
      AndroidVersion version = deviceSpec.getCommonVersion();
      if (version != null) {
        properties.add(createProjectProperty(PROPERTY_BUILD_API, Integer.toString(version.getApiLevel())));
        if (version.getCodename() != null) {
          properties.add(createProjectProperty(PROPERTY_BUILD_API_CODENAME, version.getCodename()));
        }
      }

      if (deviceSpec.getDensity() != null) {
        properties.add(createProjectProperty(PROPERTY_BUILD_DENSITY, deviceSpec.getDensity().getResourceValue()));
      }
      if (!deviceSpec.getAbis().isEmpty()) {
        properties.add(createProjectProperty(PROPERTY_BUILD_ABI, Joiner.on(',').join(deviceSpec.getAbis())));
      }
      if (configuration instanceof AndroidRunConfiguration) {
        if (((AndroidRunConfiguration)configuration).DEPLOY_AS_INSTANT) {
          properties.add(createProjectProperty(PROPERTY_DEPLOY_AS_INSTANT_APP, true));
        }
      }
    }
    return properties;
  }

  @NotNull
  private static String getEnabledDynamicFeatureList(@NotNull Module[] modules,
                                                     @NotNull AndroidRunConfiguration configuration) {
    Set<String> disabledFeatures = new HashSet<>(configuration.getDisabledDynamicFeatures());
    return Arrays.stream(modules)
      .flatMap(module -> DynamicAppUtils.getDependentFeatureModulesForBase(module).stream())
      .map(Module::getName)
      .filter(name -> !disabledFeatures.contains(name))
      .map(moduleName -> {
        // e.g name = "MyApplication.dynamicfeature"
        int index = moduleName.lastIndexOf('.');
        return index < 0 ? moduleName : moduleName.substring(index + 1);
      })
      .collect(Collectors.joining(","));
  }

  @NotNull
  private static List<String> getProfilingOptions(@NotNull AndroidRunConfigurationBase configuration,
                                                  @Nullable AndroidDeviceSpec targetDeviceSpec)
    throws IOException {
    if (targetDeviceSpec == null || targetDeviceSpec.getMinVersion() == null) {
      return emptyList();
    }

    // Find the minimum API version in case both a pre-O and post-O devices are selected.
    // TODO: if a post-O app happened to be transformed, the agent needs to account for that.
    int minFeatureLevel = targetDeviceSpec.getMinVersion().getFeatureLevel();
    List<String> arguments = new LinkedList<>();
    ProfilerState state = configuration.getProfilerState();
    if (state.ADVANCED_PROFILING_ENABLED && minFeatureLevel >= AndroidVersion.VersionCodes.LOLLIPOP &&
        minFeatureLevel < AndroidVersion.VersionCodes.O) {
      File file = EmbeddedDistributionPaths.getInstance().findEmbeddedProfilerTransform();
      arguments.add(createProjectProperty(ANDROID_ADVANCED_PROFILING_TRANSFORMS, file.getAbsolutePath()));

      Properties profilerProperties = state.toProperties();
      File propertiesFile = createTempFile("profiler", ".properties");
      propertiesFile.deleteOnExit(); // TODO: It'd be nice to clean this up sooner than at exit.

      Writer writer = new OutputStreamWriter(new FileOutputStream(propertiesFile), Charsets.UTF_8);
      profilerProperties.store(writer, "Android Studio Profiler Gradle Plugin Properties");
      writer.close();

      arguments.add(AndroidGradleSettings.createJvmArg("android.profiler.properties", propertiesFile.getAbsolutePath()));
    }
    return arguments;
  }

  @NotNull
  private static BeforeRunBuilder createBuilder(@NotNull Module[] modules,
                                                @NotNull RunConfiguration configuration,
                                                @Nullable AndroidVersion targetDeviceVersion,
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

    // Use the "select apks from bundle" task if using a "AndroidRunConfigurationBase".
    // Note: This is very ad-hoc, and it would be nice to have a better abstraction for this special case.

    // NOTE: MakeBeforeRunTask is configured on unit-test and AndroidrunConfigurationBase run configurations only. Therefore,
    //       since testCompileType != TestCompileType.UNIT_TESTS it is safe to assume that configuration is
    //       AndroidRunConfigurationBase.
    if (configuration instanceof AndroidRunConfigurationBase
        && useSelectApksFromBundleBuilder(modules, (AndroidRunConfigurationBase)configuration, targetDeviceVersion)) {
      return new DefaultGradleBuilder(gradleTasksProvider.getTasksFor(BuildMode.APK_FROM_BUNDLE, testCompileType),
                                      BuildMode.APK_FROM_BUNDLE);
    }
    return new DefaultGradleBuilder(gradleTasksProvider.getTasksFor(BuildMode.ASSEMBLE, testCompileType), BuildMode.ASSEMBLE);
  }

  private static boolean useSelectApksFromBundleBuilder(@NotNull Module[] modules,
                                                        @NotNull AndroidRunConfigurationBase configuration,
                                                        @Nullable AndroidVersion minTargetDeviceVersion) {
    return Arrays.stream(modules)
      .anyMatch(module -> DynamicAppUtils.useSelectApksFromBundleBuilder(module, configuration, minTargetDeviceVersion));
  }

  private static boolean shouldCollectListOfLanguages(@NotNull Module[] modules,
                                                      @NotNull AndroidRunConfigurationBase configuration,
                                                      @Nullable AndroidVersion targetDeviceVersion) {
    // We should collect the list of languages only if *all* devices are verify the condition, otherwise we would
    // end up deploying language split APKs to devices that don't support them.
    return Arrays.stream(modules)
      .allMatch(module -> DynamicAppUtils.shouldCollectListOfLanguages(module, configuration, targetDeviceVersion));
  }

  @NotNull
  private Module[] getModules(@Nullable DataContext context, @Nullable RunConfiguration configuration) {
    if (configuration instanceof ModuleRunProfile) {
      // ModuleBasedConfiguration includes Android and JUnit run configurations, including "JUnit: Rerun Failed Tests",
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      return ((ModuleRunProfile)configuration).getModules();
    }
    else {
      return myGradleProjectInfo.getModulesToBuildFromSelection(context);
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

    GradleTaskRunnerFactory(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    GradleTaskRunner.DefaultGradleTaskRunner createTaskRunner(@NotNull RunConfiguration configuration) {
      if (configuration instanceof AndroidRunConfigurationBase) {
        List<Module> modules = new ArrayList<>();
        Module selectedModule = ((AndroidRunConfigurationBase)configuration).getConfigurationModule().getModule();
        if (selectedModule != null) {
          modules.add(selectedModule);
          // Instrumented test support for Dynamic features: base-app module should be included explicitly,
          // then corresponding post build models are generated. And in this case, dynamic feature module
          // retrieved earlier is for test Apk.
          if (configuration instanceof AndroidTestRunConfiguration) {
            Module baseModule = DynamicAppUtils.getBaseFeature(selectedModule);
            if (baseModule != null) {
              modules.add(baseModule);
            }
          }
        }
        return GradleTaskRunner.newRunner(myProject, OutputBuildActionUtil.create(modules));
      }
      return GradleTaskRunner.newRunner(myProject, null);
    }
  }
}
