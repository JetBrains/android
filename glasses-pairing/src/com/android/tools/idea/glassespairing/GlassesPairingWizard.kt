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
package com.android.tools.idea.glassespairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.adblib.serialNumber
import com.android.adblib.tools.aiglasses.AiGlassesPairing
import com.android.adblib.tools.aiglasses.ShellCommandException
import com.android.sdklib.deviceprovisioner.DeviceActionException
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.LocalEmulatorDeviceHandle
import com.android.sdklib.deviceprovisioner.awaitReady
import com.android.sdklib.deviceprovisioner.mapChangedState
import com.android.sdklib.deviceprovisioner.pairWithNestedState
import com.android.tools.adtui.compose.ComposeWizard
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.google.wireless.android.sdk.stats.GlassesPairingEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import icons.StudioIconsCompose
import java.awt.Component
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text

@Stable
class GlassesPairingWizard
internal constructor(
  private val project: Project?,
  private val coroutineScope: CoroutineScope,
  devicesFlow: Flow<List<DeviceHandle>>,
  private val glassesHandle: DeviceHandle,
  private val pair: (glasses: DeviceHandle, phone: DeviceHandle) -> Flow<PairingState> =
    ::pairGlassesToPhone,
  private val isCompatible: (DeviceHandle) -> Boolean = ::isAiGlassesCompatible,
) {
  companion object {
    /**
     * Shows the Glasses Pairing wizard dialog, returning the paired phone if pairing is successful.
     */
    suspend fun show(
      parent: Component?,
      project: Project?,
      devicesFlow: Flow<List<DeviceHandle>>,
      glassesHandle: DeviceHandle,
    ): DeviceHandle? {
      if (!StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.get()) {
        return null
      }

      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ASSISTANT_LAUNCHED)

      val coroutineScope = CoroutineScope(SupervisorJob())
      val model = GlassesPairingWizard(project, coroutineScope, devicesFlow, glassesHandle)
      val wizard =
        ComposeWizard(
          project,
          "Glasses Pairing Assistant",
          parent = parent,
          minimumSize = JBUI.size(400, 200),
          preferredSize = JBUI.size(600, 350),
        ) {
          with(model) { SelectDevicePage() }
        }
      try {
        if (wizard.showNonModal()) {
          return model.phone?.handle
        }
        return null
      } finally {
        coroutineScope.cancel()
      }
    }
  }

  private var phone: DeviceRow? by mutableStateOf(null)

  private val deviceRowFlow: StateFlow<ImmutableList<DeviceRow>> =
    devicesFlow
      .map { devices -> devices.filter { it != glassesHandle && isCompatible(it) } }
      .pairWithNestedState { it.stateFlow }
      .mapChangedState { handle, state -> DeviceRow(handle, state) }
      .stateIn(coroutineScope, SharingStarted.Eagerly, persistentListOf())

  private data class PairingArgs(val glasses: DeviceHandle, val phone: DeviceHandle)

  private val pairingTrigger = MutableSharedFlow<PairingArgs>()

  private val pairingFlow: StateFlow<PairingState> =
    pairingTrigger
      .flatMapLatest {
        pair(it.glasses, it.phone).catch { cause ->
          emit(PairingState.Error("Unexpected error: $cause"))
        }
      }
      .distinctUntilChanged()
      .onEach {
        when (it) {
          is PairingState.AwaitingAuthorization ->
            phone?.handle?.let { project?.userInvolvementRequired(it) }
          is PairingState.Error ->
            GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.SHOW_FAILED_PAIRING)
          is PairingState.Complete ->
            GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.SHOW_SUCCESSFUL_PAIRING)
          else -> {}
        }
      }
      .stateIn(
        coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = PairingState.NotStarted,
      )

  @Composable
  internal fun WizardPageScope.SelectDevicePage() {
    val devices: ImmutableList<DeviceRow> by deviceRowFlow.collectAsState()

    val state = getOrCreateState { SelectableLazyListState(LazyListState()) }
    Column(Modifier.padding(20.dp)) {
      if (devices.isEmpty()) {
        PairingStateHorizontalProgress(
          header = "No compatible AVDs found.",
          detail =
            "Glasses pairing requires a Canary system image that includes AI Glasses support.\n\n" +
              "Please create one in Device Manager.",
          showProgressBar = false,
        )
      } else {
        LargeText("Select a device to pair", Modifier.padding(bottom = 8.dp))
        DeviceList(
          devices,
          onSelectedDeviceChange = {
            phone = it
            GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_DEVICE_SELECTED)
          },
          state,
        )
      }
    }
    nextAction =
      when (val phone = phone) {
        null -> WizardAction.Disabled
        else ->
          WizardAction {
            GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_INITIATED)
            coroutineScope.launch {
              pairingTrigger.emit(PairingArgs(glassesHandle, phone = phone.handle))
            }
            pushPage { Pair(phone) }
          }
      }
  }

  @Composable
  internal fun WizardPageScope.Pair(phone: DeviceRow) {
    val pairingState: PairingState by pairingFlow.collectAsState()

    PairingState(pairingState, phone)

    if (pairingState is PairingState.Complete) {
      enterFinishedState()
    }
    nextAction = WizardAction.Disabled
  }
}

@Composable
private fun PairingState(pairingState: PairingState, phone: DeviceRow) {
  Column(Modifier.padding(vertical = 20.dp, horizontal = 20.dp)) {
    when (pairingState) {
      is PairingState.AwaitingAuthorization -> {
        LargeText(pairingState.heading)

        Row(Modifier.padding(40.dp)) {
          CircularProgressIndicator()
          Spacer(Modifier.size(5.dp))
          Text(pairingState.detailText ?: "Waiting for user to accept permissions on ${phone.name}")
        }
      }
      is PairingState.Complete -> {
        Column(Modifier.fillMaxSize(), Arrangement.Center) {
          Icon(
            StudioIconsCompose.Common.Success,
            null,
            Modifier.size(100.dp).align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
          )
          LargeText(pairingState.heading, Modifier.align(Alignment.CenterHorizontally))
        }
      }
      else ->
        PairingStateHorizontalProgress(
          pairingState.heading,
          pairingState.detailText,
          pairingState !is PairingState.Complete && pairingState !is PairingState.Error,
        )
    }
  }
}

@Composable
private fun PairingStateHorizontalProgress(
  header: String,
  detail: String?,
  showProgressBar: Boolean,
) {
  LargeText(header)
  Box(Modifier.height(100.dp)) {
    if (showProgressBar) {
      IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth().align(Alignment.Center))
    }
  }
  detail?.let { Text(it, color = JewelTheme.globalColors.text.info) }
}

@Composable
private fun LargeText(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    fontWeight = FontWeight.SemiBold,
    fontSize = LocalTextStyle.current.fontSize * 1.2,
    modifier = modifier,
  )
}

internal sealed class PairingState {

  abstract val heading: String

  open val detailText: String?
    get() = null

  data object NotStarted : PairingState() {
    override val heading: String = "Preparing"
  }

  data class Launching(
    val phoneName: String,
    val phoneLaunchState: LaunchState,
    val glassesName: String,
    val glassesLaunchState: LaunchState,
  ) : PairingState() {
    override val heading: String = "Starting devices..."

    override val detailText
      get() =
        when (glassesLaunchState) {
          LaunchState.Ready -> phoneState()
          else -> glassesState()
        }

    fun phoneState(): String = stateText(phoneName, phoneLaunchState)

    fun glassesState(): String = stateText(glassesName, glassesLaunchState)

    private fun stateText(deviceName: String, deviceState: LaunchState): String =
      when (deviceState) {
        LaunchState.Waiting -> "Preparing to launch $deviceName"
        LaunchState.Launching -> "Starting $deviceName"
        LaunchState.Booting -> "Waiting for $deviceName to boot"
        LaunchState.Ready -> "$deviceName is ready"
      }
  }

  data class Pairing(override val detailText: String) : PairingState() {
    override val heading: String = "Establishing pairing..."
  }

  data object AwaitingAuthorization : PairingState() {
    override val heading: String = "Accept Permissions on Companion device"
  }

  data class Error(
    override val heading: String,
    override val detailText: String,
    val logDetail: String? = null,
  ) : PairingState() {
    constructor(detailText: String) : this("Pairing failed.", detailText)

    fun toLogMessage() = "$heading: $detailText${logDetail?.let { " [$it]" } ?: ""}"
  }

  data object Complete : PairingState() {
    override val heading: String = "Pairing complete."
  }
}

internal enum class LaunchState {
  Waiting,
  Launching,
  Booting,
  Ready,
}

internal fun launchAvd(handle: DeviceHandle): Flow<LaunchState> = flow {
  withTimeout(60.seconds) {
    handle.stateFlow.takeWhile { it.isTransitioning }.collect { emit(LaunchState.Waiting) }
  }
  if (handle.state.isReady) emit(LaunchState.Ready)
  else {
    emit(LaunchState.Launching)
    withTimeout(360.seconds) { handle.activationAction!!.activate() }
    emit(LaunchState.Booting)
    withTimeout(120.seconds) { handle.awaitReady() }
    emit(LaunchState.Ready)
  }
}

/** Indicates that the device requires user attention. */
internal fun Project.userInvolvementRequired(deviceHandle: DeviceHandle) {
  val connected = deviceHandle.state as? DeviceState.Connected ?: return
  val serialNumber = connected.connectedDevice.serialNumber
  messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(serialNumber, this)
}

private fun isAiGlassesCompatible(handle: DeviceHandle) =
  handle is LocalEmulatorDeviceHandle && handle.avdInfo.isAiGlassesCompatibleDevice

internal suspend fun FlowCollector<PairingState>.launchGlassesAndPhone(
  glasses: DeviceHandle,
  phone: DeviceHandle,
) = coroutineScope {
  val phoneName = phone.state.properties.title
  val glassesName = glasses.state.properties.title

  launchAvd(glasses)
    .combine(launchAvd(phone)) { glassesState, phoneState ->
      PairingState.Launching(phoneName, phoneState, glassesName, glassesState)
    }
    .onEach { emit(it) }
    .first {
      it.phoneLaunchState == LaunchState.Ready && it.glassesLaunchState == LaunchState.Ready
    }
}

internal fun pairGlassesToPhone(glasses: DeviceHandle, phone: DeviceHandle): Flow<PairingState> {
  val logger = logger<GlassesPairingWizard>()
  val phoneName = phone.state.properties.title
  val glassesName = glasses.state.properties.title
  return flow {
      try {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_LAUNCH_STARTED)
        launchGlassesAndPhone(glasses, phone)
      } catch (_: TimeoutCancellationException) {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_TIMEOUT)
        emit(PairingState.Error("Timed out waiting for devices to start."))
        return@flow
      } catch (e: DeviceActionException) {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_LAUNCH_FAILED)
        emit(PairingState.Error(e.message ?: "Failed to launch devices."))
        return@flow
      }

      val phoneDevice = phone.state.connectedDevice
      val glassesDevice = glasses.state.connectedDevice
      if (phoneDevice == null) {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_LAUNCH_FAILED)
        emit(PairingState.Error("$phoneName failed to launch."))
        return@flow
      }
      if (glassesDevice == null) {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_LAUNCH_FAILED)
        emit(PairingState.Error("$glassesName failed to launch."))
        return@flow
      }

      with(AiGlassesPairing(phoneDevice.session)) {
        if ((glassesDevice.getPairedBluetoothDeviceCount() ?: 0) > 0) {
          GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_ALREADY_PAIRED)
          emit(
            PairingState.Error(
              "Glasses already paired",
              "Wipe data on $glassesName to pair a new device.",
            )
          )
          return@flow
        }

        if (!phoneDevice.hasGlassesCompanionApp()) {
          GlassesPairingUsageTracker.log(
            GlassesPairingEvent.EventKind.PAIRING_ERROR_NO_COMPANION_APP
          )
          emit(PairingState.Error("$phoneName does not have support for Glasses."))
          return@flow
        }

        if ((phoneDevice.getPairedBluetoothDeviceCount() ?: 0) > 0) {
          phoneDevice.launchCompanionApp()
          try {
            phoneDevice.sendUnpairCommand()
          } catch (e: ShellCommandException) {}
        }

        // Reset any prior pairing attempts
        phoneDevice.clearGlassesPackages()
        delay(3.seconds)

        emit(PairingState.Pairing("Initiating pairing..."))

        if ((phoneDevice.getPairedBluetoothDeviceCount() ?: 0) > 0) {
          GlassesPairingUsageTracker.log(
            GlassesPairingEvent.EventKind.PAIRING_WARNING_PHONE_ALREADY_PAIRED
          )
          emit(
            PairingState.Pairing(
              "Warning: $phoneName already has a Bluetooth pairing; glasses pairing will likely fail."
            )
          )
          delay(3.seconds)
        }

        val glassesBluetoothAddress = glassesDevice.getBluetoothAddress()
        if (glassesBluetoothAddress == null) {
          GlassesPairingUsageTracker.log(
            GlassesPairingEvent.EventKind.PAIRING_ERROR_BLUETOOTH_ADDRESS
          )
          emit(PairingState.Error("Failed to retrieve Bluetooth address of $glassesName."))
          return@flow
        }

        val phoneBluetoothAddress = phoneDevice.getBluetoothAddress()
        // If phoneBluetoothAddress is null, we may not have access to it; we just have to proceed
        // and hope for the best.
        if (phoneBluetoothAddress == glassesBluetoothAddress) {
          emit(
            PairingState.Error(
              heading = "Network simulation error",
              detailText =
                "The same Bluetooth address has been assigned to both $phoneName and $glassesName. " +
                  "Please perform a Cold Boot on one device and try again.",
            )
          )
          return@flow
        }

        phoneDevice
          .pairToGlasses(glassesBluetoothAddress, true)
          .onEach { pairingState ->
            logger.debug("Polling pairing state: $pairingState")
            when (pairingState) {
              "PAIRED" -> emit(PairingState.Complete)
              "UI_CDM_ASSOCIATING" -> emit(PairingState.AwaitingAuthorization)
              in AiGlassesPairing.TERMINAL_STATES ->
                emit(
                  PairingState.Error(
                    heading = "Error pairing $glassesName",
                    detailText =
                      when (pairingState) {
                        "WORKER_BOND_FAILED" -> {
                          GlassesPairingUsageTracker.log(
                            GlassesPairingEvent.EventKind.PAIRING_ERROR_BOND_FAILED
                          )
                          "Failed to bond to device."
                        }
                        "WORKER_CONNECTION_FAILED" -> {
                          GlassesPairingUsageTracker.log(
                            GlassesPairingEvent.EventKind.PAIRING_ERROR_CONNECTION_FAILED
                          )
                          "Failed to connect to device."
                        }
                        "WORKER_CANCELLED" -> "Pairing was cancelled."
                        else -> "Error pairing device."
                      },
                    logDetail = pairingState,
                  )
                )
              else -> emit(PairingState.Pairing("Pairing in progress..."))
            }
          }
          .catch { cause ->
            if (cause is java.io.IOException) {
              emit(
                PairingState.Error(
                  heading = "Connection lost",
                  detailText =
                    "The connection to one or both of the devices was lost. Pairing may have still succeeded; please check the phone.",
                  logDetail = cause.message,
                )
              )
            } else {
              throw cause
            }
          }
          .first { it in AiGlassesPairing.TERMINAL_STATES }
      }
    }
    .onEach {
      when (it) {
        PairingState.NotStarted,
        is PairingState.Pairing -> {}
        is PairingState.Launching ->
          if (logger.isDebugEnabled) {
            logger.debug("Launching devices: ${it.phoneState()};  ${it.glassesState()}")
          }
        PairingState.AwaitingAuthorization -> {
          GlassesPairingUsageTracker.log(
            GlassesPairingEvent.EventKind.PAIRING_AWAITING_AUTHORIZATION
          )
          logger.debug("Awaiting authorization")
        }
        PairingState.Complete -> logger.info("Successfully paired $phoneName with $glassesName")
        is PairingState.Error -> logger.warn(it.toLogMessage())
      }
    }
}
