/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui.toolbar.actions

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.appinspection.ide.ui.ICON_EMULATOR
import com.android.tools.idea.appinspection.ide.ui.ICON_PHONE
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.appinspection.ide.ui.buildDeviceName
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.ui.LayeredIcon
import javax.swing.Icon
import javax.swing.JComponent

@VisibleForTesting
val ICON_LEGACY_PHONE: Icon =
  LayeredIcon.layeredIcon { arrayOf(ICON_PHONE, AllIcons.General.WarningDecorator) }

@VisibleForTesting
val ICON_LEGACY_EMULATOR: Icon =
  LayeredIcon.layeredIcon { arrayOf(ICON_EMULATOR, AllIcons.General.WarningDecorator) }

// TODO this class can be removed once the flag
// DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED is removed
//  and we stop using [SelectProcessAction].
// A [DropDownAction] with a button.
data class DropDownActionWithButton(
  val dropDownAction: DropDownAction,
  val getButton: () -> JComponent?,
)

/** Factory class responsible for creating either a device or process picker. */
object TargetSelectionActionFactory {
  fun getAction(layoutInspector: LayoutInspector): DropDownActionWithButton? {
    return if (LayoutInspectorSettings.getInstance().autoConnectEnabled) {
      // auto-connect is enabled, return a device picker
      val action = getDeviceSelectorAction(layoutInspector) ?: return null
      DropDownActionWithButton(action) { action.button }
    } else {
      // auto-connect is not enabled, return a process picker
      val action = getProcessSelectorAction(layoutInspector) ?: return null
      DropDownActionWithButton(action) { action.button }
    }
  }

  /**
   * Returns the process picker to use when Layout Inspector is running inside the Running Devices
   * Tool Window.
   */
  fun getSingleDeviceProcessPicker(
    layoutInspector: LayoutInspector,
    targetDeviceSerialNumber: String,
  ): SingleDeviceSelectProcessAction? {
    val model = layoutInspector.deviceModel ?: return null
    return SingleDeviceSelectProcessAction(
      deviceModel = model,
      targetDeviceSerialNumber = targetDeviceSerialNumber,
      onProcessSelected = { newProcess ->
        layoutInspector.processModel?.selectedProcess = newProcess
      },
    )
  }

  // TODO remove once the flag DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
  // is removed
  private fun getProcessSelectorAction(layoutInspector: LayoutInspector): SelectProcessAction? {
    val model = layoutInspector.processModel ?: return null
    return SelectProcessAction(
      model = model,
      supportsOffline = false,
      createProcessLabel = (SelectProcessAction)::createCompactProcessLabel,
      stopPresentation =
        SelectProcessAction.StopPresentation(
          "Stop Inspector",
          "Stop running the layout inspector against the current process",
        ),
      onStopAction = { layoutInspector.stopInspector() },
      customDeviceAttribution = TargetSelectionActionFactory::deviceAttribution,
    )
  }

  private fun getDeviceSelectorAction(layoutInspector: LayoutInspector): SelectDeviceAction? {
    val model = layoutInspector.deviceModel ?: return null
    return SelectDeviceAction(
      layoutInspector.inspectorModel.project.service<DeviceProvisionerService>().deviceProvisioner,
      layoutInspector.inspectorModel.scope,
      deviceModel = model,
      onDeviceSelected = { newDevice ->
        layoutInspector.foregroundProcessDetection?.startPollingDevice(newDevice)
      },
      onProcessSelected = { newProcess ->
        layoutInspector.processModel?.selectedProcess = newProcess
      },
      onDetachAction = { layoutInspector.stopInspector() },
      customDeviceAttribution = TargetSelectionActionFactory::deviceAttribution,
    )
  }

  private fun deviceAttribution(device: DeviceDescriptor, event: AnActionEvent) =
    when {
      device.apiLevel.majorVersion < AndroidVersion.VersionCodes.M -> {
        event.presentation.isEnabled = false
        event.presentation.text =
          "${device.buildDeviceName()} (Unsupported for API < ${AndroidVersion.VersionCodes.M})"
      }
      device.apiLevel.majorVersion < AndroidVersion.VersionCodes.Q -> {
        event.presentation.icon = device.toLegacyIcon()
        event.presentation.text =
          "${device.buildDeviceName()} (Live inspection disabled for API < ${AndroidVersion.VersionCodes.Q})"
      }
      else -> {}
    }
}

private fun DeviceDescriptor?.toLegacyIcon() =
  if (this?.isEmulator == true) ICON_LEGACY_EMULATOR else ICON_LEGACY_PHONE
