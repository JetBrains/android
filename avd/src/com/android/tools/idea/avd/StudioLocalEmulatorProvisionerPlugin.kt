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
package com.android.tools.idea.avd

import com.android.adblib.ConnectedDevice
import com.android.adblib.tools.aiglasses.AiGlassesPairing
import com.android.sdklib.deviceprovisioner.ActivationAction
import com.android.sdklib.deviceprovisioner.AvdDeviceError
import com.android.sdklib.deviceprovisioner.AvdScanner
import com.android.sdklib.deviceprovisioner.BootSnapshotAction
import com.android.sdklib.deviceprovisioner.ColdBootAction
import com.android.sdklib.deviceprovisioner.CreateDeviceAction
import com.android.sdklib.deviceprovisioner.DeactivationAction
import com.android.sdklib.deviceprovisioner.DeleteAction
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceError
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.DuplicateAction
import com.android.sdklib.deviceprovisioner.EditAction
import com.android.sdklib.deviceprovisioner.Extension
import com.android.sdklib.deviceprovisioner.LocalEmulatorContext
import com.android.sdklib.deviceprovisioner.LocalEmulatorDeviceHandle
import com.android.sdklib.deviceprovisioner.LocalEmulatorProvisionerPlugin
import com.android.sdklib.deviceprovisioner.LocalEmulatorSnapshot
import com.android.sdklib.deviceprovisioner.LocalEmulatorSnapshotReader
import com.android.sdklib.deviceprovisioner.PairGlassesAction
import com.android.sdklib.deviceprovisioner.RepairDeviceAction
import com.android.sdklib.deviceprovisioner.ShowAction
import com.android.sdklib.deviceprovisioner.Snapshot
import com.android.sdklib.deviceprovisioner.UnpairGlassesAction
import com.android.sdklib.deviceprovisioner.WipeDataAction
import com.android.sdklib.deviceprovisioner.awaitReady
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus
import com.android.sdklib.internal.avd.BootMode
import com.android.sdklib.internal.avd.BootSnapshot
import com.android.sdklib.internal.avd.ColdBoot
import com.android.tools.idea.avd.EditVirtualDeviceDialog.Mode
import com.android.tools.idea.avd.glassespairing.GlassesPairingLockService
import com.android.tools.idea.avd.glassespairing.GlassesPairingResult
import com.android.tools.idea.avd.glassespairing.GlassesPairingUsageTracker
import com.android.tools.idea.avd.glassespairing.GlassesPairingWizard
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorSolution
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.RunningAvdTracker
import com.android.tools.idea.avdmanager.checkAcceleration
import com.android.tools.idea.avdmanager.logHypervisorMigrationEvent
import com.android.tools.idea.deviceprovisioner.NotificationBannersExtension
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceActionPresentation
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.wireless.android.sdk.stats.EmulatorWindowsHypervisorMigrationEvent
import com.google.wireless.android.sdk.stats.GlassesPairingEvent
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.EditorNotificationPanel
import icons.StudioIcons
import java.awt.Component
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class StudioLocalEmulatorProvisionerPlugin(
  val scope: CoroutineScope,
  val basePlugin: LocalEmulatorProvisionerPlugin,
  val context: LocalEmulatorContext,
  val project: Project?,
  val avdScanner: AvdScanner,
  val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceProvisionerPlugin by basePlugin {
  private val accelerationError = MutableStateFlow(AccelerationErrorCode.ALREADY_INSTALLED)

  override fun <T : Extension> extension(extensionClass: Class<T>): T? {
    return if (extensionClass == NotificationBannersExtension::class.java) {
      @Suppress("UNCHECKED_CAST") NotificationBannersExtension(notificationBanners) as T
    } else {
      basePlugin.extension(extensionClass)
    }
  }

  suspend fun refreshDevices() {
    avdScanner.rescan()
  }

  override val devices: StateFlow<List<StudioLocalEmulatorDeviceHandle>> =
    flow {
        val handles = mutableMapOf<LocalEmulatorDeviceHandle, StudioLocalEmulatorDeviceHandle>()
        basePlugin.devices.collect { baseHandles ->
          val wrappedHandles = mutableListOf<StudioLocalEmulatorDeviceHandle>()
          for (baseHandle in baseHandles) {
            wrappedHandles.add(
              handles.computeIfAbsent(baseHandle as LocalEmulatorDeviceHandle) {
                StudioLocalEmulatorDeviceHandle(project, baseHandle, context, devices, ioDispatcher)
              }
            )
          }
          handles.keys.retainAll(baseHandles.toSet())
          emit(wrappedHandles.toList())
        }
      }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())

  private val notificationBanners: StateFlow<List<EditorNotificationPanel>> =
    combine(devices, accelerationError) { deviceList, accelError ->
        if (deviceList.isEmpty() || accelError == AccelerationErrorCode.ALREADY_INSTALLED) emptyList()
        else listOf(EmulatorCheckErrorBanner(accelError))
      }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())

  override val createDeviceAction =
    object : CreateDeviceAction {
      override val presentation =
        MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext().copy(label = "Create Virtual Device")).asStateFlow()

      override suspend fun create(parent: Component?) {
        if (showAddDeviceDialog(project, parent) != null) {
          refreshDevices()
        }
      }
    }

  init {
    refreshAccelerationCheck()
  }

  private fun refreshAccelerationCheck() {
    scope.launch(Dispatchers.Default) { accelerationError.value = checkAcceleration(AndroidSdks.getInstance().tryToChooseSdkHandler()) }
  }

  private inner class EmulatorCheckErrorBanner(accelError: AccelerationErrorCode) : EditorNotificationPanel() {
    init {
      text = "<html>" + accelError.problem + "</html>"
      icon(StudioIcons.Common.ERROR)
      createActionLabel(accelError.solution.description) {
        AccelerationErrorSolution.getActionForFix(accelError, project, { refreshAccelerationCheck() }, null).run()
      }
      if (accelError == AccelerationErrorCode.WHPX_RECOMMENDED) {
        logHypervisorMigrationEvent(EmulatorWindowsHypervisorMigrationEvent.Action.BANNER_SHOW)
      }
    }
  }
}

class StudioLocalEmulatorDeviceHandle(
  private val project: Project?,
  private val baseDeviceHandle: LocalEmulatorDeviceHandle,
  private val context: LocalEmulatorContext,
  private val deviceHandleFlow: StateFlow<List<StudioLocalEmulatorDeviceHandle>>,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val edtDispatcher: CoroutineContext = Dispatchers.EDT,
) : DeviceHandle by baseDeviceHandle {
  fun addPairedGlasses(glassesId: DeviceId, mac: String) = baseDeviceHandle.addPairedGlasses(glassesId, mac)

  fun removePairedGlasses(glassesId: DeviceId) = baseDeviceHandle.removePairedGlasses(glassesId)

  fun updatePairedPhone(phone: StudioLocalEmulatorDeviceHandle?) = baseDeviceHandle.updatePairedPhone(phone?.baseDeviceHandle)

  fun clearPairedGlasses() = baseDeviceHandle.clearPairedGlasses()

  private var activationJob: Job? = null
  private val avdManagerConnection
    get() = AvdManagerConnection.getDefaultAvdManagerConnection()

  private val runningAvdTracker
    get() = RunningAvdTracker.getInstance()

  private val logger: Logger
    get() = thisLogger()

  private val adbLogger
    get() = context.logger

  private val avdInfo by baseDeviceHandle::avdInfo
  private val onDiskAvdInfo by baseDeviceHandle::onDiskAvdInfo

  private suspend fun refreshDevices() {
    baseDeviceHandle.avdScanner.rescan()
  }

  // Returning GlassesPairingResult instead of just the device handle allows
  // propagating the MAC address back to avoid race conditions.
  internal var wizardProvider: suspend (Component?, Project?, Flow<List<DeviceHandle>>, DeviceHandle) -> GlassesPairingResult? =
    { par, proj, flow, handle ->
      GlassesPairingWizard.show(par, proj, flow, handle)
    }

  private val defaultPresentation: DeviceAction.DefaultPresentation = StudioDefaultDeviceActionPresentation

  private suspend fun doActivate(action: suspend () -> Unit) {
    // If we have a companion phone, launch it in parallel when we launch.
    val companionHandle = state.properties.pairedPhoneId?.let { phoneId -> deviceHandleFlow.value.find { it.id == phoneId } }
    if (companionHandle != null && companionHandle.activationAction.presentation.value.enabled) {
      coroutineScope {
        launch { baseDeviceHandle.activate(action) }
        launch { companionHandle.activationAction.activate() }
      }
    } else {
      baseDeviceHandle.activate(action)
    }

    activationJob =
      baseDeviceHandle.scope.launch {
        val ready = withTimeoutOrNull(120_000L) { stateFlow.first { it.isReady } }
        if (ready != null && isUnpairedAiGlasses()) {
          launchAutomaticGlassesPairing()
        }
      }
  }

  private suspend fun isUnpairedAiGlasses(): Boolean {
    if (state.properties.deviceType == DeviceType.AI_GLASSES) {
      awaitReady()
      return state.connectedDevice?.isUnpaired() ?: false
    }
    return false
  }

  private suspend fun ConnectedDevice.isUnpaired(): Boolean =
    with(AiGlassesPairing(session)) {
      try {
        getPairedBluetoothDeviceCount() == 0
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logger.warn("Failed to get paired Bluetooth device count", e)
        false
      }
    }

  private suspend fun startAvd(avdInfo: AvdInfo, bootMode: BootMode): Unit =
    // Note: the original DeviceManager does this in UI thread, but this may call
    // @Slow methods so switch
    withContext(Dispatchers.Default) { avdManagerConnection.startAvd(project, avdInfo, bootMode = bootMode) }

  override val activationAction =
    object : ActivationAction {
      override val presentation = defaultPresentation.fromContext().enabledIfActivatable()

      override suspend fun activate() {
        doActivate {
          // Consult the config to see what the default boot method is.
          val bootMode = BootMode.fromProperties(avdInfo.properties)
          startAvd(avdInfo, bootMode)
        }
      }
    }

  override val coldBootAction =
    object : ColdBootAction {
      override val presentation = defaultPresentation.fromContext().enabledIfActivatable()

      override suspend fun activate() {
        doActivate { startAvd(avdInfo, ColdBoot) }
      }
    }

  override val bootSnapshotAction = LocalEmulatorBootSnapshotAction()

  inner class LocalEmulatorBootSnapshotAction : BootSnapshotAction {

    override val presentation = defaultPresentation.fromContext().enabledIfActivatable()

    override suspend fun snapshots(): List<LocalEmulatorSnapshot> =
      withContext(ioDispatcher) { LocalEmulatorSnapshotReader(adbLogger).readSnapshots(avdInfo.dataFolderPath.resolve("snapshots")) }

    override suspend fun activate(snapshot: Snapshot) {
      doActivate {
        val snapshotName = (snapshot as LocalEmulatorSnapshot).path.fileName.toString()
        startAvd(avdInfo, BootSnapshot(snapshotName))
      }
    }
  }

  override val deactivationAction: DeactivationAction =
    object : DeactivationAction {
      // We could check this with AvdManagerConnection.isAvdRunning, but that's expensive, and if
      // it's not running we should see it from ADB anyway
      override val presentation = defaultPresentation.fromContext().enabledIf { it is Connected && !it.isTransitioning }

      /** Attempts to stop the AVD. We can either use the emulator console or AvdManager (which uses a shell command to kill the process) */
      override suspend fun deactivate() {
        baseDeviceHandle.deactivate {
          baseDeviceHandle.emulatorConsole?.let {
            try {
              it.kill()
              runningAvdTracker.shuttingDown(avdInfo.dataFolderPath)
              return@deactivate
            } catch (e: IOException) {
              // Connection to emulator console is closed, possibly due to a harmless race
              // condition.
              logger.debug("Failed to shutdown via emulator console; falling back to AvdManager", e)
            }
          }
          withContext(Dispatchers.Default) { avdManagerConnection.stopAvd(avdInfo) }
        }
      }
    }

  override val editAction =
    object : EditAction {
      override val presentation = MutableStateFlow(defaultPresentation.fromContext()).asStateFlow()

      override suspend fun edit(parent: Component?) {
        if (EditVirtualDeviceDialog.show(project, parent, onDiskAvdInfo, Mode.EDIT)) {
          refreshDevices()
        }
      }
    }

  override val repairDeviceAction =
    object : RepairDeviceAction {
      override val presentation =
        DeviceAction.Presentation(label = "Download system image", icon = AllIcons.Actions.Download, enabled = false).enabledIf {
          (it.error as? AvdDeviceError)?.status == AvdStatus.ERROR_IMAGE_MISSING
        }

      override suspend fun repair() {
        val path = AvdManagerConnection.getRequiredSystemImagePath(avdInfo) ?: return
        withContext(edtDispatcher) { SdkQuickfixUtils.createDialogForPaths(project, listOf(path))?.showAndGet() }
        refreshDevices()
      }
    }

  override val showAction: ShowAction =
    object : ShowAction {
      override val presentation = MutableStateFlow(defaultPresentation.fromContext().copy(label = "Show on Disk"))

      override suspend fun show() {
        RevealFileAction.openDirectory(avdInfo.dataFolderPath)
      }
    }

  override val duplicateAction: DuplicateAction =
    object : DuplicateAction {
      override val presentation = MutableStateFlow(defaultPresentation.fromContext())

      override suspend fun duplicate(parent: Component?) {
        EditVirtualDeviceDialog.show(project, parent, onDiskAvdInfo, mode = Mode.DUPLICATE)
        refreshDevices()
      }
    }

  override val wipeDataAction: WipeDataAction =
    object : WipeDataAction {
      override val presentation = defaultPresentation.fromContext().enabledIfStopped()

      override suspend fun wipeData() {
        withContext(ioDispatcher) {
          val properties = state.properties
          val hasCompanions = properties.pairedPhoneId != null || properties.pairedGlassesInfos.isNotEmpty()
          if (avdManagerConnection.wipeUserData(avdInfo)) {
            if (hasCompanions) {
              GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.CASCADING_WIPE_INITIATED)
            }
            // Clean up companions before clearing local state
            unpairFromCompanions()

            updatePairedPhone(null)
            clearPairedGlasses()
          } else {
            withContext(edtDispatcher) {
              Messages.showErrorDialog(
                project,
                "Failed to wipe data. Please check that the emulator and its files are not in use and try again.",
                "Wipe Data Error",
              )
            }
          }
        }
      }
    }

  override val deleteAction: DeleteAction =
    object : DeleteAction {
      override val presentation = defaultPresentation.fromContext().enabledIfStopped()

      override suspend fun delete() {
        withContext(ioDispatcher) {
          val properties = state.properties
          val hasCompanions = properties.pairedPhoneId != null || properties.pairedGlassesInfos.isNotEmpty()
          if (avdManagerConnection.deleteAvd(avdInfo)) {
            if (hasCompanions) {
              GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.CASCADING_WIPE_INITIATED)
            }
            unpairFromCompanions()
          } else {
            withContext(edtDispatcher) {
              if (
                MessageDialogBuilder.okCancel(
                    "Could Not Delete All AVD Files",
                    "There may be additional files remaining in the AVD directory. To fully delete " +
                      "the AVD, open the directory and manually delete the files.",
                  )
                  .yesText("Open Directory")
                  .noText("OK")
                  .icon(Messages.getInformationIcon())
                  .ask(project)
              ) {
                showAction.show()
              }
            }
          }
          refreshDevices()
        }
      }
    }

  private suspend fun unpairFromCompanions(): Boolean {
    val properties = state.properties
    logger.info("Unpairing companions for device $id")
    try {
      val phoneId = properties.pairedPhoneId
      if (phoneId != null) {
        val phoneHandle = deviceHandleFlow.value.find { it.id == phoneId }
        phoneHandle?.removePairedGlasses(id)
      } else {
        // Unpair from all paired glasses. For glasses devices, pairedGlassesInfos is empty,
        // so this is a no-op.
        properties.pairedGlassesInfos.forEach { glassesInfo ->
          val glassesHandle = deviceHandleFlow.value.find { it.id == glassesInfo.id }
          glassesHandle?.updatePairedPhone(null)
        }
      }
      return true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logger.warn("Failed to unpair companions from $id", e)
      return false
    }
  }

  private val aiGlassesAutoPairingDisabledPropertyKey
    get() = "ai.glasses.auto.pairing.disabled.$id"

  suspend fun launchAutomaticGlassesPairing() {
    if (PropertiesComponent.getInstance().isTrueValue(aiGlassesAutoPairingDisabledPropertyKey)) return
    val lockService = service<GlassesPairingLockService>()
    if (lockService.isWizardOpen.value) return

    withContext(edtDispatcher) {
      if (lockService.isWizardOpen.value) return@withContext
      val parent = WindowManager.getInstance().suggestParentWindow(project)
      while (!pairGlasses(parent)) {
        if (confirmPairingWizardCancellation()) break
      }
    }
  }

  private fun confirmPairingWizardCancellation(): Boolean =
    MessageDialogBuilder.okCancel(
        "Cancel Glasses emulator pairing",
        "Stop pairing wizard?\n\nYou can launch the pairing wizard again from the glasses emulator's overflow menu in Device Manager.",
      )
      .doNotAsk(
        object : DoNotAskOption.Adapter() {
          override fun getDoNotShowMessage() = "Do not auto-launch pairing wizard again for this device"

          override fun isSelectedByDefault() = false

          override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
            PropertiesComponent.getInstance().setValue(aiGlassesAutoPairingDisabledPropertyKey, isSelected)
          }
        }
      )
      .ask(project)

  override val pairGlassesAction =
    object : PairGlassesAction {
      override suspend fun pairGlasses(parent: Component?) {
        this@StudioLocalEmulatorDeviceHandle.pairGlasses(parent)
      }

      override val presentation: StateFlow<DeviceAction.Presentation> =
        stateFlow
          .combine(service<GlassesPairingLockService>().isWizardOpen) { deviceState: DeviceState, isOpen: Boolean ->
            val enabled = deviceState.properties.deviceType == DeviceType.AI_GLASSES
            if (isOpen && enabled) {
              defaultPresentation.fromContext().copy(enabled = false, detail = "Pairing already in progress")
            } else {
              defaultPresentation.fromContext().copy(enabled = enabled)
            }
          }
          .stateIn(this@StudioLocalEmulatorDeviceHandle.scope, SharingStarted.Eagerly, defaultPresentation.fromContext())
    }

  private suspend fun pairGlasses(parent: Component?): Boolean {
    val glassesHandle = this@StudioLocalEmulatorDeviceHandle
    logger.info("User initiated Glasses Pairing Wizard for ${glassesHandle.id}")
    val result = withContext(edtDispatcher) { wizardProvider(parent, project, deviceHandleFlow, glassesHandle) }

    if (result != null) {
      val pairedPhone = result.phone as? StudioLocalEmulatorDeviceHandle
      val mac = result.glassesMacAddress
      if (pairedPhone != null) {
        try {
          withContext(ioDispatcher) {
            glassesHandle.updatePairedPhone(pairedPhone)
            pairedPhone.addPairedGlasses(glassesHandle.id, mac)
            logger.info("Successfully paired glasses ${glassesHandle.id} with phone ${pairedPhone.id}")
          }
        } catch (e: IOException) {
          logger.warn("Failed to write pairing config", e)
          withContext(edtDispatcher) {
            Messages.showErrorDialog(
              project,
              "Failed to save pairing configuration. Please check disk space and permissions.",
              "Pairing Error",
            )
          }
          return true
        }
      }
      return true
    }
    return false
  }

  override val unpairGlassesAction =
    object : UnpairGlassesAction {
      override suspend fun unpairGlasses() {
        GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.UNPAIR_ACTION_CLICKED)
        val success =
          withContext(ioDispatcher) {
            val ok = unpairFromCompanions()
            if (ok) {
              updatePairedPhone(null)
              clearPairedGlasses()
            }
            ok
          }
        if (success) {
          GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.UNPAIR_SUCCESSFUL)
        } else {
          GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.UNPAIR_FAILED)
          withContext(edtDispatcher) { Messages.showErrorDialog(project, "Failed to clear pairing configuration.", "Unpair Error") }
        }
      }

      override val presentation: StateFlow<DeviceAction.Presentation> =
        defaultPresentation.fromContext().enabledIf { it.properties.pairedPhoneId != null || it.properties.pairedGlassesInfos.isNotEmpty() }
    }

  private fun DeviceAction.Presentation.enabledIf(condition: (DeviceState) -> Boolean) =
    stateFlow.map { this.copy(enabled = condition(it)) }.stateIn(scope, SharingStarted.Eagerly, this)

  private fun DeviceState.isStopped() = this is Disconnected && !this.isTransitioning

  private fun DeviceAction.Presentation.enabledIfStopped() = enabledIf { it.isStopped() }

  private fun DeviceAction.Presentation.enabledIfActivatable() = enabledIf {
    it.isStopped() && it.error?.severity != DeviceError.Severity.ERROR
  }
}
