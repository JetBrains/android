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
package com.android.tools.idea.run.deployment.selector

import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.DeployableToDevice.deploysToLocalDevice
import com.android.tools.idea.run.DeploymentApplicationService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.util.concurrency.AppExecutorUtil
import icons.StudioIcons
import java.util.Objects
import javax.swing.Icon

/**
 * The combo box generates these [ExecutionTargets.][ExecutionTarget] ExecutionTargets determine the
 * state of the run, debug, and stop (but *not* the apply changes) toolbar buttons.
 */
internal class DeviceAndSnapshotComboBoxExecutionTarget(
  targets: Collection<DeploymentTarget>,
  private val devicesService: DeploymentTargetDevicesService,
  private val deploymentApplicationService: () -> DeploymentApplicationService =
    DeploymentApplicationService.Companion::instance
) : AndroidExecutionTarget() {
  private val ids = targets.map(DeploymentTarget::deviceId).toSet()

  override fun isApplicationRunningAsync(appPackage: String): ListenableFuture<Boolean> {
    return Futures.submit<Boolean>(
      { isApplicationRunning(appPackage) },
      AppExecutorUtil.getAppExecutorService()
    )
  }

  private fun isApplicationRunning(appPackage: String): Boolean {
    val service = deploymentApplicationService()
    return runningDevices.any { device -> service.findClient(device, appPackage).isNotEmpty() }
  }

  override fun getAvailableDeviceCount(): Int {
    return devices().count()
  }

  override fun getRunningDevices(): Collection<IDevice> {
    return devices().filter(DeploymentTargetDevice::isConnected).map {
      Futures.getUnchecked(it.ddmlibDeviceAsync)
    }
  }

  private fun devices(): List<DeploymentTargetDevice> {
    return devicesService.loadedDevicesOrNull()?.filter { it.id in ids } ?: emptyList()
  }

  override fun getId(): String {
    return ids
      .map { it.toString() }
      .sorted()
      .joinToString(
        separator = ", ",
        prefix = "device_and_snapshot_combo_box_target[",
        postfix = "]"
      )
  }

  override fun getDisplayName(): String =
    devices().let {
      when (it.size) {
        0 -> "No Devices"
        1 -> it[0].name
        else -> "Multiple Devices"
      }
    }

  override fun getIcon(): Icon =
    devices().let {
      when (it.size) {
        1 -> it[0].icon
        else -> StudioIcons.DeviceExplorer.MULTIPLE_DEVICES
      }
    }

  override fun canRun(configuration: RunConfiguration): Boolean =
    deploysToLocalDevice(configuration)

  override fun equals(other: Any?): Boolean =
    other is DeviceAndSnapshotComboBoxExecutionTarget &&
      ids == other.ids &&
      devicesService == other.devicesService &&
      deploymentApplicationService == other.deploymentApplicationService

  override fun hashCode(): Int {
    return Objects.hash(ids, devicesService, deploymentApplicationService)
  }
}
