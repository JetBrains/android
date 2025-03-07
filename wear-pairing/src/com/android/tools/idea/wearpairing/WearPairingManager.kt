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
package com.android.tools.idea.wearpairing

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.EmulatorConsole
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.HardwareFeature
import com.android.sdklib.SystemImageTags
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.AndroidStartupActivity
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType
import com.android.tools.idea.avdmanager.AvdManagerConnection.Companion.getDefaultAvdManagerConnection
import com.android.tools.idea.ddms.DevicePropertyUtil.getManufacturer
import com.android.tools.idea.ddms.DevicePropertyUtil.getModel
import com.android.tools.idea.observable.core.OptionalProperty
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.net.NetUtils
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

private val LOG
  get() = logger<WearPairingManager>()

@Service(Service.Level.APP)
class WearPairingManager(
  private val coroutineScope: CoroutineScope,
  private val notificationsManager: WearPairingNotificationManager,
  private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val edtDispatcher: CoroutineContext = Dispatchers.EDT,
) : AndroidDebugBridge.IDeviceChangeListener, ObservablePairedDevicesList {

  constructor(
    coroutineScope: CoroutineScope
  ) : this(
    coroutineScope = coroutineScope,
    notificationsManager = WearPairingNotificationManager.getInstance(),
    defaultDispatcher = Dispatchers.Default,
    ioDispatcher = Dispatchers.IO,
    edtDispatcher = Dispatchers.EDT,
  )

  enum class PairingState {
    UNKNOWN,
    OFFLINE, // One or both device are offline/disconnected
    CONNECTING, // Both devices are online, and ADB bridge is set up
    CONNECTED, // End to end device pairing is set up
    PAIRING_FAILED, // Both devices are online, ADB bridge is set up, but don't seem to pair
  }

  interface PairingStatusChangedListener {
    fun pairingStatusChanged(phoneWearPair: PhoneWearPair)

    fun pairingDeviceRemoved(phoneWearPair: PhoneWearPair)
  }

  private val pairingStatusListeners = CopyOnWriteArrayList<PairingStatusChangedListener>()
  private val mutex = Mutex()

  private var runningJob: Job? = null
  private var model = WearDevicePairingModel()
  private var wizardAction: WizardAction? = null
  private var virtualDevicesProvider: () -> List<AvdInfo> = {
    getDefaultAvdManagerConnection().getAvds(false)
  }
  private var connectedDevicesProvider: suspend () -> List<IDevice> = suspend {
    findAdb()?.devices?.toList() ?: emptyList()
  }

  data class PhoneWearPair(val phone: PairingDevice, val wear: PairingDevice) {
    var pairingStatus = PairingState.UNKNOWN
    internal var hostPort = 0

    fun getPeerDevice(deviceID: String) =
      when (deviceID) {
        phone.deviceID -> wear
        wear.deviceID -> phone
        else -> throw AssertionError(deviceID)
      }

    fun contains(deviceID: String) = deviceID == phone.deviceID || deviceID == wear.deviceID
  }

  private val pairedDevicesList = mutableListOf<PhoneWearPair>()

  @TestOnly
  fun setDataProviders(
    virtualDevices: () -> List<AvdInfo>,
    connectedDevices: suspend () -> List<IDevice>,
  ) {
    virtualDevicesProvider = virtualDevices
    connectedDevicesProvider = connectedDevices
    pairedDevicesList.clear()
  }

  @WorkerThread
  private fun loadSettings() {
    ThreadingAssertions.assertBackgroundThread()

    WearPairingSettings.getInstance().apply {
      loadSettings(pairedDevicesState, pairedDeviceConnectionsState)
    }

    val wizardAction =
      object : WizardAction {
        override fun restart(project: Project?) {
          WearDevicePairingWizard().show(project, null)
        }
      }
    // Launch WearPairingManager
    setDeviceListListener(WearDevicePairingModel(), wizardAction)
  }

  fun loadSettings(
    pairedDevices: List<PairingDeviceState>,
    pairedDeviceConnections: List<PairingConnectionsState>,
  ) {
    pairedDevicesList.clear()
    val deviceMap = pairedDevices.associateBy { it.deviceID }

    pairedDeviceConnections.forEach { connection ->
      val phoneId = connection.phoneId
      val phone = deviceMap[phoneId]!!.toPairingDevice(ConnectionState.DISCONNECTED)
      connection.wearDeviceIds.forEach { wearId ->
        val phoneWearPair =
          PhoneWearPair(
            phone = phone,
            wear = deviceMap[wearId]!!.toPairingDevice(ConnectionState.DISCONNECTED),
          )
        updatePairingStatus(phoneWearPair, PairingState.OFFLINE)
        pairedDevicesList.add(phoneWearPair)
      }
    }
  }

  private fun saveSettings() {
    val pairedDevicesState = mutableListOf<PairingDeviceState>()
    val pairedDeviceConnectionsState = ArrayList<PairingConnectionsState>()
    val phoneToWearPairs = pairedDevicesList.groupBy { it.phone.deviceID }

    phoneToWearPairs.forEach { (_, phoneWearPairs) ->
      pairedDevicesState.add(phoneWearPairs[0].phone.toPairingDeviceState())
      val pairingConnectionsState =
        PairingConnectionsState().apply { phoneId = phoneWearPairs[0].phone.deviceID }
      phoneWearPairs.forEach { phoneWearPair ->
        pairedDevicesState.add(phoneWearPair.wear.toPairingDeviceState())
        pairingConnectionsState.wearDeviceIds.add(phoneWearPair.wear.deviceID)
      }
      pairedDeviceConnectionsState.add(pairingConnectionsState)
    }

    WearPairingSettings.getInstance().let {
      it.pairedDevicesState = pairedDevicesState
      it.pairedDeviceConnectionsState = pairedDeviceConnectionsState
    }
  }

  @Synchronized
  fun setDeviceListListener(model: WearDevicePairingModel, wizardAction: WizardAction) {
    this.model = model
    this.wizardAction = wizardAction

    AndroidDebugBridge.addDeviceChangeListener(this)
    runningJob?.cancel(
      null
    ) // Don't reuse pending job, in case it's stuck on a slow operation (eg bridging devices)
    runningJob =
      coroutineScope.launch(defaultDispatcher) {
        while (isActive) {
          try {
            updateListAndForwardState()
          } catch (ex: Throwable) {
            LOG.warn(ex)
          }
          delay(PERIODIC_UPDATE_INTERVAL)
        }
      }
  }

  @Synchronized
  override fun addDevicePairingStatusChangedListener(listener: PairingStatusChangedListener) {
    pairingStatusListeners.addIfAbsent(listener)

    pairedDevicesList.forEach(listener::pairingStatusChanged)
  }

  @Synchronized
  override fun removeDevicePairingStatusChangedListener(listener: PairingStatusChangedListener) {
    pairingStatusListeners.remove(listener)
  }

  private fun updatePairingStatus(phoneWearPair: PhoneWearPair, newState: PairingState) {
    if (phoneWearPair.pairingStatus == newState) {
      return
    }
    phoneWearPair.pairingStatus = newState
    pairingStatusListeners.forEach { it.pairingStatusChanged(phoneWearPair) }
  }

  fun getPairsForDevice(deviceID: String): List<PhoneWearPair> =
    pairedDevicesList.filter { it.phone.deviceID == deviceID || it.wear.deviceID == deviceID }

  suspend fun createPairedDeviceBridge(
    phone: PairingDevice,
    phoneDevice: IDevice,
    wear: PairingDevice,
    wearDevice: IDevice,
    connect: Boolean = true,
  ) =
    withContext(ioDispatcher) {
      LOG.warn("Starting device bridge {connect = $connect}")
      removeAllPairedDevices(wear.deviceID, restartWearGmsCore = false)

      val hostPort = NetUtils.tryToFindAvailableSocketPort(5602)
      val phoneWearPair =
        PhoneWearPair(phone = phone.disconnectedCopy(), wear = wear.disconnectedCopy())
      phoneWearPair.hostPort = hostPort
      updatePairingStatus(phoneWearPair, PairingState.CONNECTING)

      mutex.withLock {
        pairedDevicesList.add(phoneWearPair)
        saveSettings()
      }

      if (connect) {
        try {
          LOG.warn("Creating adb bridge")
          phoneDevice.createForward(hostPort, 5601)
          wearDevice.createReverse(5601, hostPort)
          wearDevice.refreshEmulatorConnection()
          updateDeviceStatus(phoneWearPair, phoneDevice, wearDevice)
        } catch (ex: Throwable) {
          throw IOException(ex)
        }
      }

      phoneWearPair
    }

  suspend fun updateDeviceStatus(
    phoneWearPair: PhoneWearPair,
    phoneDevice: IDevice,
    wearDevice: IDevice,
  ): PairingState {
    val state =
      withTimeoutOrNull(5_000) {
        while (!checkDevicesPaired(phoneDevice, wearDevice)) {
          delay(1000)
        }
        PairingState.CONNECTED
      } ?: PairingState.PAIRING_FAILED

    updatePairingStatus(phoneWearPair, state)
    return state
  }

  suspend fun removeAllPairedDevices(deviceID: String, restartWearGmsCore: Boolean = true) =
    getPairsForDevice(deviceID).forEach {
      removePairedDevices(it, restartWearGmsCore = restartWearGmsCore)
    }

  suspend fun removePairedDevices(
    phoneId: String,
    wearId: String,
    restartWearGmsCore: Boolean = true,
  ) {
    val phoneWearPair =
      mutex.withLock {
        pairedDevicesList.find { it.phone.deviceID == phoneId && it.wear.deviceID == wearId }
      } ?: return

    removePairedDevices(phoneWearPair, restartWearGmsCore)
  }

  suspend fun removePairedDevices(
    phoneWearPair: PhoneWearPair,
    restartWearGmsCore: Boolean = true,
  ): Unit =
    withContext(defaultDispatcher) {
      try {
        mutex.withLock {
          pairedDevicesList.removeAll {
            it.phone.deviceID == phoneWearPair.phone.deviceID &&
              it.wear.deviceID == phoneWearPair.wear.deviceID
          }
        }
        pairingStatusListeners.forEach { it.pairingDeviceRemoved(phoneWearPair) }
        mutex.withLock { saveSettings() }

        val connectedDevices = getConnectedDevices()
        val phoneDevice = connectedDevices[phoneWearPair.phone.deviceID]
        val wearDevice = connectedDevices[phoneWearPair.wear.deviceID]
        phoneDevice?.apply {
          LOG.warn("[$name] Remove AUTO-forward")
          runCatching {
            removeForward(5601)
          } // Make sure there is no manual connection hanging around
          runCatching { if (phoneWearPair.hostPort > 0) removeForward(phoneWearPair.hostPort) }
          if (wearDevice?.getCompanionAppIdForWatch() == PIXEL_COMPANION_APP_ID) {
            // The Pixel OEM app will re-connect via CloudSync even if we unpair. This will ensure
            // that the data for the Companion app is cleared forcing unpair to happen.
            runShellCommand("pm clear com.google.android.apps.wear.companion")
          }
        }

        wearDevice?.apply {
          LOG.warn("[$name] Remove AUTO-reverse")
          runCatching { removeReverse(5601) }
          if (restartWearGmsCore) {
            refreshEmulatorConnection()
          }
        }
      } catch (ex: Throwable) {
        LOG.warn(ex)
      }
      updateListAndForwardState()
    }

  override fun deviceConnected(device: IDevice) {
    coroutineScope.launch { updateListAndForwardState() }
  }

  override fun deviceDisconnected(device: IDevice) {
    coroutineScope.launch { updateListAndForwardState() }
  }

  override fun deviceChanged(device: IDevice, changeMask: Int) {
    coroutineScope.launch { updateListAndForwardState() }
  }

  internal suspend fun findDevice(deviceID: String): PairingDevice? =
    getAvailableDevices().second[deviceID]

  private suspend fun getAvailableDevices() =
    withContext(defaultDispatcher) {
      val deviceTable = hashMapOf<String, PairingDevice>()

      // Collect list of all available AVDs
      virtualDevicesProvider()
        .filter { it.isWearOrPhone() }
        .forEach { avdInfo ->
          val deviceID = avdInfo.id
          deviceTable[deviceID] = avdInfo.toPairingDevice(deviceID)
        }

      // Collect list of all connected devices. Enrich data with previous collected AVDs.
      val connectedDevices = getConnectedDevices()
      connectedDevices.forEach { (deviceID, iDevice) ->
        val avdDevice = deviceTable[deviceID]
        // Note: Emulators IDevice "Hardware" feature returns "emulator" (instead of TV, WEAR,
        // etc.), so we only check for physical devices
        if (iDevice.isPhysicalPhone() || avdDevice != null) {
          deviceTable[deviceID] = iDevice.toPairingDevice(deviceID, avdDevice = avdDevice)
        }
      }

      Pair(connectedDevices, deviceTable)
    }

  private suspend fun updateListAndForwardState() =
    withContext(defaultDispatcher) {
      try {
        val (connectedDevices, deviceTable) = getAvailableDevices()

        // Don't loop directly on the list, because its values may be updated (ie added/removed)
        pairedDevicesList.toList().forEach { phoneWearPair ->
          addDisconnectedPairedDeviceIfMissing(phoneWearPair.phone, deviceTable)
          addDisconnectedPairedDeviceIfMissing(phoneWearPair.wear, deviceTable)
        }

        withContext(edtDispatcher + ModalityState.any().asContextElement()) {
          // Broadcast data to listeners
          val (wears, phones) =
            deviceTable.values.sortedBy { it.displayName }.partition { it.isWearDevice }
          model.phoneList.set(phones)
          model.wearList.set(wears)
          updateSelectedDevice(phones, model.selectedPhoneDevice)
          updateSelectedDevice(wears, model.selectedWearDevice)

          // Don't loop directly on the list, because its values may be updated (ie added/removed)
          pairedDevicesList.toList().forEach { phoneWearPair ->
            updateForwardState(phoneWearPair, connectedDevices)
          }
        }
      } catch (ex: Throwable) {
        LOG.warn(ex)
      }
    }

  internal suspend fun launchDevice(project: Project?, deviceId: String, avdInfo: AvdInfo) =
    withContext(defaultDispatcher) {
      connectedDevicesProvider()
        .find { it.getDeviceID() == deviceId }
        ?.apply {
          return@withContext this
        }
      getDefaultAvdManagerConnection().startAvd(project, avdInfo, RequestType.DIRECT_DEVICE_MANAGER)
    }

  private suspend fun findAdb(): AndroidDebugBridge? {
    AndroidDebugBridge.getBridge()?.also {
      return it // Instance found, just return it
    }
    return withContext(defaultDispatcher) {
      AndroidSdkUtils.findAdb(null).adbPath?.let {
        try {
          AdbService.getInstance().getDebugBridge(it).await()
        } catch (e: Exception) {
          LOG.warn(e)
          null
        }
      }
    }
  }

  private suspend fun getConnectedDevices() =
    withContext(defaultDispatcher) {
      connectedDevicesProvider().filter { it.isOnline }.associateBy { it.getDeviceID() }
    }

  private suspend fun updateForwardState(
    phoneWearPair: PhoneWearPair,
    onlineDevices: Map<String, IDevice>,
  ) {
    val onlinePhone = onlineDevices[phoneWearPair.phone.deviceID]
    val onlineWear = onlineDevices[phoneWearPair.wear.deviceID]
    try {
      if (onlinePhone != null && onlineWear != null) { // Are both devices online?
        if (phoneWearPair.pairingStatus == PairingState.OFFLINE) {
          // Both devices are online, and before one (or both) were offline. Time to bridge.
          createPairedDeviceBridge(phoneWearPair.phone, onlinePhone, phoneWearPair.wear, onlineWear)
          notificationsManager.showReconnectMessageBalloon(phoneWearPair, wizardAction)
        } else {
          // Check if pairing was removed from the companion app and if pairing is still OK.
          updateDeviceStatus(phoneWearPair, onlinePhone, onlineWear)
        }
      } else if (phoneWearPair.pairingStatus != PairingState.OFFLINE) {
        // One (or both) devices are offline, and before were online. Show "connection dropped"
        // message
        updatePairingStatus(phoneWearPair, PairingState.OFFLINE)
        val offlineName =
          if (onlinePhone == null) phoneWearPair.phone.displayName
          else phoneWearPair.wear.displayName
        notificationsManager.showConnectionDroppedBalloon(offlineName, phoneWearPair, wizardAction)
      }
    } catch (ex: Throwable) {
      LOG.warn(ex)
    }
  }

  private suspend fun addDisconnectedPairedDeviceIfMissing(
    device: PairingDevice,
    deviceTable: HashMap<String, PairingDevice>,
  ) =
    withContext(defaultDispatcher) {
      val deviceID = device.deviceID
      if (!deviceTable.contains(deviceID)) {
        if (device.isEmulator) {
          removeAllPairedDevices(
            deviceID
          ) // Paired AVD was deleted/renamed - Don't add to the list and stop tracking its activity
        } else if (device.isDirectAccessDevice()) {
          val deviceHasNewSession =
            deviceTable.values
              .filter { it.isDirectAccessDevice() && it.displayName == device.displayName }
              .any { it.deviceID != deviceID }

          // Direct Access Devices are wiped after each session is disconnected and expired.
          // If another Direct Access Device exists in the device table with the same name as the
          // current device, then we can assume that the pairing is no longer valid as the
          // session associated to the disconnected device has expired and has been wiped.
          // Otherwise, keep the pairing in case the user reclaims the session, but don't add it to
          // the list.
          if (deviceHasNewSession) {
            removeAllPairedDevices(deviceID)
          }
        } else {
          deviceTable[deviceID] =
            device // Paired physical device - Add to be shown as "disconnected"
        }
      }
    }

  private suspend fun IDevice.getDeviceID() =
    withContext(defaultDispatcher) {
      when {
        // normalizeAvdId is applied to the returned path from the AVD data to remove any .. in the
        // path. They were added in https://r.android.com/2441481 and, since we use the path as an
        // ID, the .. does not match the path information we have in Studio.
        // We intentionally use normalize since it does not access disk and will just normalize the
        // path removing the ..
        isEmulator && avdData?.isDone == true ->
          avdData.get()?.avdFolder?.normalize()?.toString() ?: name
        isEmulator ->
          EmulatorConsole.getConsole(this@getDeviceID)?.avdNioPath?.normalize()?.toString() ?: name
        getProperty(PROP_FIREBASE_TEST_LAB_SESSION) != null ->
          getProperty(PROP_FIREBASE_TEST_LAB_SESSION) ?: name
        else -> {
          val matcher = WIFI_DEVICE_SERIAL_PATTERN.matcher(serialNumber)
          if (matcher.matches()) matcher.group(1) else serialNumber
        }
      }
    }

  class WearPairingManagerStartupActivity : AndroidStartupActivity {
    @UiThread
    override fun runActivity(project: Project, disposable: Disposable) {
      val wearPairingManager = getInstance()
      NonUrgentExecutor.getInstance().execute {
        synchronized(wearPairingManager) {
          if (wearPairingManager.runningJob == null) {
            wearPairingManager.loadSettings()
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): WearPairingManager =
      ApplicationManager.getApplication().getService(WearPairingManager::class.java)

    private val PERIODIC_UPDATE_INTERVAL = 60.seconds
  }
}

private fun IDevice.toPairingDevice(deviceID: String, avdDevice: PairingDevice?): PairingDevice {
  return PairingDevice(
      deviceID = deviceID,
      displayName = avdDevice?.displayName ?: getDeviceName(name),
      androidVersion = avdDevice?.androidVersion ?: version,
      isEmulator = isEmulator,
      isWearDevice = avdDevice?.isWearDevice ?: supportsFeature(HardwareFeature.WATCH),
      state = if (isOnline) ConnectionState.ONLINE else ConnectionState.OFFLINE,
      hasPlayStore = avdDevice?.hasPlayStore ?: false,
    )
    .apply { launch = { this@toPairingDevice } }
}

private fun AvdInfo.toPairingDevice(deviceID: String): PairingDevice {
  return PairingDevice(
      deviceID = deviceID,
      displayName = displayName,
      androidVersion = androidVersion,
      isEmulator = true,
      isWearDevice = SystemImageTags.isWearImage(tags),
      state = ConnectionState.OFFLINE,
      hasPlayStore = hasPlayStore(),
    )
    .apply {
      launch = { project ->
        WearPairingManager.getInstance().launchDevice(project, deviceID, this@toPairingDevice)
      }
    }
}

private fun PairingDevice.isDirectAccessDevice() =
  !isWearDevice && deviceID.matches(Regex("projects/.+/deviceSessions/session-.*"))

private fun IDevice.isPhysicalPhone(): Boolean =
  when {
    isEmulator -> false
    supportsFeature(HardwareFeature.WATCH) -> false
    supportsFeature(HardwareFeature.TV) -> false
    supportsFeature(HardwareFeature.AUTOMOTIVE) -> false
    else -> true
  }

internal fun AvdInfo.isWearOrPhone(): Boolean =
  when (tag) {
    SystemImageTags.WEAR_TAG -> true
    SystemImageTags.DESKTOP_TAG -> false
    SystemImageTags.ANDROID_TV_TAG -> false
    SystemImageTags.GOOGLE_TV_TAG -> false
    SystemImageTags.AUTOMOTIVE_TAG -> false
    SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG -> false
    SystemImageTags.CHROMEOS_TAG -> false
    else -> true
  }

private fun IDevice.getDeviceName(unknown: String): String {
  val model = getModel(this, "")
  val manufacturer = getManufacturer(this, "")
  val deviceName = if (model.startsWith(manufacturer, true)) model else "$manufacturer $model"
  return deviceName.ifBlank { unknown }
}

private val WIFI_DEVICE_SERIAL_PATTERN =
  Pattern.compile("adb-(.*)-.*\\._adb-tls-connect\\._tcp\\.?")
@VisibleForTesting
internal const val PROP_FIREBASE_TEST_LAB_SESSION = "debug.firebase.test.lab.session"

private fun updateSelectedDevice(
  deviceList: List<PairingDevice>,
  device: OptionalProperty<PairingDevice>,
) {
  val currentDevice = device.valueOrNull ?: return
  // Assign the new value from the list, or if missing, update the current state to DISCONNECTED
  device.value =
    deviceList.firstOrNull { currentDevice.deviceID == it.deviceID }
      ?: currentDevice.disconnectedCopy()
}

interface ObservablePairedDevicesList {
  fun addDevicePairingStatusChangedListener(
    listener: WearPairingManager.PairingStatusChangedListener
  )

  fun removeDevicePairingStatusChangedListener(
    listener: WearPairingManager.PairingStatusChangedListener
  )
}
