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
import com.android.tools.idea.run.DeploymentService
import com.android.tools.idea.run.IdeService
import com.android.tools.idea.util.StudioPathManager
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

interface ApplicationInstaller {
  fun installAppOnDevice(device: IDevice,
                         appId: String,
                         apksPaths: List<String>,
                         installFlags: String,
                         infoReceiver: (String) -> Unit): App
}

class ApplicationInstallerImpl(private val project: Project) : ApplicationInstaller {
  private val LOG = Logger.getInstance(this::class.java)

  override fun installAppOnDevice(device: IDevice,
                                  appId: String,
                                  apksPaths: List<String>,
                                  installFlags: String,
                                  infoReceiver: (String) -> Unit): App {
    val deployer = getDeployer(device, infoReceiver)

    try {
      val result = deployer.install(appId, apksPaths, getInstallOptions(device, appId, installFlags), Deployer.InstallMode.DELTA)
      if (result.skippedInstall) {
        infoReceiver("App restart successful without requiring a re-install.")
      }
      return result.app
    }
    catch (e: DeployerException) {
      throw ExecutionException("Failed to install app '$appId'. ${e.details ?: ""}", e)
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
      StudioPathManager.resolvePathFromSourcesRoot("bazel-bin/tools/base/deploy/installer/android-installer").toFile()
    }
    else {
      File(PathManager.getHomePath(), "plugins/android/resources/installer")
    }
    return path.absolutePath
  }


  private fun getInstallOptions(device: IDevice,
                                appId: String,
                                installFlags: String): InstallOptions { // All installations default to allow debuggable APKs
    val options = InstallOptions.builder().setAllowDebuggable()

    // Embedded devices (Android Things) have all runtime permissions granted since there's no requirement for user
    // interaction/display. However, regular installation will not grant some permissions until the next device reboot.
    // Installing with "-g" guarantees that the permissions are properly granted at install time.
    if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      options.setGrantAllPermissions()
    }

    // Skip verification if possible.
    options.setSkipVerification(device, appId)

    if (installFlags.isNotEmpty()) {
      options.setUserInstallOptions(installFlags.trim().split("\\s+").toTypedArray())
    }

    return options.build()
  }
}

