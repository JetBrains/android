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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IDevice
import com.android.tools.deployer.AdbClient
import com.android.tools.deployer.AdbInstaller
import com.android.tools.deployer.Deployer
import com.android.tools.deployer.DeployerException
import com.android.tools.deployer.DeployerOption
import com.android.tools.deployer.InstallOptions
import com.android.tools.deployer.MetricsRecorder
import com.android.tools.deployer.model.App
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.DeploymentService
import com.android.tools.idea.run.IdeService
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.util.StudioPathManager
import com.intellij.execution.ExecutionException
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.android.util.AndroidBundle
import java.io.File

/**
 * Installs app corresponded to given [AndroidWearConfiguration].
 */
class ApplicationInstaller(private val configuration: AndroidWearConfiguration) {
  private val project = configuration.project
  private val LOG = Logger.getInstance(this::class.java)
  private val appId = project.getProjectSystem().getApplicationIdProvider(configuration)?.packageName ?: throw RuntimeException(
    "Cannot get ApplicationIdProvider")

  fun installAppOnDevice(device: IDevice, indicator: ProgressIndicator, console: ConsoleView): App {
    indicator.text = "Installing app"
    val deployer = getDeployer(device) { console.print(it, ConsoleViewContentType.NORMAL_OUTPUT) }
    val apkInfo = getApkInfo(device)
    val pathsToInstall = apkInfo.files.map { it.apkFile.path }

    try {
      val result = deployer.install(appId, pathsToInstall, getInstallOptions(device, appId), Deployer.InstallMode.DELTA)
      if (result.skippedInstall) {
        console.print("App restart successful without requiring a re-install.", ConsoleViewContentType.NORMAL_OUTPUT)
      }
      return result.app
    }
    catch (e: DeployerException) {
      throw ExecutionException("Failed to install app. ${e.message ?: ""}", e.cause)
    }
  }

  private fun getDeployer(device: IDevice, commandPrinter: (String) -> Unit): Deployer {
    val logger = object : LogWrapper(LOG) {
      override fun info(msgFormat: String, vararg args: Any?) { // print to user console commands that we run on device
        if (msgFormat.contains("$ adb")) {
          commandPrinter(msgFormat + "\n")
        }
        super.info(msgFormat, *args)
      }
    }
    val adb = AdbClient(device, logger)
    val service = DeploymentService.getInstance(project)

    val option = DeployerOption.Builder().setUseOptimisticSwap(
      StudioFlags.APPLY_CHANGES_OPTIMISTIC_SWAP.get()).setUseOptimisticResourceSwap(
      StudioFlags.APPLY_CHANGES_OPTIMISTIC_RESOURCE_SWAP.get()).setUseStructuralRedefinition(
      StudioFlags.APPLY_CHANGES_STRUCTURAL_DEFINITION.get()).setUseVariableReinitialization(
      StudioFlags.APPLY_CHANGES_VARIABLE_REINITIALIZATION.get()).enableCoroutineDebugger(
      StudioFlags.COROUTINE_DEBUGGER_ENABLE.get()).build()

    // Collection that will accumulate metrics for the deployment.
    val metrics = MetricsRecorder()

    val installer = AdbInstaller(getLocalInstaller(), adb, metrics.deployMetrics, logger, AdbInstaller.Mode.DAEMON)
    return Deployer(adb, service.deploymentCacheDatabase, service.dexDatabase, service.taskRunner, installer, IdeService(project), metrics,
                    logger, option)
  }

  private fun getLocalInstaller(): String? {
    val path = if (StudioPathManager.isRunningFromSources()) { // Development mode
      File(StudioPathManager.getSourcesRoot(), "bazel-bin/tools/base/deploy/installer/android-installer")
    }
    else {
      File(PathManager.getHomePath(), "plugins/android/resources/installer")
    }
    return path.absolutePath
  }


  private fun getInstallOptions(device: IDevice, appId: String): InstallOptions { // All installations default to allow debuggable APKs
    val options = InstallOptions.builder().setAllowDebuggable()

    // Embedded devices (Android Things) have all runtime permissions granted since there's no requirement for user
    // interaction/display. However, regular installation will not grant some permissions until the next device reboot.
    // Installing with "-g" guarantees that the permissions are properly granted at install time.
    if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      options.setGrantAllPermissions()
    }

    // Skip verification if possible.
    options.setSkipVerification(device, appId)

    if (!configuration.installFlags.isEmpty()) {
      options.setUserInstallOptions(configuration.installFlags.trim().split("\\s+").toTypedArray())
    }

    return options.build()
  }

  private fun getApkInfo(device: IDevice): ApkInfo {
    val apkProvider = project.getProjectSystem().getApkProvider(configuration) ?: throw ExecutionException(
      AndroidBundle.message("android.run.configuration.not.supported",
                            configuration.name)) // There is no test ApkInfo for AndroidWatchFaceConfiguration, thus it should be always single ApkInfo. Only App.
    return apkProvider.getApks(device).single()
  }
}

