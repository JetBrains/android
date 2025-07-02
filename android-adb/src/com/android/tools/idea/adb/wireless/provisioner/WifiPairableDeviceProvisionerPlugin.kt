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
import com.android.adblib.MdnsTrackServiceInfo
import com.android.adblib.utils.createChildScope
import com.android.annotations.concurrency.GuardedBy
import com.android.sdklib.AndroidVersionUtil
import com.android.sdklib.deviceprovisioner.DefaultProvisionerPlugin.Companion.PLUGIN_ID
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.PairDeviceAction
import com.android.sdklib.deviceprovisioner.SetChange.Add
import com.android.sdklib.deviceprovisioner.SetChange.Remove
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.idea.adb.wireless.AdbServiceWrapper
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlin.collections.toSet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
      adbService
        .trackMdnsServices()
        .retryWhen { throwable, _ ->
          if (throwable is CancellationException) {
            false
          } else {
            delay(1000)
            true
          }
        }
        .map { it.tlsMdnsServices.toSet() }
        .trackSetChanges()
        .collect {
          mutex.withLock {
            when (it) {
              is Remove -> {
                deviceHandles.remove(it.value.service.serviceInstanceName.instance)?.scope?.cancel()
              }
              is Add -> {
                if (deviceHandles.contains(it.value.service.serviceInstanceName.instance)) {
                  throw IllegalStateException("found duplicate mdnsService: ${it.value}")
                }
                // TODO(b/412571872) filter out knownDevice (currently show after device name for
                // debugging)
                // if (it.value.knownDevice) {
                // return@withLock
                // }
                val handle =
                  WifiPairableDeviceHandle.create(
                    scope.createChildScope(isSupervisor = true),
                    Disconnected(
                      properties =
                        DeviceProperties.build {
                          deviceType = DeviceType.HANDHELD
                          icon = StudioDefaultDeviceIcons.iconForDeviceType(deviceType)
                          model = getModelName(it.value.service, it.value.knownDevice)
                          androidVersion =
                            AndroidVersionUtil.androidVersionFromDeviceProperties(
                              mapOf(
                                // TODO(b/412571872) change map key to sdk_full when attribute has
                                // full_sdk
                                "ro.build.version.sdk" to
                                  (it.value.service.buildVersionSdkFull ?: "")
                              )
                            )
                          isVirtual = false
                          isRemote = false
                          populateDeviceInfoProto(PLUGIN_ID, null, emptyMap(), randomConnectionId())
                        }
                    ),
                    project,
                    it.value.service.serviceInstanceName.instance,
                    it.value.service.ipv4,
                    it.value.service.port,
                  )

                deviceHandles[it.value.service.serviceInstanceName.instance] = handle
              }
            }
            _devices.value = deviceHandles.values.toList()
          }
        }
    }
  }

  private fun getModelName(service: MdnsTrackServiceInfo, knownDevice: Boolean): String {
    if (service.deviceModel.isNullOrBlank()) {
      return "Device at ${service.ipv4}:${service.port} $knownDevice"
    }
    return "${service.deviceModel} at ${service.ipv4}:${service.port} $knownDevice"
  }

  class WifiPairableDeviceHandle
  private constructor(
    override val scope: CoroutineScope,
    override val stateFlow: StateFlow<DeviceState>,
    private val project: Project,
    val serviceName: String,
    val ipv4: String,
    val port: Int,
  ) : DeviceHandle {

    companion object {
      fun create(
        scope: CoroutineScope,
        baseState: DeviceState,
        project: Project,
        serviceName: String,
        ipv4: String,
        port: Int,
      ): WifiPairableDeviceHandle =
        WifiPairableDeviceHandle(
          scope,
          MutableStateFlow(baseState),
          project,
          serviceName,
          ipv4,
          port,
        )
    }

    override val wifiPairDeviceAction: PairDeviceAction? =
      object : PairDeviceAction {
        override suspend fun pair() {
          val controller =
            PairDevicesUsingWiFiService.getInstance(project)
              .createPairingDialogController(serviceName)
          controller.showDialog()
        }

        private val defaultPresentation =
          DeviceAction.Presentation("Pair", StudioIcons.Avd.PAIR_OVER_WIFI, true)

        override val presentation = MutableStateFlow(defaultPresentation).asStateFlow()
      }

    override val id = DeviceId("Wireless", false, "serviceName=$serviceName")
  }
}
