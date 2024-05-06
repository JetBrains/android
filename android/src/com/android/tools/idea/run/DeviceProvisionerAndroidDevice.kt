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
import com.android.sdklib.devices.Abi
import com.android.tools.idea.concurrency.getCompletedOrNull
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import com.intellij.util.Function
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.android.facet.AndroidFacet

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

  override fun getSerial(): String {
    // The only real use of this is by AndroidDeviceSpecUtil to pass the ADB serial number to gradle
    // to identify the device (b/234033515), so there's no need to support this for non-running
    // devices.
    return runBlocking { launchDeviceTask.get()?.getCompletedOrNull()?.serialNumber ?: "" }
  }

  override fun isVirtual() = properties.isVirtual == true

  override fun getVersion(): AndroidVersion = properties.androidVersion ?: AndroidVersion.DEFAULT

  override fun getDensity() = properties.density ?: -1

  override fun getAbis() = properties.abiList

  override fun supportsFeature(feature: IDevice.HardwareFeature): Boolean =
    when (feature) {
      IDevice.HardwareFeature.WATCH -> properties.deviceType == DeviceType.WEAR
      IDevice.HardwareFeature.TV -> properties.deviceType == DeviceType.TV
      else -> false
    }

  override fun getName() = properties.title

  override fun isDebuggable() = properties.isDebuggable == true
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
    val connectedDevice =
      withTimeoutOrNull(activationTimeout) { deviceHandle.awaitOnline() }
        ?: throw IllegalStateException("Device did not start")
    ddmlibDeviceLookup.findDdmlibDeviceWithTimeout(connectedDevice)
  }

  override fun canRun(
    minSdkVersion: AndroidVersion,
    projectTarget: IAndroidTarget,
    facet: AndroidFacet,
    getRequiredHardwareFeatures: Function<AndroidFacet, EnumSet<IDevice.HardwareFeature>>?,
    supportedAbis: MutableSet<Abi>
  ): LaunchCompatibility {
    return LaunchCompatibility.canRunOnDevice(
      minSdkVersion,
      projectTarget,
      facet,
      getRequiredHardwareFeatures,
      supportedAbis,
      this
    )
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
    val connectedDevice =
      withTimeoutOrNull(activationTimeout) { deviceHandle.awaitOnline() }
        ?: throw IllegalStateException("Device did not start")
    return ddmlibDeviceLookup.findDdmlibDeviceWithTimeout(connectedDevice)
  }

  override fun canRun(
    minSdkVersion: AndroidVersion,
    projectTarget: IAndroidTarget,
    facet: AndroidFacet,
    getRequiredHardwareFeatures: Function<AndroidFacet, EnumSet<IDevice.HardwareFeature>>?,
    supportedAbis: MutableSet<Abi>
  ): LaunchCompatibility {
    deviceHandle.state.error?.let {
      when (it.severity) {
        DeviceError.Severity.ERROR ->
          return LaunchCompatibility(LaunchCompatibility.State.ERROR, it.message)
        DeviceError.Severity.WARNING ->
          return LaunchCompatibility(LaunchCompatibility.State.WARNING, it.message)
        DeviceError.Severity.INFO -> {}
      }
    }
    return LaunchCompatibility.canRunOnDevice(
      minSdkVersion,
      projectTarget,
      facet,
      getRequiredHardwareFeatures,
      supportedAbis,
      this
    )
  }
}

private suspend fun DeviceProvisionerAndroidDevice.DdmlibDeviceLookup.findDdmlibDeviceWithTimeout(
  connectedDevice: ConnectedDevice,
  timeout: Duration = 1.seconds
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

/** Returns the DeviceHandle's ConnectedDevice when it reaches the ONLINE state. */
suspend fun DeviceHandle.awaitOnline(): ConnectedDevice =
  stateFlow
    .transformLatest { state ->
      state.connectedDevice?.let { connectedDevice ->
        connectedDevice.deviceInfoFlow.first {
          it.deviceState == com.android.adblib.DeviceState.ONLINE
        }
        emit(connectedDevice)
      }
    }
    .first()

/**
 * How long we wait after activate() returns for the device to become connected.
 *
 * TODO: We could try to enforce that the DeviceHandle always waits for connection before returning
 *   instead
 */
private val activationTimeout = 5.seconds
