/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adblib.ConnectedDevice
import com.android.adblib.serialNumber
import com.android.adblib.utils.createChildScope
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.deviceprovisioner.DeviceError
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.Snapshot
import com.android.sdklib.deviceprovisioner.awaitReady
import com.android.sdklib.devices.Abi
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.withTimeoutOrNull
import javax.swing.Icon

/**
 * An [AndroidDevice] implemented via the [DeviceProvisioner]. In contrast to the other
 * AndroidDevice implementations, we do not implement "launchable" and "connected" devices
 * separately, since DeviceHandle can be both.
 *
 * Note that, for now, it still provides an [IDevice] when booted, as required by the interface.
 */
sealed class DeviceProvisionerAndroidDevice(parentScope: CoroutineScope) : AndroidDevice {
  fun interface DdmlibDeviceLookup {
    suspend fun findDdmlibDevice(connectedDevice: ConnectedDevice): IDevice
  }

  abstract val id: DeviceId

  abstract val properties: DeviceProperties

  protected val scope = parentScope.createChildScope(isSupervisor = true)
  private val launchDeviceTask = AtomicReference<Deferred<IDevice>?>(null)

  override fun launch(project: Project): ListenableFuture<IDevice> = bootDefault()

  /**
   * Boots the device in the default manner, if it is not already running, and returns the resulting
   * [IDevice]. This will cancel any existing boot operation and start a new one.
   */
  abstract fun bootDefault(): ListenableFuture<IDevice>

  protected fun boot(action: suspend () -> IDevice): ListenableFuture<IDevice> {
    return scope
      .async { action() }
      .also { launchDeviceTask.getAndSet(it)?.cancel() }
      .asListenableFuture()
  }

  override fun getLaunchedDevice(): ListenableFuture<IDevice> {
    return launchDeviceTask.get()?.asListenableFuture()
      ?: throw IllegalStateException("Attempt to get device that hasn't been launched yet.")
  }

  override fun getSerial(): String = buildString {
    append("DeviceProvisionerAndroidDevice pluginId=")
    append(id.pluginId)
    if (id.isTemplate) append(" isTemplate=true")
    append(" identifier=")
    append(id.identifier)
  }

  override fun isVirtual() = properties.isVirtual == true

  override fun getVersion(): AndroidVersion = properties.androidVersion ?: AndroidVersion.DEFAULT

  override fun getDensity() = properties.density ?: -1

  override fun getAbis() = properties.abiList

  override fun getAppPreferredAbi(): String? = properties.preferredAbi

  override fun supportsFeature(feature: IDevice.HardwareFeature): Boolean =
    when (feature) {
      IDevice.HardwareFeature.WATCH -> properties.deviceType == DeviceType.WEAR
      IDevice.HardwareFeature.TV -> properties.deviceType == DeviceType.TV
      else -> false
    }

  override fun getName() = properties.title

  override fun isDebuggable() = properties.isDebuggable == true

  override fun getIcon() = properties.icon
}

class DeviceTemplateAndroidDevice(
  parentScope: CoroutineScope,
  val ddmlibDeviceLookup: DdmlibDeviceLookup,
  val deviceTemplate: DeviceTemplate,
) : DeviceProvisionerAndroidDevice(parentScope) {
  override val id = deviceTemplate.id

  override val properties
    get() = deviceTemplate.properties

  override fun isRunning() = false

  override fun bootDefault(): ListenableFuture<IDevice> = boot {
    val deviceHandle = deviceTemplate.activationAction.activate()

    val deviceState =
      withTimeoutOrNull(activationTimeout) { deviceHandle.awaitReady() }
        ?: throw IllegalStateException("Device did not start")
    ddmlibDeviceLookup.findDdmlibDeviceWithTimeout(deviceState.connectedDevice)
  }

  override fun canRun(
    minSdkVersion: AndroidVersion,
    projectTarget: IAndroidTarget,
    getRequiredHardwareFeatures: Supplier<EnumSet<IDevice.HardwareFeature>>,
    supportedAbis: MutableSet<Abi>,
  ): LaunchCompatibility {

    val projectLaunchCompatibility =
      LaunchCompatibility.canRunOnDevice(
        minSdkVersion,
        projectTarget,
        getRequiredHardwareFeatures,
        supportedAbis,
        this,
      )
    return projectLaunchCompatibility.combine(deviceTemplate.state.error.toLaunchCompatibility())
  }
}

class DeviceHandleAndroidDevice(
  private val ddmlibDeviceLookup: DdmlibDeviceLookup,
  val deviceHandle: DeviceHandle,
  val deviceState: DeviceState,
) : DeviceProvisionerAndroidDevice(deviceHandle.scope) {
  init {
    // If we're already connected, then set the IDevice
    deviceState.connectedDevice?.let { boot { ddmlibDeviceLookup.findDdmlibDeviceWithTimeout(it) } }
  }

  override val id = deviceHandle.id

  override val properties
    get() = deviceHandle.state.properties

  override fun isRunning() = deviceHandle.state.connectedDevice != null

  override fun bootDefault(): ListenableFuture<IDevice> = boot {
    activate { deviceHandle.activationAction?.activate() }
  }

  fun coldBoot(): ListenableFuture<IDevice> = boot {
    activate { deviceHandle.coldBootAction?.activate() }
  }

  fun bootFromSnapshot(snapshot: Snapshot): ListenableFuture<IDevice> = boot {
    activate { deviceHandle.bootSnapshotAction?.activate(snapshot) }
  }

  private suspend fun activate(action: suspend () -> Unit): IDevice {
    if (deviceHandle.state.connectedDevice == null) {
      action()
    }
    val deviceState =
      withTimeoutOrNull(activationTimeout) { deviceHandle.awaitReady() }
        ?: throw IllegalStateException("Device did not start")
    return ddmlibDeviceLookup.findDdmlibDeviceWithTimeout(deviceState.connectedDevice)
  }

  override fun canRun(
    minSdkVersion: AndroidVersion,
    projectTarget: IAndroidTarget,
    getRequiredHardwareFeatures: Supplier<EnumSet<IDevice.HardwareFeature>>,
    supportedAbis: MutableSet<Abi>,
  ): LaunchCompatibility {
    val projectLaunchCompatibility =
      LaunchCompatibility.canRunOnDevice(
        minSdkVersion,
        projectTarget,
        getRequiredHardwareFeatures,
        supportedAbis,
        this,
      )
    // If the device is running, assume that these errors don't matter.
    val deviceLaunchCompatibility =
      deviceHandle.state.error?.takeUnless { isRunning }.toLaunchCompatibility()

    // Favor the project launch compatibility, since handle state tends to be more temporary.
    return projectLaunchCompatibility.combine(deviceLaunchCompatibility)
  }
}

private fun DeviceError?.toLaunchCompatibility(): LaunchCompatibility =
  when (this?.severity) {
    DeviceError.Severity.ERROR -> LaunchCompatibility(LaunchCompatibility.State.ERROR, message)
    DeviceError.Severity.WARNING -> LaunchCompatibility(LaunchCompatibility.State.WARNING, message)
    DeviceError.Severity.INFO,
    null -> LaunchCompatibility.YES
  }

private suspend fun DeviceProvisionerAndroidDevice.DdmlibDeviceLookup.findDdmlibDeviceWithTimeout(
  connectedDevice: ConnectedDevice,
  timeout: Duration = 10.seconds,
): IDevice {
  return withTimeoutOrNull(timeout) { findDdmlibDevice(connectedDevice) }
    ?: throw IllegalStateException("IDevice not found for ${connectedDevice.serialNumber}")
}

fun AndroidDebugBridge.asDdmlibDeviceLookup() =
  DeviceProvisionerAndroidDevice.DdmlibDeviceLookup { connectedDevice ->
    pollUntilPresent { devices.firstOrNull { it.serialNumber == connectedDevice.serialNumber } }
  }

suspend inline fun <R> pollUntilPresent(block: () -> R?): R {
  while (true) {
    block()?.let {
      return it
    }
    delay(50)
  }
}

/** How long we wait after activate() returns for the device to become ready. */
private val activationTimeout = 5.minutes
