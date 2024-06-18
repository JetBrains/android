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
package com.android.tools.idea.gradle.run

import com.android.builder.model.PROPERTY_APK_SELECT_CONFIG
import com.android.builder.model.PROPERTY_BUILD_ABI
import com.android.builder.model.PROPERTY_BUILD_API
import com.android.builder.model.PROPERTY_BUILD_API_CODENAME
import com.android.builder.model.PROPERTY_BUILD_WITH_STABLE_IDS
import com.android.builder.model.PROPERTY_DEPLOY_AS_INSTANT_APP
import com.android.builder.model.PROPERTY_EXTRACT_INSTANT_APK
import com.android.builder.model.PROPERTY_INJECTED_DYNAMIC_MODULES_LIST
import com.android.builder.model.PROPERTY_SUPPORTS_PRIVACY_SANDBOX
import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.API_OPTIMIZATION_ENABLE
import com.android.tools.idea.flags.StudioFlags.INJECT_DEVICE_SERIAL_ENABLED
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.Info
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.GradleTaskFinder
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.run.OutputBuildAction.PostBuildProjectModels
import com.android.tools.idea.gradle.util.AndroidGradleSettings
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.DynamicAppUtils.useSelectApksFromBundleBuilder
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.gradle.util.GradleBuilds
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.gradle.RunConfigurationGradleContext
import com.android.tools.idea.projectsystem.gradle.getGradleContext
import com.android.tools.idea.projectsystem.gradle.getGradlePluginVersion
import com.android.tools.idea.projectsystem.requiresAndroidModel
import com.android.tools.idea.run.AndroidDeviceSpec
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.GradleApkProvider
import com.android.tools.idea.run.PreferGradleMake
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.run.profiler.AbstractProfilerExecutorGroup
import com.android.tools.idea.run.profiler.ProfilingMode
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.ModuleRunProfile
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import icons.StudioIcons
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.swing.Icon

/**
 * Provides the "Gradle-aware Make" task for Run Configurations, which
 *
 *  * is only available in Android Studio
 *  * delegates to the regular "Make" if the project is not an Android Gradle project
 *  * otherwise, invokes Gradle directly, to build the project
 *
 */
class MakeBeforeRunTaskProvider : BeforeRunTaskProvider<MakeBeforeRunTask>() {
  override fun getId(): Key<MakeBeforeRunTask> = ID
  override fun getIcon(): Icon = StudioIcons.Common.ANDROID_HEAD
  override fun getTaskIcon(task: MakeBeforeRunTask): Icon = StudioIcons.Common.ANDROID_HEAD
  override fun getName(): String = TASK_NAME
  override fun isConfigurable(): Boolean = true

  override fun getDescription(task: MakeBeforeRunTask): String {
    val goal = task.goal
    return if (goal.isNullOrEmpty()) TASK_NAME else "gradle $goal"
  }

  override fun createTask(runConfiguration: RunConfiguration): MakeBeforeRunTask? {
    // "Gradle-aware Make" is only available in Android Studio.
    if (!configurationTypeIsSupported(runConfiguration)) return null
    return MakeBeforeRunTask().also {
      it.isEnabled = configurationTypeIsEnabledByDefault(runConfiguration)
      // For Android configurations, we want to replace the default make, so this new task needs to be enabled.
      // In AndroidRunConfigurationType#configureBeforeTaskDefaults we disable the default make, which is
      // enabled by default. For other configurations we leave it disabled, so we don't end up with two different
      // make steps executed by default. If the task is added to the run configuration manually, it will be
      // enabled by the UI layer later.
    }
  }

  private fun configurationTypeIsSupported(runConfiguration: RunConfiguration): Boolean {
    return runConfiguration.project.getProjectSystem() is GradleProjectSystem &&
      (runConfiguration is PreferGradleMake || isUnitTestConfiguration(runConfiguration))
  }

  private fun configurationTypeIsEnabledByDefault(runConfiguration: RunConfiguration): Boolean = runConfiguration is PreferGradleMake

  override fun configureTask(runConfiguration: RunConfiguration, task: MakeBeforeRunTask): Boolean {
    val dialog = GradleEditTaskDialog(runConfiguration.project)
    dialog.goal = task.goal
    dialog.setAvailableGoals(createAvailableTasks(runConfiguration.project))
    if (!dialog.showAndGet()) {
      // since we allow tasks without any arguments (assumed to be equivalent to assembling the app),
      // we need a way to specify that a task is not valid. This is because of the current restriction
      // of this API, where the return value from configureTask is ignored.
      task.setInvalid()
      return false
    }
    task.goal = dialog.goal
    return true
  }

  private fun createAvailableTasks(project: Project): List<String> {
    val moduleManager = ModuleManager.getInstance(project)
    val gradleTasks: MutableList<String> = ArrayList()
    for (module in moduleManager.modules) {
      CachedModuleDataFinder.findModuleData(module)?.findAll(ProjectKeys.TASK)?.forEach {
        gradleTasks.add(it.data.name)
      }
    }
    return gradleTasks
  }

  override fun canExecuteTask(configuration: RunConfiguration, task: MakeBeforeRunTask): Boolean = task.isValid

  /**
   * Execute the Gradle build task, returns `false` in case of any error.
   *
   *
   * Note: Error handling should be improved to notify user in case of `false` return value.
   * Currently, the caller does not expect exceptions, and there is no notification mechanism to propagate an
   * error message to the user. The current implementation uses logging (in idea.log) to report errors, whereas the
   * UI behavior is to merely stop the execution without any other sort of notification, which far from ideal.
   */
  override fun executeTask(
    context: DataContext,
    configuration: RunConfiguration,
    env: ExecutionEnvironment,
    task: MakeBeforeRunTask
  ): Boolean {
    if (java.lang.Boolean.FALSE == env.getUserData(GradleBuilds.BUILD_SHOULD_EXECUTE)) {
      return true
    }
    val stats = RunStats.from(env)
    return try {
      stats.beginBeforeRunTasks()
      doExecuteTask(context, configuration, env, task)
    } finally {
      stats.endBeforeRunTasks()
    }
  }

  @VisibleForTesting
  enum class SyncNeeded {
    NOT_NEEDED, NATIVE_VARIANTS_SYNC_NEEDED
  }

  @VisibleForTesting
  fun isSyncNeeded(project: Project, abis: Collection<String>): SyncNeeded {
    // Only trigger sync if both
    //   The project have native modules, synced with v1 (needsAbiSyncBeforeRun)
    //   The ABI is available, but it hasn't been synced yet
    for (module in ModuleManager.getInstance(project).modules) {
      val ndkModel = NdkModuleModel.get(module)
      val androidModel = GradleAndroidModel.get(module)
      if (ndkModel != null && androidModel != null) {
        if (ndkModel.ndkModel.needsAbiSyncBeforeRun) {
          val selectedVariantName = androidModel.selectedVariant.name
          val syncedAbis = ndkModel.syncedVariantAbis
            .filter { it.variant == selectedVariantName }
            .map { it.abi }
            .toSet()
          val availableAbis = ndkModel.allVariantAbis.filter { it.variant == selectedVariantName }.map { it.abi }.toSet()
          for (abi in abis) {
            if (!syncedAbis.contains(abi) && availableAbis.contains(abi)) {
              return SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED
            }
          }
        }
      }
    }
    return SyncNeeded.NOT_NEEDED
  }

  private fun runSyncIfNeeded(project: Project, syncNeeded: SyncNeeded, requestedAbis: Set<String>) {
    return when (syncNeeded) {
      SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED -> GradleSyncInvoker.getInstance().fetchAndMergeNativeVariants(project, requestedAbis)
      SyncNeeded.NOT_NEEDED -> Unit
    }
  }

  private fun doExecuteTask(
    context: DataContext,
    configuration: RunConfiguration,
    env: ExecutionEnvironment,
    task: MakeBeforeRunTask
  ): Boolean {
    if (!configuration.project.requiresAndroidModel()) {
      val regularMake = CompileStepBeforeRun(configuration.project)
      return regularMake.executeTask(context, configuration, env, CompileStepBeforeRun.MakeBeforeRunTask())
    }

    // Note: this run task provider may be invoked from a context such as Java unit tests, in which case it doesn't have
    // the android run config context
    val deviceFutures = env.getCopyableUserData(DeviceFutures.KEY)
    val targetDevices = deviceFutures?.devices ?: emptyList()
    val targetDeviceSpec = createSpec(targetDevices) { title, message ->
      val notification = NotificationGroupManager
        .getInstance()
        .getNotificationGroup("Deploy")
        .createNotification(message, NotificationType.INFORMATION)
        .setTitle(title)
        .setImportant(false)
      notification.notify(env.project)
      AppExecutorUtil.getAppScheduledExecutorService().schedule({ notification.expire() }, 5, TimeUnit.SECONDS)
    }

    // Some configurations (e.g. native attach) don't require a build while running the configuration
    if (configuration is RunProfileWithCompileBeforeLaunchOption &&
      (configuration as RunProfileWithCompileBeforeLaunchOption).isExcludeCompileBeforeLaunchOption
    ) {
      return true
    }

    // To support Profileable Builds, update profiling mode here based on the executor that invoked this build:
    //   Profile as profileable -> PROFILEABLE
    //   Profile as debuggable -> DEBUGGABLE
    //   Other (Run, Debug, legacy Profile) -> NOT_SET
    val profilingMode = if (StudioFlags.PROFILEABLE_BUILDS.get()) {
      AbstractProfilerExecutorGroup.getExecutorSetting(env.executor.id)?.profilingMode ?: ProfilingMode.NOT_SET
    }
    else {
      ProfilingMode.NOT_SET
    }

    // Compute modules to build
    val modules = getModules(context, configuration)
    val runConfigurationGradleContext = configuration.getGradleContext()
    val cmdLineArgs =
      try {
        getCommonArguments(modules, runConfigurationGradleContext, targetDeviceSpec, profilingMode) + "--stacktrace"
      } catch (e: Exception) {
        log.warn("Error generating command line arguments for Gradle task", e)
        return false
      }
    val targetDeviceVersion = targetDeviceSpec?.commonVersion
    val buildResult = build(modules, runConfigurationGradleContext, targetDeviceVersion, task.goal, cmdLineArgs, env)
    if (configuration is UserDataHolderEx && buildResult != null) {
      val model = buildResult.invocationResult.models.firstOrNull()
      if (model is PostBuildProjectModels) {
        configuration.putUserData(GradleApkProvider.POST_BUILD_MODEL, PostBuildModel(model))
      } else {
        log.info("Couldn't get post build models.")
      }
    }
    log.info("Gradle invocation complete, build result = $buildResult")

    // If the model needs a sync, we need to sync "synchronously" before running.
    val targetAbis: Set<String> = targetDeviceSpec?.abis?.toSet().orEmpty()
    val syncNeeded = isSyncNeeded(configuration.project, targetAbis)
    runSyncIfNeeded(configuration.project, syncNeeded, targetAbis)
    return !configuration.project.isDisposed &&
      buildResult != null &&
      buildResult.isBuildSuccessful &&
      buildResult.invocationResult.invocations.isNotEmpty()
  }

  private fun getModules(context: DataContext, configuration: RunConfiguration): Array<Module> {
    return when (configuration) {
      // ModuleBasedConfiguration includes Android and JUnit run configurations, including "JUnit: Rerun Failed Tests",
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      is ModuleRunProfile -> configuration.modules
      else -> Info.getInstance(configuration.project).getModulesToBuildFromSelection(context)
    }
  }

  companion object {
    @JvmField
    val ID = Key.create<MakeBeforeRunTask>("Android.Gradle.BeforeRunTask")

    const val TASK_NAME = "Gradle-aware Make"

    private val log = Logger.getInstance(MakeBeforeRunTask::class.java)

    /**
     * Returns the list of arguments to Gradle that are common to both instant and non-instant builds.
     */
    @VisibleForTesting
    @Throws(IOException::class)
    fun getCommonArguments(
      modules: Array<Module>,
      configuration: RunConfigurationGradleContext?,
      targetDeviceSpec: AndroidDeviceSpec?,
      profilingMode: ProfilingMode
    ): List<String> {
      val cmdLineArgs = mutableListOf<String>()
      // Always build with stable IDs to avoid push-to-device overhead.
      cmdLineArgs.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_WITH_STABLE_IDS, true))
      if (configuration != null) {
        cmdLineArgs.addAll(getDeviceSpecificArguments(modules, configuration, targetDeviceSpec))
        cmdLineArgs.addAll(getProfilingOptions(configuration, targetDeviceSpec, profilingMode))
      }
      return cmdLineArgs
    }

    @VisibleForTesting
    fun getDeviceSpecificArguments(
      modules: Array<Module>,
      configuration: RunConfigurationGradleContext,
      deviceSpec: AndroidDeviceSpec?
    ): List<String> {
      if (deviceSpec == null) {
        return emptyList()
      }
      val properties = mutableListOf<String>()
      val viaBundle = useSelectApksFromBundleBuilder(modules, configuration, deviceSpec.minVersion)
      if (viaBundle) {
        // For the bundle tool, we create a temporary json file with the device spec and
        // pass the file path to the gradle task.
        val collectListOfLanguages = shouldCollectListOfLanguages(modules, configuration, deviceSpec.minVersion)
        val moduleAgpVersions = modules.mapNotNull { it.getGradlePluginVersion() }
        val deviceSpecFile = deviceSpec.writeToJsonTempFile(collectListOfLanguages, moduleAgpVersions)
        properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_APK_SELECT_CONFIG, deviceSpecFile.absolutePath))
        if (configuration.deployAsInstant) {
          properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_EXTRACT_INSTANT_APK, true))
        }
        if (configuration.alwaysDeployApkFromBundle) {
          val featureList = getEnabledDynamicFeatureList(modules, configuration)
          if (featureList.isNotEmpty()) {
            properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_INJECTED_DYNAMIC_MODULES_LIST, featureList))
          }
        }
      } else {
        // For non bundle tool deploy tasks, we have one argument per device spec property
        val version = deviceSpec.commonVersion
        val deviceApiOptimization = API_OPTIMIZATION_ENABLE.get() && GradleExperimentalSettings.getInstance().ENABLE_GRADLE_API_OPTIMIZATION
        if (version != null && deviceApiOptimization) {
          properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_API, version.apiLevel.toString()))
          if (version.codename != null) {
            properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_API_CODENAME, version.codename!!))
          }
        }
        if (deviceSpec.abis.isNotEmpty()) {
          properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_ABI, Joiner.on(',').join(deviceSpec.abis)))
        }
        if (configuration.deployAsInstant) {
          properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_DEPLOY_AS_INSTANT_APP, true))
        }
        if (INJECT_DEVICE_SERIAL_ENABLED.get() && deviceSpec.deviceSerials.isNotEmpty()) {
          // This is an internal, opt-in flag to Sys-UI. See http://b/234033515 for more details.
          val deviceSerials = deviceSpec.deviceSerials.joinToString(separator = ",")
          val injectedProperty = AndroidGradleSettings.createProjectProperty("internal.android.inject.device.serials", deviceSerials)
          properties.add(injectedProperty)
        }
        if (configuration.supportsPrivacySandbox) {
          properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_SUPPORTS_PRIVACY_SANDBOX, deviceSpec.supportsSdkRuntime))
        }
      }
      return properties
    }
  }
}

private fun isUnitTestConfiguration(runConfiguration: RunConfiguration): Boolean {
  return runConfiguration is JUnitConfiguration || runConfiguration.javaClass.simpleName == "TestNGConfiguration"
}

private fun getEnabledDynamicFeatureList(
  modules: Array<Module>,
  configuration: RunConfigurationGradleContext
): String {
  val disabledFeatures = configuration.disabledDynamicFeatureModuleNames
  return modules.asSequence()
    .flatMap { it.getModuleSystem().getDynamicFeatureModules() }
    .map { it.name }
    .filter { name: String -> !disabledFeatures.contains(name) }
    .map { moduleName: String ->
      // e.g name = "MyApplication.dynamicfeature"
      val index = moduleName.lastIndexOf('.')
      if (index < 0) moduleName else moduleName.substring(index + 1)
    }
    .joinToString(separator = ",")
}

@Throws(IOException::class)
private fun getProfilingOptions(
  configuration: RunConfigurationGradleContext,
  targetDeviceSpec: AndroidDeviceSpec?,
  profilingMode: ProfilingMode
): List<String> {
  if (targetDeviceSpec?.minVersion == null) {
    return emptyList()
  }

  // Find the minimum API version in case both a pre-O and post-O devices are selected.
  // TODO: if a post-O app happened to be transformed, the agent needs to account for that.
  val minFeatureLevel = targetDeviceSpec.minVersion!!.featureLevel
  val arguments = mutableListOf<String>()
  if (configuration.isAdvancedProfilingEnabled && minFeatureLevel >= VersionCodes.LOLLIPOP && minFeatureLevel < VersionCodes.O) {
    val file = EmbeddedDistributionPaths.getInstance().findEmbeddedProfilerTransform()
    arguments.add(AndroidGradleSettings.createProjectProperty(ProfilerState.ANDROID_ADVANCED_PROFILING_TRANSFORMS, file.absolutePath))
    val profilerProperties = configuration.profilerProperties ?: Properties()
    val propertiesFile = FileUtil.createTempFile("profiler", ".properties")
    propertiesFile.deleteOnExit() // TODO: It'd be nice to clean this up sooner than at exit.
    val writer: Writer = OutputStreamWriter(FileOutputStream(propertiesFile), Charsets.UTF_8)
    profilerProperties.store(writer, "Android Studio Profiler Gradle Plugin Properties")
    writer.close()
    arguments.add(AndroidGradleSettings.createJvmArg("android.profiler.properties", propertiesFile.absolutePath))
  }
  // Append PROFILING_MODE if set by profilers.
  if (StudioFlags.PROFILEABLE_BUILDS.get() && profilingMode.shouldInjectProjectProperty) {
    arguments.add(AndroidGradleSettings.createProjectProperty(AbstractProfilerExecutorGroup.PROFILING_MODE_PROPERTY_NAME,
                                                              profilingMode.value))
  }
  return arguments
}

private fun build(
  modules: Array<Module>,
  configuration: RunConfigurationGradleContext?,
  targetDeviceVersion: AndroidVersion?,
  userGoal: String?,
  commandLineArgs: List<String>,
  executionEnvironment: ExecutionEnvironment
): AssembleInvocationResult? {

  check(modules.isNotEmpty()) { "Unable to determine list of modules to build" }

  val gradleTasksFinder = GradleTaskFinder.getInstance()

  fun doBuild(tasks: Map<Path, Collection<String>>, buildMode: BuildMode): AssembleInvocationResult? {
    if (tasks.values.flatten().isEmpty()) {
      logger<MakeBeforeRunTaskProvider>().error("Unable to determine gradle tasks to execute")
      return null
    }
    return GradleTaskRunner.run(modules[0].project, modules, tasks, buildMode, commandLineArgs, executionEnvironment)
  }

  if (!userGoal.isNullOrEmpty()) {
    val tasks: Map<Path, List<String>> =
      modules
        .map { ExternalSystemApiUtil.getExternalRootProjectPath(it) }
        .distinct()
        .associate { Paths.get(it) to listOf(userGoal) }

    return doBuild(tasks, BuildMode.DEFAULT_BUILD_MODE)
  }
  return when {
    // Use the "select apks from bundle" task if using a "AndroidRunConfigurationBase".
    // Note: This is very ad-hoc, and it would be nice to have a better abstraction for this special case.

    // NOTE: MakeBeforeRunTask is configured on unit-test and AndroidRunConfigurationBase run configurations only. Therefore,
    //       since this is not about unit tests compilation it is safe to assume that configuration is
    //       AndroidRunConfigurationBase.
    // Um, except that AndroidWearConfiguration now exists.
    useSelectApksFromBundleBuilder(modules, configuration, targetDeviceVersion) ->
      doBuild(gradleTasksFinder.findTasksToExecute(modules, BuildMode.APK_FROM_BUNDLE, expandModules = true).asMap(), BuildMode.APK_FROM_BUNDLE)
    else ->
      doBuild(gradleTasksFinder.findTasksToExecute(modules, BuildMode.ASSEMBLE, expandModules = true).asMap(), BuildMode.ASSEMBLE)
  }
}

private fun useSelectApksFromBundleBuilder(
  modules: Array<Module>,
  configuration: RunConfigurationGradleContext?,
  minTargetDeviceVersion: AndroidVersion?
): Boolean {
  return modules.any {
    useSelectApksFromBundleBuilder(
      it,
      configuration?.alwaysDeployApkFromBundle ?: false,
      configuration?.isTestConfiguration ?: false,
      minTargetDeviceVersion
    )
  }
}

private fun shouldCollectListOfLanguages(
  modules: Array<Module>,
  configuration: RunConfigurationGradleContext,
  targetDeviceVersion: AndroidVersion?
): Boolean {
  // We should collect the list of languages only if *all* devices are verify the condition, otherwise we would
  // end up deploying language split APKs to devices that don't support them.
  return modules.all {
    // Don't collect if not using the bundle tool
    if (!useSelectApksFromBundleBuilder(
        it,
        configuration.alwaysDeployApkFromBundle,
        configuration.isTestConfiguration,
        targetDeviceVersion
      )) {
      false
    } else {
      // Only collect if all devices are L or later devices, because pre-L devices don't support split apks, meaning
      // they don't support install on demand, meaning all languages should be installed.
      targetDeviceVersion != null && targetDeviceVersion.featureLevel >= VersionCodes.LOLLIPOP
    }
  }
}