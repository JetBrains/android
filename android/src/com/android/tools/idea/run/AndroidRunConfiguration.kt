/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.AndroidProjectTypes
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.AppRunConfiguration
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.run.activity.DefaultStartActivityFlagsProvider
import com.android.tools.idea.run.activity.InstantAppStartActivityFlagsProvider
import com.android.tools.idea.run.activity.launch.ActivityLaunchOptionState
import com.android.tools.idea.run.activity.launch.DeepLinkLaunch
import com.android.tools.idea.run.activity.launch.DefaultActivityLaunch
import com.android.tools.idea.run.activity.launch.NoLaunch
import com.android.tools.idea.run.activity.launch.SpecificActivityLaunch
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor
import com.android.tools.idea.run.editor.ApplicationRunParameters
import com.android.tools.idea.run.editor.DeployTargetProvider
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.ui.BaseAction
import com.android.tools.idea.stats.RunStats
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.Executor
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.RunnerIconProvider
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RefactoringListenerProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.junit.RefactoringListeners
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.DefaultJDOMExternalizer
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.util.concurrency.AppExecutorUtil
import org.jdom.Element
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.NonNls
import java.util.stream.Collectors
import javax.swing.Icon

/**
 * Run Configuration used for running Android Apps (and Instant Apps) locally on a device/emulator.
 */
open class AndroidRunConfiguration(project: Project?, factory: ConfigurationFactory?) :
  AndroidRunConfigurationBase(project, factory, false), RefactoringListenerProvider, RunnerIconProvider, AppRunConfiguration {
  private val myLaunchOptionStates: MutableMap<String, ActivityLaunchOptionState> = Maps.newHashMap()

  // Deploy options
  @JvmField
  var DEPLOY = true

  @JvmField
  var DEPLOY_APK_FROM_BUNDLE = false

  @JvmField
  var DEPLOY_AS_INSTANT = false

  @JvmField
  var ARTIFACT_NAME = ""

  @JvmField
  var PM_INSTALL_OPTIONS = ""

  @JvmField
  var ALL_USERS = false

  @JvmField
  var ALWAYS_INSTALL_WITH_PM = false

  @JvmField
  var CLEAR_APP_STORAGE = false
  var DYNAMIC_FEATURES_DISABLED_LIST = ""

  // Launch options
  @JvmField
  var ACTIVITY_EXTRA_FLAGS = ""

  @JvmField
  var MODE = LAUNCH_DEFAULT_ACTIVITY

  init {
    for (option in LAUNCH_OPTIONS) {
      myLaunchOptionStates[option.id] = option.createState()
    }
    putUserData(BaseAction.SHOW_APPLY_CHANGES_UI, true)
  }

  override fun getApplicableDeployTargetProviders(): List<DeployTargetProvider> {
    return deployTargetContext.getApplicableDeployTargetProviders(false)
  }

  @Throws(ExecutionException::class)
  override fun getExecutor(env: ExecutionEnvironment, facet: AndroidFacet, deployFutures: DeviceFutures): AndroidConfigurationExecutor {
    val applicationIdProvider = applicationIdProvider ?: throw RuntimeException("Cannot get ApplicationIdProvider")
    val apkProvider = apkProvider ?: throw RuntimeException("Cannot get ApkProvider")
    return LaunchTaskRunner(applicationIdProvider, env, deployFutures, apkProvider)
  }

  override fun supportsRunningLibraryProjects(facet: AndroidFacet): Pair<Boolean, String?> {
    return Pair.create(java.lang.Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"))
  }

  public override fun checkConfiguration(facet: AndroidFacet): List<ValidationError> {
    val activityLaunchOptionState = getLaunchOptionState(MODE)
    return activityLaunchOptionState.checkConfiguration(facet)
  }

  public override fun getLaunchOptions(): LaunchOptions.Builder {
    return super.getLaunchOptions()
      .setDeploy(DEPLOY)
      .setPmInstallOptions { PM_INSTALL_OPTIONS }
      .setAllUsers(ALL_USERS)
      .setDisabledDynamicFeatures(disabledDynamicFeatures)
      .setOpenLogcatAutomatically(SHOW_LOGCAT_AUTOMATICALLY)
      .setDeployAsInstant(DEPLOY_AS_INSTANT)
      .setAlwaysInstallWithPm(ALWAYS_INSTALL_WITH_PM)
      .setClearAppStorage(CLEAR_APP_STORAGE)
  }

  var disabledDynamicFeatures: List<String>
    get() = if (StringUtil.isEmpty(DYNAMIC_FEATURES_DISABLED_LIST)) {
      ImmutableList.of()
    }
    else StringUtil.split(
      DYNAMIC_FEATURES_DISABLED_LIST,
      FEATURE_LIST_SEPARATOR
    )
    set(features) {
      // Remove duplicates and sort to ensure deterministic behavior, as the value
      // is stored on disk (run configuration parameters).
      val sortedFeatures = features.stream().distinct().sorted().collect(Collectors.toList())
      DYNAMIC_FEATURES_DISABLED_LIST = StringUtil.join(sortedFeatures, FEATURE_LIST_SEPARATOR)
    }

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
    return AndroidRunConfigurationEditor(
      project,
      Predicate { module: Module? ->
        if (module == null) return@Predicate false
        val facet = AndroidFacet.getInstance(module) ?: return@Predicate false
        val moduleSystem = facet.getModuleSystem()
        when (moduleSystem.type) {
          AndroidModuleSystem.Type.TYPE_APP, AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE -> return@Predicate module.isMainModule()
          AndroidModuleSystem.Type.TYPE_ATOM, AndroidModuleSystem.Type.TYPE_FEATURE, AndroidModuleSystem.Type.TYPE_INSTANTAPP -> return@Predicate false // Legacy not-supported module types.
          AndroidModuleSystem.Type.TYPE_NON_ANDROID -> return@Predicate false
          AndroidModuleSystem.Type.TYPE_LIBRARY, AndroidModuleSystem.Type.TYPE_TEST -> return@Predicate false // Supported via AndroidTestRunConfiguration.
        }
      },
      this,
      true,
      false
    ) { moduleSelector: ConfigurationModuleSelector -> ApplicationRunParameters(project, moduleSelector) }
  }

  override fun getRefactoringElementListener(element: PsiElement): RefactoringElementListener? {
    // TODO: This is a bit of a hack: Currently, refactoring only affects the specific activity launch, so we directly peek into it and
    // change its state. The correct way of implementing this would be to delegate to all of the LaunchOptions and put the results into
    // a RefactoringElementListenerComposite
    val state = (getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY) as SpecificActivityLaunch.State)
    return RefactoringListeners.getClassOrPackageListener(element, object : RefactoringListeners.Accessor<PsiClass?> {
      override fun setName(qualifiedName: String) {
        state.ACTIVITY_CLASS = qualifiedName
      }

      override fun getPsiElement(): PsiClass? {
        return configurationModule.findClass(state.ACTIVITY_CLASS)
      }

      override fun setPsiElement(psiClass: PsiClass?) {
        state.ACTIVITY_CLASS = JavaExecutionUtil.getRuntimeQualifiedName(psiClass!!)!!
      }
    })
  }

  @Throws(ExecutionException::class)
  open fun getApplicationLaunchTask(
    applicationIdProvider: ApplicationIdProvider,
    facet: AndroidFacet,
    contributorsAmStartOptions: String,
    waitForDebugger: Boolean,
    apkProvider: ApkProvider,
    device: IDevice
  ): AppLaunchTask? {
    val state = getLaunchOptionState(MODE)
    var extraFlags = ACTIVITY_EXTRA_FLAGS
    if (contributorsAmStartOptions.isNotEmpty()) {
      extraFlags += (if (extraFlags.isEmpty()) "" else " ") + contributorsAmStartOptions
    }

    // Default Activity behavior has changed to not show the splashscreen in Tiramisu. We need to add the splashscreen back.
    if (device.version.isGreaterOrEqualThan(AndroidVersion.VersionCodes.TIRAMISU)) {
      extraFlags += (if (extraFlags.isEmpty()) "" else " ") + "--splashscreen-show-icon"
    }
    val startActivityFlagsProvider = if (facet.configuration.projectType == AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP) {
      InstantAppStartActivityFlagsProvider()
    }
    else {
      DefaultStartActivityFlagsProvider(
        project,
        waitForDebugger,
        extraFlags
      )
    }
    return try {
      state.getLaunchTask(
        applicationIdProvider.packageName, facet, startActivityFlagsProvider, profilerState,
        apkProvider
      )
    }
    catch (e: ApkProvisionException) {
      throw ExecutionException("Unable to identify application id :$e")
    }
  }

  /**
   * Configures the [SpecificActivityLaunch.State] and sets the [.MODE] to [.LAUNCH_SPECIFIC_ACTIVITY].
   *
   * @param activityName                Name of the activity to be launched.
   * @param searchActivityInGlobalScope Whether the activity should be searched in the global scope, as opposed to the project scope. Please
   * note that setting it to `true` might result in a slower search, so prefer using `false`
   * if the activity is located inside the project.
   */
  fun setLaunchActivity(activityName: String, searchActivityInGlobalScope: Boolean) {
    MODE = LAUNCH_SPECIFIC_ACTIVITY

    // TODO: we probably need a better way to do this rather than peeking into the option state
    // Possibly something like setLaunch(LAUNCH_SPECIFIC_ACTIVITY, SpecificLaunchActivity.state(className))
    val state = getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY)
    assert(state is SpecificActivityLaunch.State)
    val specificActivityLaunchState = state as SpecificActivityLaunch.State
    specificActivityLaunchState.ACTIVITY_CLASS = activityName
    specificActivityLaunchState.SEARCH_ACTIVITY_IN_GLOBAL_SCOPE = searchActivityInGlobalScope
  }

  fun setLaunchActivity(activityName: String) {
    setLaunchActivity(activityName, false)
  }

  fun setLaunchUrl(url: String) {
    MODE = LAUNCH_DEEP_LINK
    val state = getLaunchOptionState(LAUNCH_DEEP_LINK)
    assert(state is DeepLinkLaunch.State)
    (state as DeepLinkLaunch.State).DEEP_LINK = url
  }

  fun isLaunchingActivity(activityName: String?): Boolean {
    if (!StringUtil.equals(MODE, LAUNCH_SPECIFIC_ACTIVITY)) {
      return false
    }

    // TODO: we probably need a better way to do this rather than peeking into the option state, possibly just delegate equals to the option
    val state = getLaunchOptionState(LAUNCH_SPECIFIC_ACTIVITY)
    assert(state is SpecificActivityLaunch.State)
    return StringUtil.equals((state as SpecificActivityLaunch.State).ACTIVITY_CLASS, activityName)
  }

  fun getLaunchOptionState(launchOptionId: String): ActivityLaunchOptionState {
    return myLaunchOptionStates[launchOptionId]!!
  }

  @Throws(InvalidDataException::class)
  override fun readExternal(element: Element) {
    super<AndroidRunConfigurationBase>.readExternal(element)
    for (state in myLaunchOptionStates.values) {
      DefaultJDOMExternalizer.readExternal(state, element)
    }

    // Ensure invariant in case persisted state is manually edited or corrupted for some reason
    if (DEPLOY_APK_FROM_BUNDLE) {
      DEPLOY = true
    }
  }

  @Throws(WriteExternalException::class)
  override fun writeExternal(element: Element) {
    super<AndroidRunConfigurationBase>.writeExternal(element)
    for (state in myLaunchOptionStates.values) {
      DefaultJDOMExternalizer.writeExternal(state, element)
    }
  }

  // if null platform creates icon without "live" indicator is it's applicable. See [ExecutorRegistryImpl.ExecutorAction.getInformativeIcon]
  private var runExecutorIcon: Icon? = null

  override fun getExecutorIcon(configuration: RunConfiguration, executor: Executor): Icon? {
    if (DefaultRunExecutor.EXECUTOR_ID != executor.id) {
      return null
    }
    updateRunExecutorIconAsync()
    return runExecutorIcon
  }

  private fun updateRunExecutorIconAsync() {
    // Customize the executor icon for the DeviceAndSnapshotComboBoxAction: show "restart" icon instead of "run" icon if the app is already
    // running (even if it is started not from the IDE)
    val project = this.project
    val executionTarget = ExecutionTargetManager.getInstance(project).activeTarget
    if (executionTarget !is AndroidExecutionTarget) {
      runExecutorIcon = null
      return
    }
    var applicationId: String? = null
    try {
      applicationId = applicationIdProvider?.packageName
    }
    catch (ignored: ApkProvisionException) {
    }
    if (applicationId == null) {
      runExecutorIcon = null
      return
    }
    executionTarget.isApplicationRunningAsync(applicationId).transform(AppExecutorUtil.getAppExecutorService()) { isRunning ->
      runExecutorIcon = if (isRunning) {
        // Use the system's restart icon for the default run executor if application running on selected target.
        AllIcons.Actions.Restart
      }
      else {
        null
      }
    }
  }

  override fun updateExtraRunStats(runStats: RunStats) {
    runStats.setAppComponentType(ComponentType.ACTIVITY)
    runStats.setDeployedAsInstant(DEPLOY_AS_INSTANT)
    runStats.setDeployedFromBundle(DEPLOY_APK_FROM_BUNDLE)
  }

  override val appId: String?
    get() = try {
      // Provider could be null if module set to null.
      applicationIdProvider?.packageName
    }
    catch (e: ApkProvisionException) {
      Logger.getInstance(AndroidRunConfiguration::class.java).error(e)
      null
    }

  companion object {
    @NonNls
    const val LAUNCH_DEFAULT_ACTIVITY = "default_activity"

    @NonNls
    const val LAUNCH_SPECIFIC_ACTIVITY = "specific_activity"

    @NonNls
    const val DO_NOTHING = "do_nothing"

    @NonNls
    const val LAUNCH_DEEP_LINK = "launch_deep_link"

    @JvmField
    val LAUNCH_OPTIONS =
      listOf(NoLaunch.INSTANCE, DefaultActivityLaunch.INSTANCE, SpecificActivityLaunch.INSTANCE, DeepLinkLaunch.INSTANCE)

    @NonNls
    private val FEATURE_LIST_SEPARATOR = ","
  }
}