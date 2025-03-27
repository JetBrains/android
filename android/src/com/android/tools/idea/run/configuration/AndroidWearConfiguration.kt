/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.AndroidConfigurationExecutorRunProfileState
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.execution.common.WearSurfaceLaunchOptions
import com.android.tools.idea.execution.common.debug.AndroidDebuggerContext
import com.android.tools.idea.execution.common.debug.RunConfigurationWithDebugger
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.RunStatsService
import com.android.tools.idea.project.FacetBasedApplicationProjectContext
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.getAndroidModulesForDisplay
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.PreferGradleMake
import com.android.tools.idea.run.configuration.editors.AndroidWearConfigurationEditor
import com.android.tools.idea.run.configuration.execution.ApplicationDeployerImpl
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.JavaRunConfigurationModule
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

abstract class AndroidWearConfiguration(project: Project, factory: ConfigurationFactory) :
  ModuleBasedConfiguration<JavaRunConfigurationModule, Element>(JavaRunConfigurationModule(project, false), factory),
  RunConfigurationWithSuppressedDefaultRunAction, RunConfigurationWithSuppressedDefaultDebugAction, PreferGradleMake, RunConfigurationWithDebugger {

  companion object {
    const val LAUNCH_OPTIONS_ELEMENT_NAME = "LaunchOptions"
    const val DEPLOY_OPTIONS_ELEMENT_NAME = "DeployOptions"
  }

  override val androidDebuggerContext: AndroidDebuggerContext = AndroidDebuggerContext(
    AndroidJavaDebugger.ID)

  abstract val componentLaunchOptions: WearSurfaceLaunchOptions

  val deployOptions: DeployOptions = DeployOptions(emptyList(), "", installOnAllUsers = true, alwaysInstallWithPm = true, allowAssumeVerified = false)

  init {
    putUserData(DeployableToDevice.KEY, true)
  }

  override fun getConfigurationEditor(): AndroidWearConfigurationEditor<*> = AndroidWearConfigurationEditor(project, this)

  @WorkerThread
  override fun checkConfiguration() {
    configurationModule.checkForWarning()
    // If module is null `configurationModule.checkForWarning()` will throw an error
    getAndroidFacetOrThrow()
    if (project.getProjectSystem().getSyncManager().isSyncInProgress()) {
      throw RuntimeConfigurationError("Project is synchronizing")
    }
    componentLaunchOptions.componentName ?: throw RuntimeConfigurationError(
      "${componentLaunchOptions.userVisibleComponentTypeName} is not chosen")
  }

  private fun getAndroidFacetOrThrow(): AndroidFacet {
    val module = configurationModule.module!!
    return AndroidFacet.getInstance(module) ?: throw RuntimeConfigurationError(AndroidBundle.message("no.facet.error", module.name))
  }

  final override fun getState(executor: Executor, environment: ExecutionEnvironment): AndroidConfigurationExecutorRunProfileState {
    return AndroidConfigurationExecutorRunProfileState(getExecutor(environment))
  }

  private fun getExecutor(environment: ExecutionEnvironment): AndroidConfigurationExecutor {
    val provider = DeviceAndSnapshotComboBoxTargetProvider.getInstance()
    val deployTarget = if (provider.requiresRuntimePrompt(project)) {
      invokeAndWaitIfNeeded { provider.showPrompt(project) }
    } else {
      provider.getDeployTarget(project)
    } ?: throw ExecutionException(AndroidBundle.message("deployment.target.not.found"))
    val deviceFutures = deployTarget.launchDevices(project)

    fillStatsForEnvironment(environment, deviceFutures)

    val stats = RunStats.from(environment)
    return try {
      stats.start()
      val apkProvider = project.getProjectSystem().getApkProvider(this) ?: throw ExecutionException(
        AndroidBundle.message("android.run.configuration.not.supported",
                              name)) // There is no test ApkInfo for AndroidWearConfiguration, thus it should be always single ApkInfo. Only App.

      val applicationIdProvider = project.getProjectSystem().getApplicationIdProvider(this) ?: throw RuntimeException(
        "Cannot get ApplicationIdProvider")
      val applicationContext = FacetBasedApplicationProjectContext(applicationIdProvider.packageName, getAndroidFacetOrThrow());

      val appRunSettings = object : AppRunSettings {
        override val deployOptions = this@AndroidWearConfiguration.deployOptions
        override val componentLaunchOptions = this@AndroidWearConfiguration.componentLaunchOptions
        override val module = this@AndroidWearConfiguration.module
      }
      val deployer = ApplicationDeployerImpl(project, stats)
      val state = getExecutor(
        environment, deviceFutures, appRunSettings, apkProvider,
        applicationContext,
        deployer
      )
      stats.markStateCreated()
      state
    }
    catch (t: Throwable) {
      stats.abort()
      throw t
    }
  }

  abstract fun getExecutor(
    environment: ExecutionEnvironment,
    deviceFutures: DeviceFutures,
    appRunSettings: AppRunSettings,
    apkProvider: ApkProvider,
    applicationContext: ApplicationProjectContext,
    deployer: ApplicationDeployer
  ): AndroidConfigurationExecutor

  private fun fillStatsForEnvironment(environment: ExecutionEnvironment, deviceFutures: DeviceFutures) {
    val stats = RunStatsService.get(project).create()
    stats.setDebuggable(module!!.getModuleSystem().isDebuggable)
    stats.setExecutor(environment.executor.id)
    stats.setAppComponentType(componentLaunchOptions.componentType)

    // Save the stats so that before-run task can access it
    environment.putUserData(RunStats.KEY, stats)

    // Record stat if we launched a device.
    stats.setLaunchedDevices(deviceFutures.devices.any { !it.isRunning })
    // Store the chosen target on the execution environment so before-run tasks can access it.
    environment.putCopyableUserData(DeviceFutures.KEY, deviceFutures)
  }

  override fun writeExternal(element: Element) {
    super<ModuleBasedConfiguration>.writeExternal(element)
    XmlSerializer.serializeInto(this, element)

    Element(LAUNCH_OPTIONS_ELEMENT_NAME).apply {
      element.addContent(this)
      XmlSerializer.serializeInto(componentLaunchOptions, this)
    }

    Element(DEPLOY_OPTIONS_ELEMENT_NAME).apply {
      element.addContent(this)
      XmlSerializer.serializeInto(deployOptions, this)
    }
  }

  override fun readExternal(element: Element) {
    super<ModuleBasedConfiguration>.readExternal(element)
    XmlSerializer.deserializeInto(this, element)

    element.getChild(LAUNCH_OPTIONS_ELEMENT_NAME)?.let { XmlSerializer.deserializeInto(componentLaunchOptions, it) }
    element.getChild(DEPLOY_OPTIONS_ELEMENT_NAME)?.let { XmlSerializer.deserializeInto(deployOptions, it) }
  }

  override fun getValidModules() = project.getAndroidModulesForDisplay()

  val module: Module?
    get() = configurationModule.module

}