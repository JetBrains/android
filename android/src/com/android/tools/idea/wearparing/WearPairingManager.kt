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
package com.android.tools.idea.wearparing

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.EmulatorConsole
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.HardwareFeature
import com.android.ddmlib.NullOutputReceiver
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.ddms.DevicePropertyUtil.getManufacturer
import com.android.tools.idea.ddms.DevicePropertyUtil.getModel
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.wearparing.ConnectionState.OFFLINE
import com.android.tools.idea.wearparing.ConnectionState.ONLINE
import com.google.common.util.concurrent.Futures
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.android.util.AndroidBundle.message

private val LOG get() = logger<WearPairingManager>()

object WearPairingManager : AndroidDebugBridge.IDeviceChangeListener {
  private val updateDevicesChannel = Channel<Unit>(1)

  private var runningJob: Job? = null
  private var model = WearDevicePairingModel()
  private var wizardAction: WizardAction? = null

  private var pairedDevicesAreOnline = false
  private var pairedPhoneDevice: PairingDevice? = null
  private var pairedWearDevice: PairingDevice? = null

  @Synchronized
  fun setDeviceListListener(model: WearDevicePairingModel,
                            wizardAction: WizardAction) {
    this.model = model
    this.wizardAction = wizardAction

    AndroidDebugBridge.addDeviceChangeListener(this)
    runningJob?.cancel(null) // Don't reuse pending job, in case it's stuck on a slow operation (eg bridging devices)
    runningJob = GlobalScope.launch(Dispatchers.IO) {
      for (operation in updateDevicesChannel) {
        try {
          updateListAndForwardState()
        }
        catch (ex: Throwable) {
          LOG.warn(ex)
        }
      }
    }

    updateDevicesChannel.offer(Unit)
  }

  @Synchronized
  fun setPairedDevices(phone: PairingDevice, wear: PairingDevice) {
    pairedPhoneDevice = phone.disconnectedCopy(isPaired = true)
    pairedWearDevice = wear.disconnectedCopy(isPaired = true)
    pairedDevicesAreOnline = true
  }

  @Synchronized
  fun getPairedDevices(): Pair<PairingDevice?, PairingDevice?> {
    return Pair(pairedPhoneDevice, pairedWearDevice)
  }

  @Synchronized
  suspend fun removePairedDevices() {
    try {
      val phoneDeviceID = pairedPhoneDevice?.deviceID ?: return
      val wearDeviceID = pairedWearDevice?.deviceID ?: return

      pairedPhoneDevice = null
      pairedWearDevice = null

      val connectedDevices = getConnectedDevices()
      connectedDevices[phoneDeviceID]?.apply {
        LOG.warn("[$name] Remove AUTO-forward")
        runCatching { removeForward(5601, 5601) }
      }
      connectedDevices[wearDeviceID]?.apply {
        restartGmsCore(this)
      }
    }
    catch (ex: Throwable) {
      LOG.warn(ex)
    }

    updateDevicesChannel.offer(Unit)
  }

  override fun deviceConnected(device: IDevice) {
    updateDevicesChannel.offer(Unit)
  }

  override fun deviceDisconnected(device: IDevice) {
    updateDevicesChannel.offer(Unit)
  }

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    updateDevicesChannel.offer(Unit)
  }

  @Slow
  private suspend fun updateListAndForwardState() {
    @Suppress("UnstableApiUsage")
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val deviceTable = hashMapOf<String, PairingDevice>()

    // Collect list of all available AVDs
    AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(false).filter { it.isWearOrPhone() }.forEach { avdInfo ->
      val deviceID = avdInfo.name
      deviceTable[deviceID] = avdInfo.toPairingDevice(deviceID, isPaired(deviceID))
    }

    // Collect list of all connected devices. Enrich data with previous collected AVDs.
    val connectedDevices = getConnectedDevices()
    connectedDevices.forEach { (deviceID, iDevice) ->
      val avdDevice = deviceTable[deviceID]
      // Note: Emulators IDevice "Hardware" feature returns "emulator" (instead of TV, WEAR, etc), so we only check for physical devices
      if ((!iDevice.isEmulator && iDevice.isWearOrPhone()) || avdDevice != null) {
        deviceTable[deviceID] = iDevice.toPairingDevice(deviceID, isPaired(deviceID), avdDevice = avdDevice)
      }
    }

    addDisconnectedPairedDeviceIfMissing(pairedPhoneDevice, deviceTable)
    addDisconnectedPairedDeviceIfMissing(pairedWearDevice, deviceTable)

    // Broadcast data to listeners
    val (wears, phones) = deviceTable.values.sortedBy { it.displayName }.partition { it.isWearDevice }
    model.phoneList.set(phones)
    model.wearList.set(wears)
    updateSelectedDevice(phones, model.selectedPhoneDevice)
    updateSelectedDevice(wears, model.selectedWearDevice)

    updateForwardState(connectedDevices)
  }

  private fun getConnectedDevices(): Map<String, IDevice> {
    val connectedDevices = AndroidDebugBridge.getBridge()?.devices ?: return emptyMap()
    return connectedDevices
      .filter { it.isEmulator || it.arePropertiesSet() } // Ignore un-populated physical devices (still loading properties)
      .associateBy { it.getDeviceID() }
  }

  private suspend fun updateForwardState(onlineDevices: Map<String, IDevice>) {
    val pairedPhoneDevice = this.pairedPhoneDevice ?: return
    val pairedWearDevice = this.pairedWearDevice ?: return
    val onlinePhone = onlineDevices[pairedPhoneDevice.deviceID]
    val onlineWear = onlineDevices[pairedWearDevice.deviceID]
    val bothDeviceOnline = onlinePhone != null && onlineWear != null
    try {
      if (bothDeviceOnline && !pairedDevicesAreOnline) {
        // Both device are online, and before one (or both) were offline. Time to bridge
        createDeviceBridge(onlinePhone!!, onlineWear!!)
        showReconnectMessageBalloon(pairedPhoneDevice.displayName, pairedWearDevice.displayName, wizardAction)
      }
      else if (!bothDeviceOnline && pairedDevicesAreOnline) {
        // One (or both) devices are offline, and before were online. Show "connection dropped" message
        val offlineName = if (onlinePhone == null) pairedPhoneDevice.displayName else pairedWearDevice.displayName
        showConnectionDroppedBalloon(offlineName, pairedPhoneDevice.displayName, pairedWearDevice.displayName, wizardAction)
      }
    }
    catch (ex: Throwable) {
      LOG.warn(ex)
    }

    pairedDevicesAreOnline = bothDeviceOnline
  }

  private fun isPaired(deviceID: String): Boolean = (deviceID == pairedPhoneDevice?.deviceID || deviceID == pairedWearDevice?.deviceID)

  private suspend fun addDisconnectedPairedDeviceIfMissing(device: PairingDevice?, deviceTable: HashMap<String, PairingDevice>) {
    val deviceID = device?.deviceID ?: return
    if (!deviceTable.contains(deviceID)) {
      if (device.isEmulator) {
         removePairedDevices() // Paired AVD was deleted/renamed - Don't add to the list and stop tracking its activity
      }
      else {
        deviceTable[deviceID] = device // Paired physical device - Add to be shown as "disconnected"
      }
    }
  }
}

private const val GMS_PACKAGE = "com.google.android.gms"

suspend fun IDevice.executeShellCommand(cmd: String) {
  withContext(Dispatchers.IO) {
    runCatching {
      executeShellCommand(cmd, NullOutputReceiver())
    }
  }
}

suspend fun IDevice.runShellCommand(cmd: String): String = withContext(Dispatchers.IO) {
  val outputReceiver = CollectingOutputReceiver()
  runCatching {
    executeShellCommand(cmd, outputReceiver)
  }
  outputReceiver.output.trim().apply {
  }
}

suspend fun IDevice.loadNodeID(): String {
  val localIdPattern = "local: "
  val output = runShellCommand("dumpsys activity service WearableService | grep '$localIdPattern'")
  return output.replace(localIdPattern, "").trim()
}

suspend fun IDevice.loadCloudNetworkID(): String {
  val cloudNetworkIdPattern = "cloud network id: "
  val output = runShellCommand("dumpsys activity service WearableService | grep '$cloudNetworkIdPattern'")
  return output.replace(cloudNetworkIdPattern, "").replace("null", "").trim()
}
suspend fun IDevice.retrieveUpTime(): Double {
  runCatching {
    val uptimeRes = runShellCommand("cat /proc/uptime")
    return uptimeRes.split(' ').firstOrNull()?.toDoubleOrNull() ?: 0.0
  }
  return 0.0
}

suspend fun createDeviceBridge(phoneDevice: IDevice, wearDevice: IDevice) {
  phoneDevice.runCatching { createForward(5601, 5601) }
  restartGmsCore(wearDevice)
}

private suspend fun killGmsCore(device: IDevice) {
  runCatching {
    val uptime = device.retrieveUpTime()
    // Killing gmsCore during cold boot will hang booting for a while, so skip it
    if (uptime > 120.0) {
      LOG.warn("[${device.name}] Killing Google Play Services/gmsCore")
      device.executeShellCommand("am force-stop $GMS_PACKAGE")
    }
    else {
      LOG.warn("[${device.name}] Skip killing Google Play Services/gmsCore (uptime = $uptime)")
    }
  }
}

private suspend fun restartGmsCore(device: IDevice) {
  killGmsCore(device)

  LOG.warn("[${device.name}] Wait for Google Play Services/gmsCore re-start")
  val res = withTimeoutOrNull(30_000) {
    while (device.loadNodeID().isEmpty()) {
      // Restart in case it doesn't restart automatically
      device.executeShellCommand("am broadcast -a $GMS_PACKAGE.INITIALIZE")
      delay(1_000)
    }
    true
  }
  when (res) {
    true -> LOG.warn("[${device.name}] Google Play Services/gmsCore started")
    else -> LOG.warn("[${device.name}] Google Play Services/gmsCore never started")
  }
}

private fun IDevice.toPairingDevice(deviceID: String, isPared: Boolean, avdDevice: PairingDevice?): PairingDevice {
  return PairingDevice(
    deviceID = deviceID,
    displayName = avdDevice?.displayName ?: getDeviceName(name),
    apiLevel = avdDevice?.apiLevel ?: version.featureLevel,
    isEmulator = isEmulator,
    isWearDevice = avdDevice?.isWearDevice ?: supportsFeature(HardwareFeature.WATCH),
    state = if (isOnline) ONLINE else OFFLINE,
    hasPlayStore = avdDevice?.hasPlayStore ?: false,
    isPaired = isPared
  ).apply {
    launch = { Futures.immediateFuture(this@toPairingDevice) }
  }
}

private fun AvdInfo.toPairingDevice(deviceID: String, isPared: Boolean): PairingDevice {
  return PairingDevice(
    deviceID = deviceID,
    displayName = displayName,
    apiLevel = androidVersion.featureLevel,
    isEmulator = true,
    isWearDevice = SystemImage.WEAR_TAG == tag,
    state = OFFLINE,
    hasPlayStore = hasPlayStore(),
    isPaired = isPared
  ).apply {
    launch = { project -> AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(project, this@toPairingDevice) }
  }
}

private fun IDevice.isWearOrPhone(): Boolean = when {
  supportsFeature(HardwareFeature.WATCH) -> true
  supportsFeature(HardwareFeature.TV) -> false
  supportsFeature(HardwareFeature.AUTOMOTIVE) -> false
  isEmulator -> throw RuntimeException("Call on emulator") // supportsFeature() only works for physical devices
  else -> true
}

private fun AvdInfo.isWearOrPhone(): Boolean = when (tag) {
  SystemImage.WEAR_TAG -> true
  SystemImage.ANDROID_TV_TAG -> false
  SystemImage.GOOGLE_TV_TAG -> false
  SystemImage.AUTOMOTIVE_TAG -> false
  SystemImage.AUTOMOTIVE_PLAY_STORE_TAG -> false
  SystemImage.CHROMEOS_TAG -> false
  else -> true
}

private fun IDevice.getDeviceName(unknown: String): String {
  val deviceName = "${getManufacturer(this, "")} ${getModel(this, "")}"
  return deviceName.ifBlank { unknown }
}

private fun IDevice.getDeviceID(): String {
  return when {
    avdName != null -> avdName!!
    isEmulator -> EmulatorConsole.getConsole(this)?.avdName ?: name
    else -> name
  }
}

private fun updateSelectedDevice(deviceList: List<PairingDevice>, device: OptionalProperty<PairingDevice>) {
  val currentDevice = device.valueOrNull ?: return
  // Assign the new value from the list, or if missing, update the current state to DISCONNECTED
  device.value = deviceList.firstOrNull { currentDevice.deviceID == it.deviceID } ?: currentDevice.disconnectedCopy()
}

private fun showReconnectMessageBalloon(phoneName: String, wearName: String, wizardAction: WizardAction?) =
  showMessageBalloon(
    message("wear.assistant.device.connection.reconnected.title"),
    message("wear.assistant.device.connection.reconnected.message", wearName, phoneName),
    wizardAction
  )

private fun showConnectionDroppedBalloon(offlineName: String, phoneName: String, wearName: String, wizardAction: WizardAction?) =
  showMessageBalloon(
    message("wear.assistant.device.connection.dropped.title"),
    message("wear.assistant.device.connection.dropped.message", offlineName, wearName, phoneName),
    wizardAction
  )

private fun showMessageBalloon(title: String, text: String, wizardAction: WizardAction?) {
  val hyperlink = object : NotificationHyperlink("launchAssistant", message("wear.assistant.device.connection.balloon.link")) {
    override fun execute(project: Project) {
      wizardAction?.restart(project)
    }
  }

  LOG.warn(text)
  ProjectManager.getInstance().openProjects.forEach {
    AndroidNotification.getInstance(it).showBalloon(title, "$text<br/>", INFORMATION, hyperlink)
  }
}