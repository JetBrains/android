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

import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.WearSurfaceLaunchOptions
import com.android.tools.idea.projectsystem.getAndroidModulesForDisplay
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.LaunchableAndroidDevice
import com.android.tools.idea.run.PreferGradleMake
import com.android.tools.idea.run.configuration.editors.AndroidWearConfigurationEditor
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutorRunProfileState
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider
import com.android.tools.idea.run.editor.AndroidDebuggerContext
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.RunConfigurationWithDebugger
import com.android.tools.idea.stats.RunStats
import com.android.tools.idea.stats.RunStatsService
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

  override val androidDebuggerContext: AndroidDebuggerContext = AndroidDebuggerContext(AndroidJavaDebugger.ID)

  abstract val componentLaunchOptions: WearSurfaceLaunchOptions

  val deployOptions: DeployOptions = DeployOptions(emptyList(), "", installOnAllUsers = true, alwaysInstallWithPm = true)

  override fun getConfigurationEditor(): AndroidWearConfigurationEditor<*> = AndroidWearConfigurationEditor(project, this)
  override fun checkConfiguration() {
    configurationModule.checkForWarning()
    // If module is null `configurationModule.checkForWarning()` will throw an error
    val module = configurationModule.module!!
    AndroidFacet.getInstance(module) ?: throw RuntimeConfigurationError(AndroidBundle.message("no.facet.error", module.name))
    if (project.getProjectSystem().getSyncManager().isSyncInProgress()) {
      throw RuntimeConfigurationError("Project is synchronizing")
    }
    componentLaunchOptions.componentName ?: throw RuntimeConfigurationError(
      "${componentLaunchOptions.userVisibleComponentTypeName} is not chosen")
  }

  final override fun getState(executor: Executor, environment: ExecutionEnvironment): AndroidConfigurationExecutorRunProfileState {
    return AndroidConfigurationExecutorRunProfileState(getExecutor(environment))
  }

  private fun getExecutor(environment: ExecutionEnvironment): AndroidConfigurationExecutor {
    val provider = DeviceAndSnapshotComboBoxTargetProvider()
    val deployTarget = if (provider.requiresRuntimePrompt(project)) {
      invokeAndWaitIfNeeded { provider.showPrompt(project) }
    }
                       else {
      provider.getDeployTarget(project)
    } ?: throw ExecutionException(AndroidBundle.message("deployment.target.not.found"))

    fillStatsForEnvironment(environment, deployTarget)

    val stats = RunStats.from(environment)
    return try {
      stats.start()
      val apkProvider = project.getProjectSystem().getApkProvider(this) ?: throw ExecutionException(
        AndroidBundle.message("android.run.configuration.not.supported",
                              name)) // There is no test ApkInfo for AndroidWearConfiguration, thus it should be always single ApkInfo. Only App.

      val applicationIdProvider = project.getProjectSystem().getApplicationIdProvider(this) ?: throw RuntimeException(
        "Cannot get ApplicationIdProvider")

      val appRunSettings = object : AppRunSettings {
        override val deployOptions = this@AndroidWearConfiguration.deployOptions
        override val componentLaunchOptions = this@AndroidWearConfiguration.componentLaunchOptions
        override val module = this@AndroidWearConfiguration.module
      }
      val state = getExecutor(environment, deployTarget, appRunSettings, applicationIdProvider, apkProvider)
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
    deployTarget: DeployTarget,
    appRunSettings: AppRunSettings,
    applicationIdProvider: ApplicationIdProvider,
    apkProvider: ApkProvider
  ): AndroidConfigurationExecutor

  private fun fillStatsForEnvironment(environment: ExecutionEnvironment, deployTarget: DeployTarget) {
    val stats = RunStatsService.get(project).create()
    stats.setDebuggable(module!!.getModuleSystem().isDebuggable)
    stats.setExecutor(environment.executor.id)
    val appId = project.getProjectSystem().getApplicationIdProvider(this)?.packageName
                ?: throw RuntimeException("Cannot get ApplicationIdProvider")
    stats.setPackage(appId)
    stats.setAppComponentType(componentLaunchOptions.componentType)

    // Save the stats so that before-run task can access it
    environment.putUserData(RunStats.KEY, stats)

    val deviceFutureList = deployTarget.getDevices(project) ?: throw ExecutionException(
      AndroidBundle.message("deployment.target.not.found"))

    // Record stat if we launched a device.
    stats.setLaunchedDevices(deviceFutureList.devices.any { it is LaunchableAndroidDevice })
    // Store the chosen target on the execution environment so before-run tasks can access it.
    environment.putCopyableUserData(DeviceFutures.KEY, deviceFutureList)
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