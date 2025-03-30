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

import com.android.adblib.ConnectedDevice
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.TemplateState
import com.android.sdklib.deviceprovisioner.mapChangedState
import com.android.sdklib.deviceprovisioner.pairWithNestedState
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.run.DeviceHandleAndroidDevice
import com.android.tools.idea.run.DeviceProvisionerAndroidDevice.DdmlibDeviceLookup
import com.android.tools.idea.run.DeviceTemplateAndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibilityChecker
import com.android.tools.idea.run.LaunchCompatibilityCheckerImpl
import com.android.tools.idea.run.asDdmlibDeviceLookup
import com.android.tools.idea.run.deployment.selector.DeploymentTargetDevicesService.ActivationState.Activatable
import com.android.tools.idea.run.deployment.selector.DeploymentTargetDevicesService.ActivationState.NotActivatable
import com.android.tools.idea.run.deployment.selector.DeploymentTargetDevicesService.ActivationState.Online
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.android.facet.AndroidFacet

/**
 * A service that produces the [DeploymentTargetDevice] objects used by the target selection
 * dropdown, by joining the current set of devices and device templates from the [DeviceProvisioner]
 * with their [LaunchCompatibility] state and connection time.
 */
class DeploymentTargetDevicesService
@NonInjectable
@VisibleForTesting
constructor(
  overrideCoroutineScope: CoroutineScope?,
  private val devicesFlow: StateFlow<List<DeviceHandle>>,
  private val templatesFlow: StateFlow<List<DeviceTemplate>>,
  private val clock: Clock,
  private val adbFlow: Flow<DdmlibDeviceLookup?>,
  private val launchCompatibilityCheckerFlow: Flow<LaunchCompatibilityChecker>,
) : Disposable {
  constructor(
    project: Project
  ) : this(
    null, // use default
    project.service<DeviceProvisionerService>().deviceProvisioner.devices,
    project.service<DeviceProvisionerService>().deviceProvisioner.templates,
    Clock.System,
    adbDeviceLookupFlow(),
    launchCompatibilityCheckerFlow(project),
  )

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DeploymentTargetDevicesService =
      project.service<DeploymentTargetDevicesService>()
  }

  val coroutineScope =
    overrideCoroutineScope ?: AndroidCoroutineScope(this, CoroutineName("DevicesService"))

  override fun dispose() {}

  /** The flattened state of a DeviceHandle that is needed to create a DeploymentTargetDevice. */
  private data class DeviceHandleState(
    val handle: DeviceHandle,
    val state: DeviceState,
    val activationState: ActivationState,
    val connectionTime: Instant?,
  )

  private sealed interface ActivationState {
    object Online : ActivationState

    object Activatable : ActivationState

    data class NotActivatable(val error: String) : ActivationState

    fun toLaunchCompatibilityChecker() = LaunchCompatibilityChecker {
      when (this) {
        is NotActivatable -> LaunchCompatibility(LaunchCompatibility.State.ERROR, error)
        else -> LaunchCompatibility.YES
      }
    }
  }

  private fun Flow<Iterable<DeviceHandle>>.pairWithDeviceAndActivationState():
    Flow<List<Pair<DeviceHandle, Pair<DeviceState, ActivationState>>>> =
    pairWithNestedState { handle ->
      val actionFlow = handle.activationAction?.presentation ?: flowOf(null)
      handle.stateFlow.combine(actionFlow) { deviceState, presentation ->
        Pair(
          deviceState,
          when {
            deviceState.isOnline() -> Online
            deviceState.isTransitioning -> Activatable
            presentation == null -> NotActivatable("Unavailable")
            presentation.enabled -> Activatable
            else -> NotActivatable(presentation.detail ?: "Unavailable")
          },
        )
      }
    }

  private fun Flow<Iterable<DeviceTemplate>>.pairWithTemplateAndActivationState():
    Flow<List<Pair<DeviceTemplate, Pair<TemplateState, ActivationState>>>> =
    pairWithNestedState { template ->
      template.stateFlow.combine(template.activationAction.presentation) { state, presentation ->
        val activationState =
          when {
            presentation.enabled -> Activatable
            state.isActivating -> Activatable
            else -> NotActivatable(presentation.detail ?: "Unavailable")
          }
        Pair(state, activationState)
      }
    }

  /** A StateFlow that tracks DeviceHandles to build [DeviceHandleState]s. */
  private val deviceStateFlow: StateFlow<List<DeviceHandleState>> =
    run {
        val connectionTimes = mutableMapOf<DeviceId, Instant>()
        devicesFlow
          .pairWithDeviceAndActivationState()
          .onEach { handles -> connectionTimes.keys.retainAll(handles.map { it.first.id }.toSet()) }
          .mapChangedState { handle, (state, activationState) ->
            val connectionTime =
              if (state.isOnline()) {
                connectionTimes.computeIfAbsent(handle.id) { clock.now() }
              } else {
                connectionTimes.remove(handle.id)
                null
              }
            DeviceHandleState(handle, state, activationState, connectionTime)
          }
      }
      .stateIn(scope = coroutineScope, SharingStarted.Eagerly, emptyList())

  /**
   * Provides a flow of DeploymentTargetDevice based on DeviceHandles, by combining the
   * deviceStateFlow with the current ADB and LaunchCompatibilityChecker.
   */
  private fun deviceHandleFlow(
    ddmlibDeviceLookup: DdmlibDeviceLookup,
    launchCompatibilityChecker: LaunchCompatibilityChecker,
  ): Flow<List<DeploymentTargetDevice>> =
    deviceStateFlow.map {
      it.map {
        val device = DeviceHandleAndroidDevice(ddmlibDeviceLookup, it.handle, it.state)
        val launchCompatibility =
          it.activationState
            .toLaunchCompatibilityChecker()
            .combine(launchCompatibilityChecker)
            .validate(device)
        val snapshots = it.handle.bootSnapshotAction?.snapshots() ?: emptyList()
        DeploymentTargetDevice(
          DeviceHandleAndroidDevice(ddmlibDeviceLookup, it.handle, it.state),
          it.connectionTime,
          snapshots,
          launchCompatibility,
        )
      }
    }

  private fun deviceTemplateFlow(
    ddmlibDeviceLookup: DdmlibDeviceLookup,
    launchCompatibilityChecker: LaunchCompatibilityChecker,
  ): Flow<List<DeploymentTargetDevice>> =
    templatesFlow.pairWithTemplateAndActivationState().mapChangedState {
      template,
      (_, activationState) ->
      val device = DeviceTemplateAndroidDevice(coroutineScope, ddmlibDeviceLookup, template)
      val launchCompatibility =
        activationState
          .toLaunchCompatibilityChecker()
          .combine(launchCompatibilityChecker)
          .validate(device)
      DeploymentTargetDevice(device, null, emptyList(), launchCompatibility)
    }

  internal val devices: StateFlow<LoadingState<List<DeploymentTargetDevice>>> =
    adbFlow
      .flatMapLatest { ddmlibDeviceLookup ->
        if (ddmlibDeviceLookup == null) {
          flowOf(LoadingState.Loading)
        } else {
          launchCompatibilityCheckerFlow.flatMapLatest { launchCompatibilityChecker ->
            deviceHandleFlow(ddmlibDeviceLookup, launchCompatibilityChecker).combine(
              deviceTemplateFlow(ddmlibDeviceLookup, launchCompatibilityChecker)
            ) { handles, templates ->
              // Filter out the templates that are active
              val activeTemplates = handles.mapNotNull { it.templateId }.toSet()
              val devices = handles + templates.filterNot { it.id in activeTemplates }
              LoadingState.Ready(devices.sortedWith(DeviceComparator))
            }
          }
        }
      }
      .stateIn(coroutineScope, SharingStarted.Eagerly, LoadingState.Loading)

  /** A flow that only contains the list of devices when it is ready. */
  val loadedDevices: Flow<List<DeploymentTargetDevice>> = devices.mapNotNull { it.value }

  fun loadedDevicesOrNull(): List<DeploymentTargetDevice>? {
    return devices.value.value
  }
}

/**
 * Returns the current value of the StateFlow, like StateFlow.value, but if the flow is lazy, also
 * starts collecting the flow.
 */
internal fun <T> StateFlow<T>.firstValue() = runBlocking { first() }

private fun adbFlow(): Flow<DdmlibDeviceLookup?> = callbackFlow {
  val listener =
    AndroidDebugBridge.IDebugBridgeChangeListener { bridge ->
      trySendBlocking(bridge?.asDdmlibDeviceLookup())
    }
  trySendBlocking(null)
  AndroidDebugBridge.addDebugBridgeChangeListener(listener)
  awaitClose { AndroidDebugBridge.removeDebugBridgeChangeListener(listener) }
}

/**
 * A [DdmlibDeviceLookup] that just uses the current value of [AndroidDebugBridge] at lookup time,
 * rather than holding a reference that may go stale.
 */
internal object AdbDeviceLookup : DdmlibDeviceLookup {
  override suspend fun findDdmlibDevice(connectedDevice: ConnectedDevice): IDevice =
    AndroidDebugBridge.getBridge()?.asDdmlibDeviceLookup()?.findDdmlibDevice(connectedDevice)
      ?: throw IllegalStateException("No ADB connection")
}

/**
 * A flow that emits [AdbDeviceLookup] whenever [AndroidDebugBridge] is not null, and null
 * otherwise.
 */
private fun adbDeviceLookupFlow() =
  adbFlow().map { if (it == null) null else AdbDeviceLookup }.distinctUntilChanged()

/** Ignore the dumb mode switch unless it lasts for a little while. */
private fun debouncedDumbModeFlow(project: Project): Flow<DumbModeStatus> =
  dumbModeFlow(project).debounce {
    when (it) {
      DumbModeStatus.SMART_MODE -> 0.seconds
      DumbModeStatus.DUMB_MODE -> 2.seconds
    }
  }

internal fun launchCompatibilityCheckerFlow(project: Project): Flow<LaunchCompatibilityChecker> =
  runConfigurationFlow(project).combine(debouncedDumbModeFlow(project)) { runSettings, dumbMode ->
    (runSettings?.takeIf { dumbMode == DumbModeStatus.SMART_MODE }?.configuration
        as? ModuleBasedConfiguration<*, *>)
      ?.configurationModule
      ?.module
      ?.let { module -> AndroidFacet.getInstance(module) }
      ?.let { facet -> LaunchCompatibilityCheckerImpl.create(facet) }
      ?: LaunchCompatibilityChecker { LaunchCompatibility.YES }
  }
