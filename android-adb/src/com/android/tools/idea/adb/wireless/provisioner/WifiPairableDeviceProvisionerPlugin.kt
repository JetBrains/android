/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless.provisioner

import com.android.adblib.AdbFeatures.TRACK_MDNS_SERVICE
import com.android.adblib.ConnectedDevice
import com.android.adblib.MdnsTlsService
import com.android.adblib.MdnsTrackServiceInfo
import com.android.adblib.utils.createChildScope
import com.android.annotations.concurrency.GuardedBy
import com.android.sdklib.AndroidVersionUtil
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.PairDeviceAction
import com.android.tools.idea.adb.wireless.AdbServiceWrapper
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService
import com.android.tools.idea.adb.wireless.TrackingMdnsService
import com.android.tools.idea.adb.wireless.v2.ui.WifiPairableDevicesPersistentStateComponent
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlin.collections.toSet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO(b/412571872) add tests
class WifiPairableDeviceProvisionerPlugin(
  private val scope: CoroutineScope,
  private val adbService: AdbServiceWrapper,
  private val project: Project,
) : DeviceProvisionerPlugin {

  private val log = logger<WifiPairableDeviceProvisionerPlugin>()

  private val persistentState = WifiPairableDevicesPersistentStateComponent.getInstance()

  private val mutex = Mutex()

  @GuardedBy("mutex") private val deviceHandles = HashMap<String, WifiPairableDeviceHandle>()

  private val _devices = MutableStateFlow(emptyList<DeviceHandle>())
  override val devices: StateFlow<List<DeviceHandle>> = _devices

  override val priority: Int = Integer.MIN_VALUE

  override suspend fun claim(device: ConnectedDevice): DeviceHandle? {
    return null
  }

  init {
    scope.launch {
      // TODO(b/412571872) investigate IllegalStateException(ADB has not been initialized for this
      // project)
      delay(5000)
      if (!adbService.getHostFeatures().contains(TRACK_MDNS_SERVICE)) {
        return@launch
      }
      val mdnsTrackServicesFlow: Flow<Set<MdnsTlsService>> =
        adbService
          .trackMdnsServices()
          .retryWhen { throwable, attempt ->
            if (throwable is CancellationException) {
              false
            } else {
              log.warn(
                "Error tracking mDNS services (attempt ${attempt + 1}), retrying in 1000 ms",
                throwable,
              )
              delay(1000)
              true
            }
          }
          .map { it.tlsMdnsServices.toSet() }

      combine(mdnsTrackServicesFlow, persistentState.hiddenDevices) {
          currentTrackServices,
          hiddenDeviceServiceNames ->
          Pair(currentTrackServices, hiddenDeviceServiceNames)
        }
        .collect { (currentTrackServices, hiddenDeviceServiceNames) ->
          mutex.withLock {
            val newVisibleHandlesMap =
              buildNewVisibleHandlesMap(currentTrackServices, hiddenDeviceServiceNames)

            val handlesToCancel = determineHandlesToCancel(newVisibleHandlesMap)

            handlesToCancel.forEach { handle ->
              log.info("Cancelling scope for handle: ${handle.serviceName} (removed or hidden)")
              handle.scope.cancel()
            }

            deviceHandles.clear()
            deviceHandles.putAll(newVisibleHandlesMap)

            _devices.value = deviceHandles.values.toList()
            log.debug(
              "Updated devices list. Count: ${deviceHandles.size}. Hidden count: ${hiddenDeviceServiceNames.size}"
            )
          }
        }
    }
  }

  private fun buildNewVisibleHandlesMap(
    currentTrackServices: Set<MdnsTlsService>,
    hiddenDeviceServiceNames: Set<String>,
  ): Map<String, WifiPairableDeviceHandle> {
    val newOrReusedHandles = mutableMapOf<String, WifiPairableDeviceHandle>()

    for (trackService in currentTrackServices) {
      val serviceName = trackService.service.serviceInstanceName.instance

      if (hiddenDeviceServiceNames.contains(serviceName)) {
        continue
      }

      if (trackService.knownDevice) {
        // known devices auto connect once mDNS is discovered, no need to pair again.
        continue
      }

      val handle =
        this.deviceHandles[serviceName]
          ?: run {
            log.info("Creating new WifiPairableDeviceHandle for service: $serviceName")
            WifiPairableDeviceHandle.create(
              this.scope.createChildScope(isSupervisor = true),
              Disconnected(
                properties =
                  DeviceProperties.build {
                    this.deviceType = DeviceType.HANDHELD
                    icon = StudioDefaultDeviceIcons.iconForDeviceType(this.deviceType)
                    model = getModelName(trackService.service)
                    androidVersion =
                      AndroidVersionUtil.androidVersionFromDeviceProperties(
                        mapOf(
                          // TODO(b/412571872) change map key to sdk_full when attribute has
                          // full_sdk
                          "ro.build.version.sdk" to (trackService.service.buildVersionSdkFull ?: "")
                        )
                      )
                    isVirtual = false
                    isRemote = false
                    populateDeviceInfoProto(PLUGIN_ID, null, emptyMap(), randomConnectionId())
                  }
              ),
              project,
              serviceName,
              trackService.service.deviceModel,
              trackService.service.ipv4,
              trackService.service.port,
            )
          }
      newOrReusedHandles[serviceName] = handle
    }
    return newOrReusedHandles
  }

  private fun determineHandlesToCancel(
    newVisibleHandlesMap: Map<String, WifiPairableDeviceHandle>
  ): List<WifiPairableDeviceHandle> {
    val handlesToCancel = mutableListOf<WifiPairableDeviceHandle>()
    this.deviceHandles.forEach { (serviceName, handle) ->
      if (!newVisibleHandlesMap.containsKey(serviceName)) {
        handlesToCancel.add(handle)
      }
    }
    return handlesToCancel
  }

  private fun getModelName(service: MdnsTrackServiceInfo): String {
    if (service.deviceModel.isNullOrBlank()) {
      return "Device at ${service.ipv4}:${service.port}"
    }
    return "${service.deviceModel} at ${service.ipv4}:${service.port}"
  }

  class WifiPairableDeviceHandle
  private constructor(
    override val scope: CoroutineScope,
    override val stateFlow: StateFlow<DeviceState>,
    private val project: Project,
    val serviceName: String,
    val deviceName: String?,
    val ipv4: String,
    val port: Int,
  ) : DeviceHandle {

    companion object {
      fun create(
        scope: CoroutineScope,
        baseState: DeviceState,
        project: Project,
        serviceName: String,
        deviceName: String?,
        ipv4: String,
        port: Int,
      ): WifiPairableDeviceHandle =
        WifiPairableDeviceHandle(
          scope,
          MutableStateFlow(baseState),
          project,
          serviceName,
          deviceName,
          ipv4,
          port,
        )
    }

    override val wifiPairDeviceAction: PairDeviceAction? =
      object : PairDeviceAction {
        override suspend fun pair() {
          val controller =
            PairDevicesUsingWiFiService.getInstance(project)
              .createPairingDialogController(
                TrackingMdnsService(
                  serviceName = serviceName,
                  ipv4 = ipv4,
                  port = port.toString(),
                  deviceName = deviceName,
                )
              )
          controller.showDialog()
        }

        private val defaultPresentation =
          DeviceAction.Presentation("Pair", StudioIcons.Avd.PAIR_OVER_WIFI, true)

        override val presentation = MutableStateFlow(defaultPresentation).asStateFlow()
      }

    override val id = DeviceId("Wireless", false, "serviceName=$serviceName")
  }

  companion object {
    const val PLUGIN_ID = "WifiPairableDevices"
  }
}
