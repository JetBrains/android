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
import com.android.adblib.serialNumber
import com.android.adblib.utils.createChildScope
import com.android.annotations.concurrency.GuardedBy
import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersionUtil
import com.android.sdklib.deviceprovisioner.ConnectionType
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceError
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.HideDeviceAction
import com.android.sdklib.deviceprovisioner.PairDeviceAction
import com.android.sdklib.deviceprovisioner.PhysicalDeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.sdklib.deviceprovisioner.awaitDisconnection
import com.android.sdklib.devices.Abi
import com.android.tools.idea.adb.wireless.AdbServiceWrapper
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService
import com.android.tools.idea.adb.wireless.TrackingMdnsService
import com.android.tools.idea.adb.wireless.WiFiPairingNotificationService
import com.android.tools.idea.adb.wireless.needsUpdate
import com.android.tools.idea.adb.wireless.showDeviceHiddenBalloon
import com.android.tools.idea.adb.wireless.v2.ui.WifiPairableDevicesPersistentStateComponent
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceActionPresentation
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceIcons
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import icons.StudioIcons
import javax.swing.Icon
import kotlin.collections.plus
import kotlin.collections.toSet
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WifiPairableDeviceProvisionerPlugin(
  private val scope: CoroutineScope,
  private val adbService: AdbServiceWrapper,
  private val project: Project,
  private val notificationService: WiFiPairingNotificationService,
) : DeviceProvisionerPlugin {

  private val log = logger<WifiPairableDeviceProvisionerPlugin>()

  private val persistentState = WifiPairableDevicesPersistentStateComponent.getInstance()

  private val mutex = Mutex()

  @GuardedBy("mutex") private val deviceHandles = HashMap<String, WifiPairableDeviceHandle>()

  private val _devices = MutableStateFlow(emptyList<DeviceHandle>())
  override val devices: StateFlow<List<DeviceHandle>> = _devices

  private val wifiConnectedDevicesRegex = Regex("(adb-.*-.*)\\._adb-tls-connect\\._tcp\\.?")
  private val wifiConnectedDevices = MutableStateFlow<Set<String>>(emptySet())

  /**
   * Must have higher priority than PhysicalDeviceProvisionerPlugin to filter out already
   * wifi-connected devices. It's ok to have very high priority since
   * WifiPairableDeviceProvisionerPlugin never claims any devices.
   *
   * @see PhysicalDeviceProvisionerPlugin.priority
   */
  override val priority: Int = Integer.MAX_VALUE

  override suspend fun claim(device: ConnectedDevice): DeviceHandle? {
    wifiConnectedDevicesRegex.find(device.serialNumber)?.groupValues?.get(1)?.let {
      wifiConnectedDevice ->
      wifiConnectedDevices.update { it + wifiConnectedDevice }
      scope.launch {
        device.awaitDisconnection()
        wifiConnectedDevices.update { it - wifiConnectedDevice }
      }
    }
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

      combine(mdnsTrackServicesFlow, persistentState.hiddenDevices, wifiConnectedDevices) {
          currentTrackServices,
          currentHiddenDeviceServiceNames,
          currentWifiConnectedDevices ->
          mutex.withLock {
            val newVisibleHandlesMap =
              buildNewVisibleHandlesMap(
                currentTrackServices,
                currentHiddenDeviceServiceNames,
                currentWifiConnectedDevices,
              )

            val handlesToCancel = determineHandlesToCancel(newVisibleHandlesMap)

            handlesToCancel.forEach { handle ->
              log.info("Cancelling scope for handle: ${handle.id} (removed or hidden)")
              handle.scope.cancel()
            }

            deviceHandles.clear()
            deviceHandles.putAll(newVisibleHandlesMap)

            _devices.value = deviceHandles.values.toList()
            log.debug(
              "Updated devices list. Count: ${deviceHandles.size}. Hidden count: ${currentHiddenDeviceServiceNames.size}"
            )
          }
        }
        .collect()
    }
  }

  private fun buildNewVisibleHandlesMap(
    currentTrackServices: Set<MdnsTlsService>,
    currentHiddenDeviceServiceNames: Set<String>,
    currentWifiConnectedDevices: Set<String>,
  ): Map<String, WifiPairableDeviceHandle> {
    val newOrReusedHandles = mutableMapOf<String, WifiPairableDeviceHandle>()

    for (trackService in currentTrackServices) {
      val serviceName = trackService.service.serviceInstanceName.instance

      if (currentHiddenDeviceServiceNames.contains(serviceName)) {
        continue
      }

      if (currentWifiConnectedDevices.contains(serviceName)) {
        // device is already paired and connected over wifi, don't show it again.
        continue
      }

      if (serviceName.startsWith("adb-EMULATOR")) {
        // It's not possible to pair emulators.
        continue
      }

      val handle =
        this.deviceHandles[serviceName]?.also {
          it.ensureDeviceNameUpToDate(
            trackService,
            buildDeviceNameForDeviceManager(trackService.service),
          )
        }
          ?: run {
            log.info("Creating new WifiPairableDeviceHandle for service: $serviceName")
            WifiPairableDeviceHandle.create(
              this.scope.createChildScope(isSupervisor = true),
              Disconnected(
                properties =
                  WifiPairableDeviceProperties.build {
                    this.deviceType = DeviceType.HANDHELD
                    icon = StudioDefaultDeviceIcons.iconForDeviceType(this.deviceType)
                    model = buildDeviceNameForDeviceManager(trackService.service)
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
                    mdnsService = trackService.service
                    populateDeviceInfoProto(PLUGIN_ID, null, emptyMap(), randomConnectionId())
                  },
                status = "Available for Wi-Fi pairing",
                error = if (trackService.service.needsUpdate()) NotUpdatedError else null,
              ),
              project,
              notificationService,
            )
          }
      newOrReusedHandles[serviceName] = handle
    }
    return newOrReusedHandles
  }

  private object NotUpdatedError : DeviceError {
    override val severity = DeviceError.Severity.WARNING
    override val message = "Check for device software updates to improve Wi-Fi pairing."
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

  private fun buildDeviceNameForDeviceManager(service: MdnsTrackServiceInfo): String =
    service.givenName.takeUnless { it.isNullOrBlank() }
      ?: service.deviceModel
        .takeUnless { it.isNullOrBlank() }
        ?.let { "$it at ${service.ipv4}:${service.port}" }
      ?: "Device at ${service.ipv4}:${service.port}"

  data class WifiPairableDeviceProperties(
    override val model: String?,
    override val manufacturer: String?,
    override val preferredAbi: String?,
    override val abiList: List<Abi>,
    override val androidVersion: AndroidVersion?,
    override val androidRelease: String?,
    override val deviceType: DeviceType?,
    override val isVirtual: Boolean?,
    override val isRemote: Boolean?,
    override val isDebuggable: Boolean?,
    override val isResizable: Boolean?,
    override val icon: Icon,
    override val resolution: Resolution?,
    override val density: Int?,
    override val disambiguator: String?,
    override val wearPairingId: String?,
    override val pairedPhoneId: DeviceId?,
    override val pairedGlassesId: DeviceId?,
    override val connectionType: ConnectionType?,
    override val deviceInfoProto: DeviceInfo,
    val mdnsService: MdnsTrackServiceInfo,
  ) : DeviceProperties {

    override fun toBuilder(): Builder =
      Builder().apply { copyFrom(this@WifiPairableDeviceProperties) }

    companion object {
      inline fun build(block: Builder.() -> Unit): WifiPairableDeviceProperties =
        Builder().apply(block).build()
    }

    class Builder : DeviceProperties.Builder() {
      var mdnsService: MdnsTrackServiceInfo? = null

      fun copyFrom(properties: WifiPairableDeviceProperties) {
        super.copyFrom(properties)
        mdnsService = properties.mdnsService
      }

      override fun build(): WifiPairableDeviceProperties =
        WifiPairableDeviceProperties(
          model = model,
          manufacturer = manufacturer,
          preferredAbi = preferredAbi,
          abiList = abiList,
          androidVersion = androidVersion,
          androidRelease = androidRelease,
          deviceType = deviceType,
          isVirtual = isVirtual,
          isRemote = isRemote,
          isDebuggable = isDebuggable,
          isResizable = isResizable,
          icon = checkNotNull(icon),
          resolution = resolution,
          density = density,
          disambiguator = disambiguator,
          wearPairingId = wearPairingId,
          pairedPhoneId = pairedPhoneId,
          pairedGlassesId = pairedGlassesId,
          connectionType = connectionType,
          deviceInfoProto = deviceInfoProto.build(),
          mdnsService = checkNotNull(mdnsService),
        )
    }
  }

  class WifiPairableDeviceHandle
  private constructor(
    override val scope: CoroutineScope,
    private val _stateFlow: MutableStateFlow<Disconnected>,
    private val project: Project,
    private val notificationService: WiFiPairingNotificationService,
  ) : DeviceHandle {

    override val stateFlow: StateFlow<DeviceState> = _stateFlow.asStateFlow()

    companion object {
      fun create(
        scope: CoroutineScope,
        baseState: Disconnected,
        project: Project,
        notificationService: WiFiPairingNotificationService,
      ): WifiPairableDeviceHandle =
        WifiPairableDeviceHandle(scope, MutableStateFlow(baseState), project, notificationService)
    }

    private val mdnsService: MdnsTrackServiceInfo
      get() = (stateFlow.value.properties as WifiPairableDeviceProperties).mdnsService

    private fun buildDeviceName(service: MdnsTrackServiceInfo): String =
      service.givenName.takeUnless { it.isNullOrBlank() }
        ?: service.deviceModel.takeUnless { it.isNullOrBlank() }
        ?: "Device"

    fun ensureDeviceNameUpToDate(trackService: MdnsTlsService, newModel: String) {
      _stateFlow.update { currentState ->
        currentState.copy(
          properties =
            (currentState.properties as WifiPairableDeviceProperties)
              .toBuilder()
              .apply {
                model = newModel
                mdnsService = trackService.service
              }
              .build()
        )
      }
    }

    override val wifiPairDeviceAction: PairDeviceAction =
      object : PairDeviceAction {
        override suspend fun pair() {
          val controller =
            PairDevicesUsingWiFiService.getInstance(project)
              .createPairingDialogController(
                TrackingMdnsService(
                  serviceName = mdnsService.serviceInstanceName.instance,
                  ipv4 = mdnsService.ipv4,
                  port = mdnsService.port.toString(),
                  deviceName = buildDeviceName(mdnsService),
                  mdnsServiceVersion = mdnsService.mdnsServiceVersion,
                )
              )
          controller.showDialog()
        }

        private val defaultPresentation =
          DeviceAction.Presentation("Pair", StudioIcons.Avd.PAIR_OVER_WIFI, true)

        override val presentation = MutableStateFlow(defaultPresentation).asStateFlow()
      }

    override val hideDeviceAction: HideDeviceAction =
      object : HideDeviceAction {
        override suspend fun hide() {
          WifiPairableDevicesPersistentStateComponent.getInstance()
            .addHiddenDevice(mdnsService.serviceInstanceName.instance)
          project.coroutineScope.launch(Dispatchers.EDT) {
            notificationService.showDeviceHiddenBalloon(buildDeviceName(mdnsService))
          }
        }

        override val presentation =
          MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
      }

    override val id =
      DeviceId("Wireless", false, "serviceName=${mdnsService.serviceInstanceName.instance}")
  }

  companion object {
    const val PLUGIN_ID = "WifiPairableDevices"
  }
}
