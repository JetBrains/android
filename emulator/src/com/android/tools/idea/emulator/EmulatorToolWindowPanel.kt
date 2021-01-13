/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.emulator.control.ExtendedControlsStatus
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.util.ActionToolbarUtil.makeToolbarNavigable
import com.android.tools.deployer.AdbClient
import com.android.tools.deployer.ApkInstaller
import com.android.tools.deployer.InstallStatus
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformNullable
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.actions.findManageSnapshotDialog
import com.android.tools.idea.emulator.actions.showExtendedControls
import com.android.tools.idea.emulator.actions.showManageSnapshotsDialog
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LogWrapper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil.getFileListFromAttachedObject
import com.intellij.ide.dnd.FileCopyPasteUtil.isFileListFlavorAvailable
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.awt.BorderLayout
import java.awt.EventQueue
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
private const val isToolbarHorizontal = true

/**
 * Represents contents of the Emulator tool window for a single Emulator instance.
 */
class EmulatorToolWindowPanel(
  private val project: Project,
  val emulator: EmulatorController,
  val uiState: EmulatorUiState
) : BorderLayoutPanel(), DataProvider {

  private val mainToolbar: ActionToolbar
  private val emulatorPanel = EmulatorDisplayPanel(emulator)

  private val emulatorView: EmulatorView?
    get() = emulatorPanel.emulatorView

  val id
    get() = emulator.emulatorId

  val title
    get() = emulator.emulatorId.avdName

  val icon
    get() = ICON

  val component: JComponent
    get() = this

  var zoomToolbarVisible: Boolean
    get() = emulatorPanel.zoomToolbarVisible
    set(value) { emulatorPanel.zoomToolbarVisible = value }

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(EMULATOR_MAIN_TOOLBAR_ID, isToolbarHorizontal)

    installDnD()
  }

  fun getPreferredFocusableComponent(): JComponent {
    return emulatorPanel.getPreferredFocusableComponent()
  }

  private fun addToolbar() {
    if (isToolbarHorizontal) {
      mainToolbar.setOrientation(SwingConstants.HORIZONTAL)
      emulatorPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      addToTop(mainToolbar.component)
    }
    else {
      mainToolbar.setOrientation(SwingConstants.VERTICAL)
      emulatorPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.LEFT)
      addToLeft(mainToolbar.component)
    }
  }

  private fun installDnD() {
    DnDSupport.createBuilder(this)
      .enableAsNativeTarget()
      .setTargetChecker { event ->
        if (isFileListFlavorAvailable(event)) {
          event.isDropPossible = true
          return@setTargetChecker false
        }
        return@setTargetChecker true
      }
      .setDropHandler(ApkFileDropHandler())
      .install()
  }

  fun setDeviceFrameVisible(visible: Boolean) {
    emulatorPanel.setDeviceFrameVisible(visible)
  }

  fun createContent(deviceFrameVisible: Boolean) {
    try {
      emulatorPanel.createContent(deviceFrameVisible)
      mainToolbar.setTargetComponent(emulatorView)

      addToCenter(emulatorPanel)

      addToolbar()

      if (connected) {
        emulatorView?.let { emulatorView ->
          if (uiState.manageSnapshotsDialogShown) {
            showManageSnapshotsDialog(emulatorView, project)
          }
          if (uiState.extendedControlsShown) {
            showExtendedControls(emulator)
          }
        }
      }
    }
    catch (e: Exception) {
      val label = "Unable to create emulator view: $e"
      add(JLabel(label), BorderLayout.CENTER)
    }
  }

  fun destroyContent() {
    val manageSnapshotsDialog = emulatorView?.let { findManageSnapshotDialog(it) }
    uiState.manageSnapshotsDialogShown = manageSnapshotsDialog != null
    manageSnapshotsDialog?.close(DialogWrapper.CLOSE_EXIT_CODE)

    if (StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS.get() && connected) {
      emulator.closeExtendedControls(object: EmptyStreamObserver<ExtendedControlsStatus>() {
        override fun onNext(response: ExtendedControlsStatus) {
          EventQueue.invokeLater {
            uiState.extendedControlsShown = response.visibilityChanged
          }
        }
      })
    }

    emulatorPanel.destroyContent()

    mainToolbar.setTargetComponent(null)

    removeAll()
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> emulator
      EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
      else -> null
    }
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutPolicy = ActionToolbar.AUTO_LAYOUT_POLICY
    makeToolbarNavigable(toolbar)
    return toolbar
  }

  private inner class ApkFileDropHandler : DnDDropHandler {

    override fun drop(event: DnDEvent) {
      val files = getFileListFromAttachedObject(event.attachedObject)
      val fileNames = files.joinToString(", ") { it.name }
      emulatorView?.showLongRunningOperationIndicator("Installing $fileNames")

      val resultFuture: ListenableFuture<AdbClient.InstallResult?> = findDevice().transformNullable(getAppExecutorService()) { device ->
        if (device == null) return@transformNullable null
        val adbClient = AdbClient(device, LogWrapper(EmulatorToolWindowPanel::class.java))
        val filePaths = files.asSequence().map { it.path }.toList()
        return@transformNullable adbClient.install(filePaths, listOf("-t", "--user", "current", "--full", "--dont-kill"), true)
      }
      resultFuture.addCallback(
          EdtExecutorService.getInstance(),
          success = { installResult ->
            if (installResult?.status == InstallStatus.OK) {
              notifyOfSuccess("$fileNames installed")
            }
            else {
              val message = if (installResult == null) {
                "Unable to find the device to install the app"
              }
              else {
                installResult.reason ?: ApkInstaller.message(installResult)
              }
              notifyOfError(message)
            }
            emulatorView?.hideLongRunningOperationIndicator()
          },
          failure = { throwable ->
            val message = throwable?.message ?: "Installation failed"
            notifyOfError(message)
            emulatorView?.hideLongRunningOperationIndicator()
          })
    }

    private fun notifyOfSuccess(message: String) {
      EMULATOR_NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(project)
    }

    private fun notifyOfError(message: String) {
      EMULATOR_NOTIFICATION_GROUP.createNotification(message, NotificationType.WARNING).notify(project)
    }

    private fun findDevice(): ListenableFuture<IDevice?> {
      val serialNumber = "emulator-${emulator.emulatorId.serialPort}"
      val adbFile = AndroidSdkUtils.getAdb(project) ?:
                    return Futures.immediateFailedFuture(RuntimeException("Could not find adb executable"))
      val bridgeFuture: ListenableFuture<AndroidDebugBridge> = AdbService.getInstance().getDebugBridge(adbFile)
      return bridgeFuture.transform(getAppExecutorService()) { debugBridge ->
        debugBridge.devices.find { it.isEmulator && it.serialNumber == serialNumber }
      }
    }
  }
}
