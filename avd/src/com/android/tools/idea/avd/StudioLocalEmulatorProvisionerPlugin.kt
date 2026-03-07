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
import com.android.sdklib.deviceprovisioner.BootSnapshotAction
import com.android.sdklib.deviceprovisioner.ColdBootAction
import com.android.sdklib.deviceprovisioner.CreateDeviceAction
import com.android.sdklib.deviceprovisioner.DeactivationAction
import com.android.sdklib.deviceprovisioner.DeleteAction
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceError
import com.android.sdklib.deviceprovisioner.DeviceHandle
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
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus
import com.android.sdklib.internal.avd.BootMode
import com.android.sdklib.internal.avd.BootSnapshot
import com.android.sdklib.internal.avd.ColdBoot
import com.android.tools.idea.avd.EditVirtualDeviceDialog.Mode
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorSolution
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.RunningAvdTracker
import com.android.tools.idea.avdmanager.checkAcceleration
import com.android.tools.idea.deviceprovisioner.NotificationBannersExtension
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceActionPresentation
import com.android.tools.idea.glassespairing.GlassesPairingWizard
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioLocalEmulatorProvisionerPlugin(
  val scope: CoroutineScope,
  val basePlugin: LocalEmulatorProvisionerPlugin,
  val context: LocalEmulatorContext,
  val project: Project?,
) : DeviceProvisionerPlugin by basePlugin {
  private val accelerationError = MutableStateFlow(AccelerationErrorCode.ALREADY_INSTALLED)

  override fun <T : Extension> extension(extensionClass: Class<T>): T? {
    return if (extensionClass == NotificationBannersExtension::class.java) {
      @Suppress("UNCHECKED_CAST") NotificationBannersExtension(notificationBanners) as T
    } else {
      basePlugin.extension(extensionClass)
    }
  }

  fun refreshDevices() {
    basePlugin.refreshDevices()
  }

  override val devices: StateFlow<List<StudioLocalEmulatorDeviceHandle>> =
    flow {
        val handles = mutableMapOf<LocalEmulatorDeviceHandle, StudioLocalEmulatorDeviceHandle>()
        basePlugin.devices.collect { baseHandles ->
          val wrappedHandles = mutableListOf<StudioLocalEmulatorDeviceHandle>()
          for (baseHandle in baseHandles) {
            wrappedHandles.add(
              handles.computeIfAbsent(baseHandle as LocalEmulatorDeviceHandle) {
                StudioLocalEmulatorDeviceHandle(project, baseHandle, context, devices)
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
    }
  }
}

class StudioLocalEmulatorDeviceHandle(
  private val project: Project?,
  internal val baseDeviceHandle: LocalEmulatorDeviceHandle,
  private val context: LocalEmulatorContext,
  private val deviceHandleFlow: Flow<List<StudioLocalEmulatorDeviceHandle>>,
) : DeviceHandle by baseDeviceHandle {
  // Do not cache this; getDefaultAvdManagerConnection() changes when the local SDK path changes.
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

  private fun refreshDevices() {
    baseDeviceHandle.refreshDevices()
  }

  private val defaultPresentation: DeviceAction.DefaultPresentation = StudioDefaultDeviceActionPresentation

  private suspend fun doActivate(action: suspend () -> Unit) {
    baseDeviceHandle.activate(action)
    if (isUnpairedAiGlasses()) {
      launchAutomaticGlassesPairing()
    }
  }

  private suspend fun isUnpairedAiGlasses(): Boolean =
    state.properties.deviceType == DeviceType.AI_GLASSES && state.connectedDevice?.isUnpaired() == true

  private suspend fun ConnectedDevice.isUnpaired() = with(AiGlassesPairing(session)) { getPairedBluetoothDeviceCount() == 0 }

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
      withContext(Dispatchers.IO) { LocalEmulatorSnapshotReader(adbLogger).readSnapshots(avdInfo.dataFolderPath.resolve("snapshots")) }

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
        withContext(Dispatchers.EDT) { SdkQuickfixUtils.createDialogForPaths(project, listOf(path))?.showAndGet() }
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
        withContext(Dispatchers.IO) {
          if (!avdManagerConnection.wipeUserData(avdInfo)) {
            withContext(Dispatchers.EDT) {
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
        withContext(Dispatchers.IO) {
          if (!avdManagerConnection.deleteAvd(avdInfo)) {
            withContext(Dispatchers.EDT) {
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

  private val aiGlassesAutoPairingDisabledPropertyKey
    get() = "ai.glasses.auto.pairing.disabled.$id"

  suspend fun launchAutomaticGlassesPairing() {
    if (PropertiesComponent.getInstance().isTrueValue(aiGlassesAutoPairingDisabledPropertyKey)) return

    withContext(Dispatchers.EDT) {
      val parent = WindowManager.getInstance().suggestParentWindow(project)
      while (!pairGlasses(parent) && !confirmPairingWizardCancellation()) {}
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
        defaultPresentation.fromContext().enabledIf { it.properties.deviceType == DeviceType.AI_GLASSES }
    }

  private suspend fun pairGlasses(parent: Component?): Boolean {
    val glassesHandle = this@StudioLocalEmulatorDeviceHandle
    val pairedPhone =
      withContext(Dispatchers.EDT) {
        GlassesPairingWizard.show(parent, project = project, devicesFlow = deviceHandleFlow, glassesHandle = glassesHandle)
          as? StudioLocalEmulatorDeviceHandle
      }
    if (pairedPhone != null) {
      withContext(Dispatchers.IO) {
        glassesHandle.baseDeviceHandle.updatePairedPhone(pairedPhone.baseDeviceHandle)
        pairedPhone.baseDeviceHandle.updatePairedGlasses(glassesHandle.baseDeviceHandle)
      }
    }
    return pairedPhone != null
  }

  override val unpairGlassesAction =
    object : UnpairGlassesAction {
      override suspend fun unpairGlasses() {
        // TODO(b/458470193): Implement unpairing
      }

      override val presentation: StateFlow<DeviceAction.Presentation> =
        defaultPresentation.fromContext().enabledIf { it.properties.pairedPhoneId != null || it.properties.pairedGlassesId != null }
    }

  private fun DeviceAction.Presentation.enabledIf(condition: (DeviceState) -> Boolean) =
    stateFlow.map { this.copy(enabled = condition(it)) }.stateIn(scope, SharingStarted.Eagerly, this)

  private fun DeviceState.isStopped() = this is Disconnected && !this.isTransitioning

  private fun DeviceAction.Presentation.enabledIfStopped() = enabledIf { it.isStopped() }

  private fun DeviceAction.Presentation.enabledIfActivatable() = enabledIf {
    it.isStopped() && it.error?.severity != DeviceError.Severity.ERROR
  }
}
