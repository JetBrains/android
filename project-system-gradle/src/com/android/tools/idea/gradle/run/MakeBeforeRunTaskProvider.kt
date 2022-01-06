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
import com.android.builder.model.PROPERTY_BUILD_DENSITY
import com.android.builder.model.PROPERTY_BUILD_WITH_STABLE_IDS
import com.android.builder.model.PROPERTY_DEPLOY_AS_INSTANT_APP
import com.android.builder.model.PROPERTY_EXTRACT_INSTANT_APK
import com.android.builder.model.PROPERTY_INJECTED_DYNAMIC_MODULES_LIST
import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel.Companion.get
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.run.OutputBuildAction.PostBuildProjectModels
import com.android.tools.idea.gradle.util.AndroidGradleSettings
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.gradle.util.GradleBuilds
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.run.AndroidDeviceSpec
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.GradleApkProvider
import com.android.tools.idea.run.PreferGradleMake
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.stats.RunStats
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
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBuildCommonUtils
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Icon

/**
 * Provides the "Gradle-aware Make" task for Run Configurations, which
 *
 *  * is only available in Android Studio
 *  * delegates to the regular "Make" if the project is not an Android Gradle project
 *  * otherwise, invokes Gradle directly, to build the project
 *
 */
class MakeBeforeRunTaskProvider(private val project: Project) : BeforeRunTaskProvider<MakeBeforeRunTask>() {
  private val androidProjectInfo: AndroidProjectInfo = AndroidProjectInfo.getInstance(project)
  private val gradleProjectInfo: GradleProjectInfo = GradleProjectInfo.getInstance(project)

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

  fun configurationTypeIsSupported(runConfiguration: RunConfiguration): Boolean {
    return runConfiguration.project.getProjectSystem() is GradleProjectSystem &&
      (runConfiguration is PreferGradleMake || isUnitTestConfiguration(runConfiguration))
  }

  fun configurationTypeIsEnabledByDefault(runConfiguration: RunConfiguration): Boolean = runConfiguration is PreferGradleMake

  override fun configureTask(runConfiguration: RunConfiguration, task: MakeBeforeRunTask): Boolean {
    val dialog = GradleEditTaskDialog(project)
    dialog.goal = task.goal
    dialog.setAvailableGoals(createAvailableTasks())
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

  private fun createAvailableTasks(): List<String> {
    val moduleManager = ModuleManager.getInstance(project)
    val gradleTasks: MutableList<String> = ArrayList()
    for (module in moduleManager.modules) {
      val facet = GradleFacet.getInstance(module) ?: continue
      val gradleModuleModel = facet.gradleModuleModel ?: continue
      gradleTasks.addAll(gradleModuleModel.taskNames)
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
  fun isSyncNeeded(abis: Collection<String>?): SyncNeeded {
    // If the project has native modules, and there are any un-synced variants.
    for (module in ModuleManager.getInstance(project).modules) {
      val ndkModel = get(module!!)
      val androidModel = GradleAndroidModel.get(module)
      if (ndkModel != null && androidModel != null) {
        val selectedVariantName = androidModel.selectedVariant.name
        val availableAbis = ndkModel.syncedVariantAbis
          .filter { it.variant == selectedVariantName }
          .map { it.abi }
          .toSet()
        if (!availableAbis.containsAll(abis!!)) {
          return SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED
        }
      }
    }
    return SyncNeeded.NOT_NEEDED
  }

  private fun runSyncIfNeeded(syncNeeded: SyncNeeded, requestedAbis: Set<String>) {
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
    val androidRunConfiguration = if (configuration is AndroidRunConfigurationBase) configuration else null
    if (!androidProjectInfo.requiresAndroidModel()) {
      val regularMake = CompileStepBeforeRun(project)
      return regularMake.executeTask(context, configuration, env, CompileStepBeforeRun.MakeBeforeRunTask())
    }

    // Note: this run task provider may be invoked from a context such as Java unit tests, in which case it doesn't have
    // the android run config context
    val deviceFutures = env.getCopyableUserData(DeviceFutures.KEY)
    val targetDevices = deviceFutures?.devices ?: emptyList()
    val targetDeviceSpec = createSpec(targetDevices)


    // Some configurations (e.g. native attach) don't require a build while running the configuration
    if (configuration is RunProfileWithCompileBeforeLaunchOption &&
      (configuration as RunProfileWithCompileBeforeLaunchOption).isExcludeCompileBeforeLaunchOption
    ) {
      return true
    }

    // Compute modules to build
    val modules = getModules(context, configuration)
    val cmdLineArgs =
      try {
        getCommonArguments(modules, androidRunConfiguration, targetDeviceSpec) + "--stacktrace"
      } catch (e: Exception) {
        log.warn("Error generating command line arguments for Gradle task", e)
        return false
      }
    val targetDeviceVersion = targetDeviceSpec?.commonVersion
    val buildResult = build(modules, configuration, targetDeviceVersion, task.goal, cmdLineArgs)
    if (androidRunConfiguration != null && buildResult != null) {
      val model = buildResult.invocationResult.models.firstOrNull()
      if (model is PostBuildProjectModels) {
        androidRunConfiguration.putUserData(GradleApkProvider.POST_BUILD_MODEL, PostBuildModel(model))
      } else {
        log.info("Couldn't get post build models.")
      }
    }
    log.info("Gradle invocation complete, build result = $buildResult")

    // If the model needs a sync, we need to sync "synchronously" before running.
    val targetAbis: Set<String> = targetDeviceSpec?.abis?.toSet().orEmpty()
    val syncNeeded = isSyncNeeded(targetAbis)
    runSyncIfNeeded(syncNeeded, targetAbis)
    return !project.isDisposed &&
      buildResult != null &&
      buildResult.isBuildSuccessful &&
      buildResult.invocationResult.invocations.isNotEmpty()
  }

  private fun getModules(context: DataContext, configuration: RunConfiguration): Array<Module> {
    return when (configuration) {
      // ModuleBasedConfiguration includes Android and JUnit run configurations, including "JUnit: Rerun Failed Tests",
      // which is AbstractRerunFailedTestsAction.MyRunProfile.
      is ModuleRunProfile -> configuration.modules
      else -> gradleProjectInfo.getModulesToBuildFromSelection(context)
    }
  }

  companion object {
    @JvmField
    val ID = Key.create<MakeBeforeRunTask>("Android.Gradle.BeforeRunTask")

    const val TASK_NAME = "Gradle-aware Make"

    private fun isUnitTestConfiguration(runConfiguration: RunConfiguration): Boolean {
      return runConfiguration is JUnitConfiguration || runConfiguration.javaClass.simpleName == "TestNGConfiguration"
    }

    private val log = Logger.getInstance(MakeBeforeRunTask::class.java)

    /**
     * Returns the list of arguments to Gradle that are common to both instant and non-instant builds.
     */
    @VisibleForTesting
    @Throws(IOException::class)
    fun getCommonArguments(
      modules: Array<Module>,
      configuration: AndroidRunConfigurationBase?,
      targetDeviceSpec: AndroidDeviceSpec?
    ): List<String> {
      val cmdLineArgs = mutableListOf<String>()
      // Always build with stable IDs to avoid push-to-device overhead.
      cmdLineArgs.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_WITH_STABLE_IDS, true))
      if (configuration != null) {
        cmdLineArgs.addAll(getDeviceSpecificArguments(modules, configuration, targetDeviceSpec))
        cmdLineArgs.addAll(getProfilingOptions(configuration, targetDeviceSpec))
      }
      return cmdLineArgs
    }

    @VisibleForTesting
    fun getDeviceSpecificArguments(
      modules: Array<Module>,
      configuration: AndroidRunConfigurationBase,
      deviceSpec: AndroidDeviceSpec?
    ): List<String> {
      if (deviceSpec == null) {
        return emptyList()
      }
      val properties = mutableListOf<String>()
      if (useSelectApksFromBundleBuilder(modules, configuration, deviceSpec.minVersion)) {
        // For the bundle tool, we create a temporary json file with the device spec and
        // pass the file path to the gradle task.
        val collectListOfLanguages = shouldCollectListOfLanguages(modules, configuration, deviceSpec.minVersion)
        val deviceSpecFile = deviceSpec.writeToJsonTempFile(collectListOfLanguages)
        properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_APK_SELECT_CONFIG, deviceSpecFile.absolutePath))
        if (configuration is AndroidRunConfiguration) {
          if (configuration.DEPLOY_AS_INSTANT) {
            properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_EXTRACT_INSTANT_APK, true))
          }
          if (configuration.DEPLOY_APK_FROM_BUNDLE) {
            val featureList = getEnabledDynamicFeatureList(modules, configuration)
            if (featureList.isNotEmpty()) {
              properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_INJECTED_DYNAMIC_MODULES_LIST, featureList))
            }
          }
        }
      } else {
        // For non bundle tool deploy tasks, we have one argument per device spec property
        val version = deviceSpec.commonVersion
        if (version != null) {
          properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_API, version.apiLevel.toString()))
          if (version.codename != null) {
            properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_API_CODENAME, version.codename!!))
          }
        }
        if (deviceSpec.density != null) {
          properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_DENSITY, deviceSpec.density!!.resourceValue))
        }
        if (deviceSpec.abis.isNotEmpty()) {
          properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_BUILD_ABI, Joiner.on(',').join(deviceSpec.abis)))
        }
        if (configuration is AndroidRunConfiguration) {
          if (configuration.DEPLOY_AS_INSTANT) {
            properties.add(AndroidGradleSettings.createProjectProperty(PROPERTY_DEPLOY_AS_INSTANT_APP, true))
          }
        }
      }
      return properties
    }

    private fun getEnabledDynamicFeatureList(
      modules: Array<Module>,
      configuration: AndroidRunConfiguration
    ): String {
      val disabledFeatures = configuration.disabledDynamicFeatures.toSet()
      return modules.asSequence()
        .flatMap { GradleProjectSystemUtil.getDependentFeatureModulesForBase(it).asSequence() }
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
      configuration: AndroidRunConfigurationBase,
      targetDeviceSpec: AndroidDeviceSpec?
    ): List<String> {
      if (targetDeviceSpec?.minVersion == null) {
        return emptyList()
      }

      // Find the minimum API version in case both a pre-O and post-O devices are selected.
      // TODO: if a post-O app happened to be transformed, the agent needs to account for that.
      val minFeatureLevel = targetDeviceSpec.minVersion!!.featureLevel
      val arguments = mutableListOf<String>()
      val state = configuration.profilerState
      if (state.ADVANCED_PROFILING_ENABLED && minFeatureLevel >= VersionCodes.LOLLIPOP && minFeatureLevel < VersionCodes.O) {
        val file = EmbeddedDistributionPaths.getInstance().findEmbeddedProfilerTransform()
        arguments.add(AndroidGradleSettings.createProjectProperty(ProfilerState.ANDROID_ADVANCED_PROFILING_TRANSFORMS, file.absolutePath))
        val profilerProperties = state.toProperties()
        val propertiesFile = FileUtil.createTempFile("profiler", ".properties")
        propertiesFile.deleteOnExit() // TODO: It'd be nice to clean this up sooner than at exit.
        val writer: Writer = OutputStreamWriter(FileOutputStream(propertiesFile), Charsets.UTF_8)
        profilerProperties.store(writer, "Android Studio Profiler Gradle Plugin Properties")
        writer.close()
        arguments.add(AndroidGradleSettings.createJvmArg("android.profiler.properties", propertiesFile.absolutePath))
      }
      return arguments
    }

    private fun build(
      modules: Array<Module>,
      configuration: RunConfiguration,
      targetDeviceVersion: AndroidVersion?,
      userGoal: String?,
      commandLineArgs: List<String>
    ): AssembleInvocationResult? {

      check(modules.isNotEmpty()) { "Unable to determine list of modules to build" }
      val project = configuration.project

      fun doBuild(tasks: Map<Path, Collection<String>>, buildMode: BuildMode): AssembleInvocationResult? {
        if (tasks.values.flatten().isEmpty()) {
          log.error("Unable to determine gradle tasks to execute")
          return null
        }
        return GradleTaskRunner.run(project, modules, tasks, buildMode, commandLineArgs)
      }

      if (!userGoal.isNullOrEmpty()) {
        val tasks: Map<Path, List<String>> =
          modules
            .map { ExternalSystemApiUtil.getExternalRootProjectPath(it) }
            .distinct()
            .associate { Paths.get(it) to listOf(userGoal) }

        return doBuild(tasks, BuildMode.DEFAULT_BUILD_MODE)
      }
      val gradleTasksProvider = GradleModuleTasksProvider(modules)
      val testCompileType = when {
        configuration is PreferGradleMake -> configuration.testCompileMode
        AndroidBuildCommonUtils.isTestConfiguration(configuration.type.id) -> TestCompileType.UNIT_TESTS
        else -> TestCompileType.NONE
      }
      return when {
        testCompileType === TestCompileType.UNIT_TESTS ->
          doBuild(gradleTasksProvider.getUnitTestTasks(BuildMode.COMPILE_JAVA), BuildMode.COMPILE_JAVA)
        // Use the "select apks from bundle" task if using a "AndroidRunConfigurationBase".
        // Note: This is very ad-hoc, and it would be nice to have a better abstraction for this special case.

        // NOTE: MakeBeforeRunTask is configured on unit-test and AndroidrunConfigurationBase run configurations only. Therefore,
        //       since testCompileType != TestCompileType.UNIT_TESTS it is safe to assume that configuration is
        //       AndroidRunConfigurationBase.
        configuration is AndroidRunConfigurationBase && useSelectApksFromBundleBuilder(modules, configuration, targetDeviceVersion) ->
          doBuild(gradleTasksProvider.getTasksFor(BuildMode.APK_FROM_BUNDLE, testCompileType), BuildMode.APK_FROM_BUNDLE)
        else ->
          doBuild(gradleTasksProvider.getTasksFor(BuildMode.ASSEMBLE, testCompileType), BuildMode.ASSEMBLE)
      }
    }

    private fun useSelectApksFromBundleBuilder(
      modules: Array<Module>,
      configuration: AndroidRunConfigurationBase,
      minTargetDeviceVersion: AndroidVersion?
    ): Boolean {
      return modules.any { DynamicAppUtils.useSelectApksFromBundleBuilder(it, configuration, minTargetDeviceVersion) }
    }

    private fun shouldCollectListOfLanguages(
      modules: Array<Module>,
      configuration: AndroidRunConfigurationBase,
      targetDeviceVersion: AndroidVersion?
    ): Boolean {
      // We should collect the list of languages only if *all* devices are verify the condition, otherwise we would
      // end up deploying language split APKs to devices that don't support them.
      return modules.all { DynamicAppUtils.shouldCollectListOfLanguages(it, configuration, targetDeviceVersion) }
    }
  }
}