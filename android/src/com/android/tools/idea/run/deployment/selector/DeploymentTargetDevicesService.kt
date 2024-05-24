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

import com.android.ddmlib.AndroidDebugBridge
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.SetChange
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.run.DeviceHandleAndroidDevice
import com.android.tools.idea.run.DeviceProvisionerAndroidDevice.DdmlibDeviceLookup
import com.android.tools.idea.run.DeviceTemplateAndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibilityChecker
import com.android.tools.idea.run.LaunchCompatibilityCheckerImpl
import com.android.tools.idea.run.asDdmlibDeviceLookup
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

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
    adbFlow(),
    launchCompatibilityCheckerFlow(project)
  )

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DeploymentTargetDevicesService =
      project.service<DeploymentTargetDevicesService>()
  }

  val coroutineScope =
    overrideCoroutineScope ?: AndroidCoroutineScope(this, CoroutineName("DevicesService"))

  override fun dispose() {}

  private val connectionTimes = ConcurrentHashMap<DeviceId, Instant>()

  private fun deviceHandleFlow(
    ddmlibDeviceLookup: DdmlibDeviceLookup,
    launchCompatibilityChecker: LaunchCompatibilityChecker
  ): Flow<List<DeploymentTargetDevice>> = channelFlow {
    val handles = ConcurrentHashMap<DeviceHandle, DeploymentTargetDevice>()
    // Immediately send empty list, since if the actual list is empty, there will be no Add,
    // and we don't want to be stuck in the Loading state.
    send(emptyList())
    devicesFlow
      .map { it.toSet() }
      .trackSetChanges()
      .collect {
        when (it) {
          is SetChange.Add -> {
            val handle = it.value
            handle.scope.launch {
              handle.stateFlow.collect { state ->
                val connectionTime =
                  if (handle.state.isOnline()) {
                    connectionTimes.computeIfAbsent(handle.id) { clock.now() }
                  } else {
                    connectionTimes.remove(handle.id)
                    null
                  }
                handles[handle] =
                  DeploymentTargetDevice.create(
                    DeviceHandleAndroidDevice(ddmlibDeviceLookup, handle, state),
                    connectionTime,
                    launchCompatibilityChecker
                  )
                send(handles.values.toList())
              }
            }
          }
          is SetChange.Remove -> {
            handles.remove(it.value)
            connectionTimes.remove(it.value.id)
            send(handles.values.toList())
          }
        }
      }
  }

  private fun deviceTemplateFlow(
    ddmlibDeviceLookup: DdmlibDeviceLookup,
    launchCompatibilityChecker: LaunchCompatibilityChecker
  ): Flow<List<DeploymentTargetDevice>> = flow {
    val templateDevices = mutableMapOf<DeviceTemplate, DeploymentTargetDevice>()
    templatesFlow.collect { templates ->
      templateDevices.keys.retainAll(templates)
      for (template in templates) {
        if (!templateDevices.containsKey(template)) {
          templateDevices[template] =
            DeploymentTargetDevice.create(
              DeviceTemplateAndroidDevice(coroutineScope, ddmlibDeviceLookup, template),
              null,
              launchCompatibilityChecker
            )
        }
      }
      emit(templateDevices.values.toList())
    }
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
      .stateIn(coroutineScope, SharingStarted.Lazily, LoadingState.Loading)

  /** A flow that only contains the list of devices when it is ready. */
  val loadedDevices: Flow<List<DeploymentTargetDevice>> = devices.mapNotNull { it.value }

  internal fun loadedDevicesOrNull(): List<DeploymentTargetDevice>? {
    return devices.firstValue().value
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
      ?.let { facet -> LaunchCompatibilityCheckerImpl.create(facet, null, null) }
      ?: LaunchCompatibilityChecker { LaunchCompatibility.YES }
  }
