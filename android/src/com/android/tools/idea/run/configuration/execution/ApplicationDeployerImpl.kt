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
import com.android.tools.deployer.Deployer
import com.android.tools.idea.deploy.DeploymentConfiguration
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.track
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.ApkFileUnit
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.tasks.AbstractDeployTask
import com.android.tools.idea.run.tasks.ApplyChangesTask
import com.android.tools.idea.run.tasks.ApplyCodeChangesTask
import com.android.tools.idea.run.tasks.DeployTask
import com.google.wireless.android.sdk.stats.ArtifactDetail
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable


class ApplicationDeployerImpl(private val project: Project, private val stats: RunStats) : ApplicationDeployer {
  private val LOG = Logger.getInstance(this::class.java)
  private val installPathProvider: Computable<String> = Computable { EmbeddedDistributionPaths.getInstance().findEmbeddedInstaller() }

  override fun fullDeploy(device: IDevice, app: ApkInfo, deployOptions: DeployOptions, indicator: ProgressIndicator): Deployer.Result {
    LOG.info("Full deploy on $device")
    // Add packages to the deployment,
    val deployTask = DeployTask(
      project,
      listOf(filterDisabledFeatures(app, deployOptions.disabledDynamicFeatures)),
      deployOptions.pmInstallFlags,
      deployOptions.installOnAllUsers,
      deployOptions.alwaysInstallWithPm,
      installPathProvider
    )

    return runDeployTask(app, deployTask, device, indicator)
  }

  override fun applyChangesDeploy(device: IDevice,
                                  app: ApkInfo,
                                  deployOptions: DeployOptions,
                                  indicator: ProgressIndicator): Deployer.Result {
    LOG.info("Apply Changes on $device")
    val deployTask = ApplyChangesTask(
      project,
      listOf(filterDisabledFeatures(app, deployOptions.disabledDynamicFeatures)),
      DeploymentConfiguration.getInstance().APPLY_CHANGES_FALLBACK_TO_RUN,
      deployOptions.alwaysInstallWithPm,
      installPathProvider
    )

    return runDeployTask(app, deployTask, device, indicator)
  }

  override fun applyCodeChangesDeploy(device: IDevice,
                                      app: ApkInfo,
                                      deployOptions: DeployOptions,
                                      indicator: ProgressIndicator): Deployer.Result {
    LOG.info("Apply Code Changes on $device")
    val deployTask = ApplyCodeChangesTask(
      project,
      listOf(filterDisabledFeatures(app, deployOptions.disabledDynamicFeatures)),
      DeploymentConfiguration.getInstance().APPLY_CODE_CHANGES_FALLBACK_TO_RUN,
      deployOptions.alwaysInstallWithPm,
      installPathProvider
    )

    return runDeployTask(app, deployTask, device, indicator)
  }

  private fun filterDisabledFeatures(apkInfo: ApkInfo, disabledFeatures: List<String>): ApkInfo {
    return if (apkInfo.files.size > 1) {
      val filtered = apkInfo.files.filter { feature: ApkFileUnit -> DynamicAppUtils.isFeatureEnabled(disabledFeatures, feature) }
      apkInfo.copy(files = filtered)
    }
    else {
      apkInfo
    }
  }

  private fun runDeployTask(app: ApkInfo, deployTask: AbstractDeployTask, device: IDevice, indicator: ProgressIndicator): Deployer.Result {
    val result = stats.track(deployTask.id) {
      for (unit in app.files) {
        val artifactDetailBuilder = ArtifactDetail.newBuilder()
        artifactDetailBuilder.setSize(unit.apkFile.length())
        artifactDetailBuilder.setType(ArtifactDetail.ArtifactType.APK)
        addArtifact(artifactDetailBuilder)
      }

      // Check if a baseline profile fit the device
      for (bpSet in app.baselineProfiles) {
          if (device.version.apiLevel in bpSet.minApi..bpSet.maxApi) {
            for (bp in bpSet.baselineProfiles) {
              val artifactDetailBuilder = ArtifactDetail.newBuilder()
              artifactDetailBuilder.setSize(bp.length())
              artifactDetailBuilder.setType(ArtifactDetail.ArtifactType.BASELINE_PROFILE)
              addArtifact(artifactDetailBuilder)
            }
          }
      }

      deployTask.run(device, indicator).single() // use single(), because we have 1 apkInfo as input.
    }
    stats.addAllLaunchTaskDetail(deployTask.subTaskDetails)
    return result
  }
}

class AdbCommandCaptureLoggerWithConsole(logger: Logger, val console: ConsoleView) : LogWrapper(logger) {
  override fun info(msgFormat: String, vararg args: Any?) { // print to user console commands that we run on device
    if (msgFormat.contains("$ adb")) {
      console.println(msgFormat + "\n")
    }
    super.info(msgFormat, *args)
  }

  override fun warning(msgFormat: String, vararg args: Any?) { // print to user console commands that we run on device
    console.printlnError(msgFormat + "\n")
    super.info(msgFormat, *args)
  }
}

