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
package com.android.tools.idea.avd.glassespairing

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.adblib.ConnectedDevice
import com.android.adblib.serialNumber
import com.android.adblib.tools.aiglasses.AiGlassesPairing
import com.android.adblib.tools.aiglasses.ShellCommandException
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageTags
import com.android.sdklib.deviceprovisioner.AbstractAvdScanner
import com.android.sdklib.deviceprovisioner.DeviceActionException
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.LocalEmulatorProperties
import com.android.sdklib.deviceprovisioner.awaitReady
import com.android.sdklib.deviceprovisioner.mapChangedState
import com.android.sdklib.deviceprovisioner.pairWithNestedState
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.adtui.compose.ComposeWizard
import com.android.tools.adtui.compose.WizardAction
import com.android.tools.adtui.compose.WizardPageScope
import com.android.tools.idea.adddevicedialog.FormFactors
import com.android.tools.idea.avd.VirtualDeviceProfile
import com.android.tools.idea.avd.showAddDeviceDialog
import com.android.tools.idea.avdmanager.AvdScannerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GlassesPairingEvent
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import icons.StudioIconsCompose
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import java.io.IOException
import java.text.Collator
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

private const val GLASSES_PAIRING_AUTH_IMAGE_PATH = "/screens/glasses_auth.png"
private const val GLASSES_CORE_CONNECTING_IMAGE_PATH = "/screens/glasses_core.png"

internal interface WizardController {
  suspend fun show(): Boolean
}

private class ComposeWizardController(val wizard: ComposeWizard) : WizardController {
  override suspend fun show() = wizard.showNonModal()
}

fun interface AddDeviceDialog {
  suspend fun show(
    project: Project?,
    parent: Component?,
    virtualDeviceFilter: (VirtualDeviceProfile) -> Boolean,
    systemImageFilter: (ISystemImage) -> Boolean,
  ): AvdInfo?
}

// Data class to carry the result of the wizard, allowing both the phone and MAC
// to be propagated back to the provisioner plugin without re-fetching via ADB.
data class GlassesPairingResult(val phone: DeviceHandle, val glassesMacAddress: String)

internal interface GlassesPairer {
  fun pair(glasses: DeviceHandle, phone: DeviceHandle, project: Project?, onMacRetrieved: (String) -> Unit): Flow<PairingState>
}

internal object DefaultGlassesPairer : GlassesPairer {
  override fun pair(glasses: DeviceHandle, phone: DeviceHandle, project: Project?, onMacRetrieved: (String) -> Unit): Flow<PairingState> {
    return pairGlassesToPhone(glasses, phone, project, onMacRetrieved = onMacRetrieved)
  }
}

@Stable
class GlassesPairingWizard
internal constructor(
  private val project: Project?,
  private val coroutineScope: CoroutineScope,
  devicesFlow: Flow<List<DeviceHandle>>,
  private val glassesHandle: DeviceHandle,
  private val pairer: GlassesPairer = DefaultGlassesPairer,
  private val isCompatible: (DeviceHandle) -> Boolean = ::isAiGlassesCompatible,
  private val addDeviceDialog: AddDeviceDialog = AddDeviceDialog(::showAddDeviceDialog),
  private val avdScanner: () -> AbstractAvdScanner = { AvdScannerService.instance },
) {
  companion object {
    @VisibleForTesting
    fun resetForTesting() {
      service<GlassesPairingLockService>().setWizardOpen(false)
    }

    /**
     * Shows the Glasses Pairing wizard dialog, returning the paired phone if pairing is successful.
     *
     * Returns null if the wizard is cancelled or was already running.
     */
    @UiThread
    suspend fun show(
      parent: Component?,
      project: Project?,
      devicesFlow: Flow<List<DeviceHandle>>,
      glassesHandle: DeviceHandle,
    ): GlassesPairingResult? =
      showCore(parent, project, devicesFlow, glassesHandle) { p, t, par, min, pref, c ->
        ComposeWizardController(ComposeWizard(p, t, par, min, pref, c))
      }

    @UiThread
    internal suspend fun showCore(
      parent: Component?,
      project: Project?,
      devicesFlow: Flow<List<DeviceHandle>>,
      glassesHandle: DeviceHandle,
      factory: (Project?, String, Component?, Dimension, Dimension, @Composable WizardPageScope.() -> Unit) -> WizardController,
    ): GlassesPairingResult? {
      if (!StudioFlags.AI_GLASSES_PHONE_EMULATOR_PAIRING_WIZARD_ENABLED.get()) {
        return null
      }

      val lockService = service<GlassesPairingLockService>()
      // If a wizard is already running, return null.
      if (lockService.isWizardOpen.value) {
        return null
      }

      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ASSISTANT_LAUNCHED)
      val coroutineScope = CoroutineScope(SupervisorJob())
      val wizard = GlassesPairingWizard(project, coroutineScope, devicesFlow, glassesHandle)
      val controller =
        factory(project, "Glasses Pairing Assistant", parent, JBUI.size(400, 200), JBUI.size(800, 500)) {
          with(wizard) { SelectDevicePage() }
        }

      lockService.setWizardOpen(true)
      try {
        if (controller.show()) {
          val phoneHandle = wizard.phone?.handle ?: return null
          val mac = wizard.glassesMacAddress ?: return null
          val result = GlassesPairingResult(phoneHandle, mac)
          return result
        }
        return null
      } finally {
        // Ensure we cancel the wizard's scope and reset the global open state when it closes
        // (either normally or via exception).
        wizard.coroutineScope.cancel()
        lockService.setWizardOpen(false)
      }
    }
  }

  private var phone: DeviceRow? by mutableStateOf(null)
  @Volatile internal var glassesMacAddress: String? = null

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
        flow {
            try {
              // We use a large timeout (15 minutes) to account for potential slow cold boots of both devices,
              // which can take significant time on some machines/configurations (e.g no GPU, cold boot, etc),
              // in addition to time for the user to navigate the pairing flow.
              withTimeout(15.minutes) {
                pairer
                  .pair(glasses = it.glasses, phone = it.phone, project = project, onMacRetrieved = { mac -> glassesMacAddress = mac })
                  .collect { emit(it) }
              }
            } catch (cause: TimeoutCancellationException) {
              GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_TIMEOUT)
              emit(PairingState.Error("Pairing timed out", "The pairing process timed out."))
            }
          }
          .catch { cause ->
            GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.UNSPECIFIED)
            emit(PairingState.Error("Unexpected error: $cause"))
          }
      }
      .distinctUntilChanged()
      .onEach {
        when (it) {
          is PairingState.AwaitingAuthorization -> phone?.handle?.let { project?.userInvolvementRequired(it) }
          is PairingState.Error -> GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.SHOW_FAILED_PAIRING)
          is PairingState.Complete -> {
            GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.SHOW_SUCCESSFUL_PAIRING)
            phone?.handle?.let { project?.userInvolvementRequired(glassesHandle, it) }
          }
          else -> {}
        }
      }
      .stateIn(coroutineScope, started = SharingStarted.Eagerly, initialValue = PairingState.NotStarted)

  fun WizardPageScope.launchCreateCompatibleDevice(component: JComponent, state: SelectableLazyListState) {
    coroutineScope.launch {
      val createdAvd =
        addDeviceDialog.show(
          project = project,
          parent = component,
          virtualDeviceFilter = { it.formFactor == FormFactors.PHONE },
          systemImageFilter = { it.tags.contains(SystemImageTags.AI_GLASSES_COMPATIBLE_TAG) },
        )
      // Force focus back to this panel after the dialog closes; because this dialog is non-modal, it doesn't happen on its own
      withContext(Dispatchers.UI) { ((component as? Window) ?: SwingUtilities.getWindowAncestor(component))?.toFront() }
      if (createdAvd != null) {
        avdScanner().rescan()
        coroutineScope.launch {
          val createdRow =
            withTimeoutOrNull(5.seconds) {
              deviceRowFlow.mapNotNull { rows -> rows.find { it.state.properties.title == createdAvd.displayName } }.first()
            }

          if (createdRow != null) {
            phone = createdRow
            val currentSorted = deviceRowFlow.value.sortedWith(compareBy(Collator.getInstance()) { it.name })
            val index = currentSorted.indexOfFirst { it.handle.id == createdRow.handle.id }
            if (index >= 0) {
              state.selectedKeys = setOf(createdRow.handle.id)
              state.scrollToItem(index)
            }
          }
        }
      }
    }
  }

  @Composable
  internal fun WizardPageScope.SelectDevicePage() {
    val devices: ImmutableList<DeviceRow> by deviceRowFlow.collectAsState()
    val sortedDevices = remember(devices) { devices.sortedWith(compareBy(Collator.getInstance()) { it.name }).toImmutableList() }

    val component = LocalComponent.current
    val state = getOrCreateState { SelectableLazyListState(LazyListState()) }
    Column(Modifier.padding(20.dp)) {
      if (sortedDevices.isEmpty()) {
        LargeText(text = "No compatible AVDs found.")
        Text("Glasses pairing requires a Phone AVD with a system image that includes AI Glasses support.", Modifier.padding(top = 20.dp))
        ExternalLink(
          "Create a compatible device",
          onClick = { launchCreateCompatibleDevice(component, state) },
          Modifier.padding(top = 10.dp),
        )
      } else {
        LargeText("Select a device to pair", Modifier.padding(bottom = 8.dp))
        DeviceList(
          sortedDevices,
          onSelectedDeviceChange = {
            phone = it
            GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_DEVICE_SELECTED)
          },
          state,
          Modifier.weight(1f),
        )
        ExternalLink(
          "Create a new compatible device",
          onClick = { launchCreateCompatibleDevice(component, state) },
          Modifier.padding(top = 10.dp),
        )
      }
    }

    nextAction =
      when (val phone = phone) {
        null -> WizardAction.Disabled
        else ->
          WizardAction {
            GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_INITIATED)
            coroutineScope.launch { pairingTrigger.emit(PairingArgs(glassesHandle, phone = phone.handle)) }
            pushPage { Pair(phone) }
          }
      }
  }

  @Composable
  internal fun WizardPageScope.Pair(phone: DeviceRow) {
    val pairingState: PairingState by pairingFlow.collectAsState()

    PairingState(pairingState, phone)

    if (pairingState is PairingState.Complete) {
      enterTerminalState()
    }
  }
}

@Composable
private fun PairingState(pairingState: PairingState, phone: DeviceRow) {
  Column(Modifier.padding(vertical = 20.dp, horizontal = 20.dp)) {
    when (pairingState) {
      is PairingState.AwaitingAuthorization -> {
        Row(Modifier.fillMaxWidth()) {
          Column(Modifier.weight(1f)) {
            LargeText(pairingState.heading)

            Row(Modifier.padding(40.dp)) {
              CircularProgressIndicator()
              Spacer(Modifier.size(5.dp))
              Text(pairingState.detailText ?: "Waiting for user to accept Companion app permissions on ${phone.name}...")
            }
          }

          val painterProvider = rememberResourcePainterProvider(GLASSES_PAIRING_AUTH_IMAGE_PATH, GlassesPairingWizard::class.java)
          val painter by painterProvider.getPainter()

          Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(width = 244.dp, height = 400.dp),
            contentScale = ContentScale.Fit,
          )
        }
      }
      is PairingState.GlassesCoreConnecting -> {
        Row(Modifier.fillMaxWidth()) {
          Column(Modifier.weight(1f)) {
            LargeText(pairingState.heading)

            Row(Modifier.padding(40.dp)) {
              CircularProgressIndicator()
              Spacer(Modifier.size(5.dp))
              Text(pairingState.detailText ?: "Waiting for user to accept XR Services permissions on ${phone.name}...")
            }
          }

          val painterProvider = rememberResourcePainterProvider(GLASSES_CORE_CONNECTING_IMAGE_PATH, GlassesPairingWizard::class.java)
          val painter by painterProvider.getPainter()

          Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(width = 244.dp, height = 400.dp),
            contentScale = ContentScale.Fit,
          )
        }
      }
      is PairingState.Complete -> {
        Column(Modifier.fillMaxSize(), Arrangement.Center) {
          Icon(StudioIconsCompose.Common.Success, null, Modifier.size(100.dp).align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
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
private fun PairingStateHorizontalProgress(header: String, detail: String?, showProgressBar: Boolean) {
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
  Text(text, fontWeight = FontWeight.SemiBold, fontSize = LocalTextStyle.current.fontSize * 1.2, modifier = modifier)
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
    override val heading: String = "Starting $phoneName and $glassesName..."

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

  data class AwaitingAuthorization(val phoneName: String) : PairingState() {
    override val heading: String = "Accept Companion app Permissions on $phoneName"
  }

  data class GlassesCoreConnecting(val phoneName: String) : PairingState() {
    override val heading: String = "Accept XR Services Permissions on $phoneName"
  }

  data class GlassesCoreConnected(val phoneName: String) : PairingState() {
    override val heading: String = "Finishing pairing with $phoneName..."
  }

  data class AwaitingForeground(val phoneName: String) : PairingState() {
    override val heading: String = "Waiting for Companion app to move to the foreground on $phoneName"
  }

  data class Error(override val heading: String, override val detailText: String, val logDetail: String? = null) : PairingState() {
    constructor(detailText: String) : this("Pairing failed.", detailText)

    fun toLogMessage() = "$heading: $detailText${logDetail?.let { " [$it]" } ?: "" }"
  }

  data class Complete(val phoneName: String, val glassesName: String) : PairingState() {
    override val heading: String = "Successfully paired $phoneName with $glassesName"
  }
}

internal enum class LaunchState {
  Waiting,
  Launching,
  Booting,
  Ready,
}

internal fun launchAvd(handle: DeviceHandle): Flow<LaunchState> = flow {
  withTimeout(60.seconds) { handle.stateFlow.takeWhile { it.isTransitioning }.collect { emit(LaunchState.Waiting) } }
  if (handle.state.isReady) {
    emit(LaunchState.Ready)
  } else {
    emit(LaunchState.Launching)
    val activationAction = handle.activationAction ?: throw DeviceActionException("Device cannot be activated")
    withTimeout(360.seconds) { activationAction.activate() }
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

/** Indicates that two devices require user attention at the same time. */
internal fun Project.userInvolvementRequired(device1: DeviceHandle, device2: DeviceHandle) {
  val connected1 = device1.state as? DeviceState.Connected ?: return
  val connected2 = device2.state as? DeviceState.Connected ?: return
  val serialNumber1 = connected1.connectedDevice.serialNumber
  val serialNumber2 = connected2.connectedDevice.serialNumber
  messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(serialNumber1, serialNumber2, this)
}

private fun isAiGlassesCompatible(handle: DeviceHandle) =
  (handle.state.properties as? LocalEmulatorProperties)?.isAiGlassesCompatible == true

internal fun launchGlassesAndPhone(glasses: DeviceHandle, phone: DeviceHandle): Flow<PairingState> {
  val phoneName = phone.state.properties.title
  val glassesName = glasses.state.properties.title

  return launchAvd(glasses)
    .combine(launchAvd(phone)) { glassesState, phoneState -> PairingState.Launching(phoneName, phoneState, glassesName, glassesState) }
    .transformWhile {
      emit(it)
      it.phoneLaunchState != LaunchState.Ready || it.glassesLaunchState != LaunchState.Ready
    }
}

internal fun pairGlassesToPhone(
  glasses: DeviceHandle,
  phone: DeviceHandle,
  project: Project?,
  launchFlow: () -> Flow<PairingState> = { launchGlassesAndPhone(glasses, phone) },
  onMacRetrieved: (String) -> Unit,
): Flow<PairingState> {
  val logger = logger<GlassesPairingWizard>()
  val phoneName = phone.state.properties.title
  val glassesName = glasses.state.properties.title
  return flow {
      try {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_LAUNCH_STARTED)
        // The 9-minute timeout guards the concurrent launch of both emulators,
        // while the outer 15-minute timeout (in the caller) covers the whole process.
        withTimeout(9.minutes) { emitAll(launchFlow()) }
      } catch (e: TimeoutCancellationException) {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_TIMEOUT)
        emit(PairingState.Error("Timed out waiting for both $phoneName and $glassesName to start."))
        return@flow
      } catch (e: DeviceActionException) {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_LAUNCH_FAILED)
        emit(PairingState.Error(e.message ?: "Failed to launch both $phoneName and $glassesName."))
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

      try {
        project?.userInvolvementRequired(phone)
        runPairingSequence(phoneDevice, glassesDevice, phoneName, glassesName, logger, onMacRetrieved)
      } catch (cause: ShellCommandException) {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_SHELL_COMMAND)
        emit(
          PairingState.Error(
            heading = "Pairing failed",
            detailText =
              "An error occurred while communicating with $phoneName. Please check the device state on $phoneName.\n\nERROR: ${cause.message}",
            logDetail = cause.message,
          )
        )
      } catch (cause: IOException) {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_IO_FAILED)
        emit(
          PairingState.Error(
            heading = "Connection lost",
            detailText =
              "The connection to one or both of $phoneName and $glassesName was lost. Pairing may have still succeeded; please check $phoneName.",
            logDetail = cause.message,
          )
        )
      }
    }
    .distinctUntilChanged()
    .onEach {
      when (it) {
        PairingState.NotStarted,
        is PairingState.Pairing -> {}
        is PairingState.AwaitingForeground -> {
          logger.debug("Awaiting foreground of Companion app on $phoneName")
        }
        is PairingState.Launching ->
          if (logger.isDebugEnabled) {
            logger.debug("Launching $phoneName and $glassesName: ${it.phoneState()};  ${it.glassesState()}")
          }
        is PairingState.AwaitingAuthorization -> {
          GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_AWAITING_AUTHORIZATION)
          logger.debug("Awaiting authorization on $phoneName")
        }
        is PairingState.GlassesCoreConnecting -> {
          GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_GLASSES_CORE_CONNECTING)
          logger.debug("Connecting to XR Services on $phoneName")
        }
        is PairingState.GlassesCoreConnected -> {
          GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_GLASSES_CORE_CONNECTED)
          logger.debug("XR Services connection successful on $phoneName")
        }
        is PairingState.Complete -> logger.info("Successfully paired $phoneName with $glassesName")
        is PairingState.Error -> logger.warn(it.toLogMessage())
      }
    }
}

private suspend fun FlowCollector<PairingState>.runPairingSequence(
  phoneDevice: ConnectedDevice,
  glassesDevice: ConnectedDevice,
  phoneName: String,
  glassesName: String,
  logger: Logger,
  onMacRetrieved: (String) -> Unit,
) {
  with(AiGlassesPairing(phoneDevice.session)) {
    val glassesPairedCount =
      try {
        glassesDevice.getPairedBluetoothDeviceCount() ?: 0
      } catch (e: ShellCommandException) {
        throw ShellCommandException("Getting paired device count failed: ${e.message}")
      }

    if (glassesPairedCount > 0) {
      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_ALREADY_PAIRED)
      emit(PairingState.Error("$glassesName is already paired", "Wipe data on $glassesName to pair a new phone device."))
      return
    }

    val hasCompanionApp =
      try {
        phoneDevice.hasGlassesCompanionApp()
      } catch (e: ShellCommandException) {
        throw ShellCommandException("Checking for Glasses Companion app installed failed: ${e.message}")
      }

    if (!hasCompanionApp) {
      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_NO_COMPANION_APP)
      emit(PairingState.Error("$phoneName does not have support for AI Glasses."))
      return
    }

    if ((phoneDevice.getPairedBluetoothDeviceCount() ?: 0) > 0) {
      try {
        phoneDevice.sendUnpairCommand()
      } catch (e: ShellCommandException) {
        logger.warn("Failed to send unpair command", e)
      }
    }

    // Reset any prior pairing attempts
    phoneDevice.clearGlassesPackages()
    delay(3.seconds)

    emit(PairingState.Pairing("Initiating pairing with $phoneName and $glassesName..."))

    if ((phoneDevice.getPairedBluetoothDeviceCount() ?: 0) > 0) {
      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_WARNING_PHONE_ALREADY_PAIRED)
      emit(PairingState.Pairing("Warning: $phoneName already has a Bluetooth pairing; pairing with $glassesName will likely fail."))
      delay(3.seconds)
    }

    val glassesBluetoothAddress =
      try {
        glassesDevice.getBluetoothAddress()
      } catch (e: ShellCommandException) {
        throw ShellCommandException("Getting Bluetooth address of $glassesName failed: ${e.message}")
      }

    if (glassesBluetoothAddress == null) {
      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_BLUETOOTH_ADDRESS)
      emit(PairingState.Error("Failed to retrieve Bluetooth address of $glassesName."))
      return
    }
    onMacRetrieved(glassesBluetoothAddress)

    val phoneBluetoothAddress = phoneDevice.getBluetoothAddress()
    // If phoneBluetoothAddress is null, we may not have access to it; we just have to proceed
    // and hope for the best.
    if (phoneBluetoothAddress == glassesBluetoothAddress) {
      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_BLUETOOTH_ADDRESS)
      emit(
        PairingState.Error(
          heading = "Network simulation error",
          detailText =
            "The same Bluetooth address has been assigned to both $phoneName and $glassesName. " +
              "Please perform a Cold Boot of either $phoneName or $glassesName and try again.",
        )
      )
      return
    }

    phoneDevice
      .pairToGlasses(glassesBluetoothAddress, true)
      .onEach { pairingState ->
        logger.debug("Polling pairing state: $pairingState")
        when (pairingState) {
          "PAIRED" -> emit(PairingState.Complete(phoneName, glassesName))
          "UI_CDM_ASSOCIATING" -> emit(PairingState.AwaitingAuthorization(phoneName))
          "WORKER_CONNECTING" -> emit(PairingState.GlassesCoreConnecting(phoneName))
          "WORKER_GLASSES_CORE_CONNECTED" -> emit(PairingState.GlassesCoreConnected(phoneName))
          AiGlassesPairing.AWAITING_FOREGROUND -> emit(PairingState.AwaitingForeground(phoneName))
          in AiGlassesPairing.TERMINAL_STATES ->
            emit(
              PairingState.Error(
                heading = "Error pairing $glassesName",
                detailText =
                  when (pairingState) {
                    "UI_CDM_ASSOCIATION_FAILED" -> {
                      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_COMPANION_CDM_FAILED)
                      "Failed to create companion device association between $phoneName and $glassesName."
                    }
                    "WORKER_BOND_FAILED" -> {
                      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_BOND_FAILED)
                      "Failed to create a Bluetooth bond between $phoneName and $glassesName."
                    }
                    "WORKER_CONNECTION_FAILED" -> {
                      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_CONNECTION_FAILED)
                      "Failed to connect $glassesName to XR Services on $phoneName."
                    }
                    "WORKER_GLASSES_CORE_CONNECTION_FAILED" -> {
                      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_CONNECTION_FAILED)
                      "Failed to connect $glassesName to XR Services on $phoneName. Please make sure to accept all permissions on $phoneName."
                    }
                    "WORKER_CANCELLED" -> {
                      GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.PAIRING_ERROR_WORKER_CANCELLED)
                      "Pairing $glassesName with $phoneName was cancelled."
                    }
                    "POLLING_FAILED" ->
                      "Failed to detect the Companion app in the foreground or pairing state. Please make sure it is open and focused on $phoneName."
                    else -> "Error pairing $glassesName with $phoneName."
                  },
                logDetail = pairingState,
              )
            )
          else -> emit(PairingState.Pairing("Pairing in progress..."))
        }
      }
      .first { it in AiGlassesPairing.TERMINAL_STATES }
  }
}
