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
package com.android.tools.idea.run.deployment.selector

import com.android.ddmlib.IDevice
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.Snapshot
import com.android.tools.idea.run.DeviceHandleAndroidDevice
import com.android.tools.idea.run.DeviceProvisionerAndroidDevice
import com.android.tools.idea.run.DeviceTemplateAndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibilityChecker
import com.google.common.util.concurrent.Futures.immediateFailedFuture
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.ui.LayeredIcon
import icons.StudioIcons
import java.text.Collator
import javax.swing.Icon
import kotlinx.datetime.Instant


/**
 * An abstraction of a device (or device template) used by the deployment target selector.
 *
 * It is itself immutable, although it holds a DeviceProvisionerAndroidDevice which can change
 * state. It is mostly a wrapper of that handle, however, it adds the connection time and launch
 * compatibility (relative to the current run configuration). It is ephemeral; it will be recreated
 * whenever any of its inputs changes (e.g. changing the run configuration from a Wear app to a
 * mobile app affects the launch compatibility).
 */
class DeploymentTargetDevice(
  val androidDevice: DeviceProvisionerAndroidDevice,
  val connectionTime: Instant?,
  val snapshots: List<Snapshot>,
  val launchCompatibility: LaunchCompatibility,
) {
  companion object {
    suspend fun create(
      androidDevice: DeviceProvisionerAndroidDevice,
      connectionTime: Instant?,
      launchCompatibilityChecker: LaunchCompatibilityChecker,
    ): DeploymentTargetDevice {
      val snapshots =
        (androidDevice as? DeviceHandleAndroidDevice)?.deviceHandle?.bootSnapshotAction?.snapshots()
          ?: emptyList()

      return DeploymentTargetDevice(
        androidDevice,
        connectionTime,
        snapshots,
        launchCompatibilityChecker.validate(androidDevice),
      )
    }
  }

  /** The [DeviceId] of the [DeviceHandle] or [DeviceTemplate] wrapped by this object. */
  val id: DeviceId
    get() = androidDevice.id

  /**
   * The [DeviceId] of the template that this device was created from. This is null if the device
   * was not created from a template. If the device *is* a template, [templateId] is the same as
   * [id].
   */
  val templateId: DeviceId?
    get() =
      when (androidDevice) {
        is DeviceTemplateAndroidDevice -> id
        is DeviceHandleAndroidDevice -> androidDevice.deviceHandle.sourceTemplate?.id
      }

  val icon: Icon
    get() {
      var baseIcon = androidDevice.properties.icon
      if (isConnected) {
        baseIcon = ExecutionUtil.getLiveIndicator(baseIcon)
      }
      return when (launchCompatibility.state) {
        LaunchCompatibility.State.OK -> baseIcon
        LaunchCompatibility.State.WARNING ->
          LayeredIcon(baseIcon, AllIcons.General.WarningDecorator)
        LaunchCompatibility.State.ERROR -> LayeredIcon(baseIcon, StudioIcons.Common.ERROR_DECORATOR)
      }
    }

  val isConnected: Boolean
    get() = androidDevice.isRunning

  val name: String
    get() = androidDevice.name

  val disambiguator: String?
    get() = androidDevice.properties.disambiguator

  val defaultTarget: DeploymentTarget
    get() = DeploymentTarget(this, DefaultBoot)

  val targets: List<DeploymentTarget>
    get() = buildList {
      // All devices have a DefaultTarget, even those that can't be booted; boot is a no-op then.
      add(DeploymentTarget(this@DeploymentTargetDevice, DefaultBoot))
      // If there are no snapshots, omit cold boot for simplicity
      if (snapshots.isNotEmpty()) {
        if ((androidDevice as? DeviceHandleAndroidDevice)?.deviceHandle?.coldBootAction != null) {
          add(DeploymentTarget(this@DeploymentTargetDevice, ColdBoot))
        }
        addAll(snapshots.map { DeploymentTarget(this@DeploymentTargetDevice, BootSnapshot(it)) })
      }
    }

  // TODO: refactor this API; it's only used synchronously
  val ddmlibDeviceAsync: ListenableFuture<IDevice>
    get() {
      val device = androidDevice
      if (!device.isRunning()) {
        return immediateFailedFuture(IllegalStateException("$device is not running"))
      }
      return device.getLaunchedDevice()
    }

  override fun toString() = "Device($name)"
}

/**
 * Given the full set of devices that are present, returns a unique name for this device by adding
 * its disambiguator if there is a different device with the same name.
 */
fun DeploymentTargetDevice.disambiguatedName(
  otherDevices: List<DeploymentTargetDevice> = emptyList(),
): String =
  if (disambiguator != null && otherDevices.any { it.id != id && it.name == name }) {
    "$name [$disambiguator]"
  } else name

internal object DeviceComparator :
  Comparator<DeploymentTargetDevice> by (compareBy<DeploymentTargetDevice> {
      it.launchCompatibility.state
    }
    .thenByDescending(nullsFirst()) { it.connectionTime }
    .thenBy(Collator.getInstance()) { it.name })
