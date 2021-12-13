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
import com.android.tools.idea.emulator.EmulatorSettings
import com.android.tools.idea.ui.wizard.SimpleStudioWizardLayout
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.model.ModelWizardDialog.CancellationPolicy.ALWAYS_CAN_CANCEL
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.MODELESS
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.PROJECT
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.jetbrains.android.util.AndroidBundle.message
import java.net.URL

// Keep an instance, so if the wizard is already running, just bring it to the front
private var wizardDialog: ModelWizardDialog? = null

internal class WearDevicePairingWizard {
  @Synchronized
  private fun show(project: Project?, selectedDevice: PairingDevice?) {
    wizardDialog?.apply {
      window?.toFront()  // We already have a dialog, just bring it to the front and return
      return@show
    }

    val wizardAction = object : WizardAction {
      override fun closeAndStartAvd(project: Project?) {
        wizardDialog?.close(CANCEL_EXIT_CODE)

        if (project == null) {
          ActionManager.getInstance().getAction("WelcomeScreen.RunDeviceManager").actionPerformed(
            AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null) { null }
          )
        }
        else {
          // Action id is from com.android.tools.idea.devicemanager.legacy.actions.RunAndroidAvdManagerAction.
          val runAndroidAvdManagerAction = ActionManager.getInstance().getAction("Android.RunAndroidAvdManager")
          val projectContext = SimpleDataContext.getProjectContext(project)
          ActionUtil.invokeAction(runAndroidAvdManagerAction, projectContext, ActionPlaces.UNKNOWN, null, null)
        }
      }

      override fun restart(project: Project?) {
        wizardDialog?.close(CANCEL_EXIT_CODE)
        show(project, selectedDevice)
      }
    }

    val model = WearDevicePairingModel()
    when (selectedDevice?.isWearDevice) {
      true -> model.selectedWearDevice.setNullableValue(selectedDevice)
      false -> model.selectedPhoneDevice.setNullableValue(selectedDevice)
    }
    val modelWizard = ModelWizard.Builder()
      .addStep(DeviceListStep(model, project, wizardAction))
      .build()

    // Remove the dialog reference when the dialog is disposed (closed).
    Disposer.register(modelWizard) { wizardDialog = null }

    WearPairingManager.setDeviceListListener(model, wizardAction)

    val modality = if (EmulatorSettings.getInstance().launchInToolWindow) MODELESS else PROJECT
    wizardDialog = StudioWizardDialogBuilder(modelWizard, "Wear OS emulator pairing assistant")
      .setProject(project)
      .setHelpUrl(URL(WEAR_DOCS_LINK))
      .setModalityType(modality)
      .setCancellationPolicy(ALWAYS_CAN_CANCEL)
      .setPreferredSize(JBUI.size(560, 420))
      .setMinimumSize(JBUI.size(400, 250))
      .build(SimpleStudioWizardLayout())

    wizardDialog?.show()
  }

  @UiThread
  fun show(project: Project?, selectedDeviceId: String?) {
    object : Task.Modal(project, message("wear.assistant.device.connection.balloon.link"), true) {
      var selectedDevice: PairingDevice? = null

      override fun run(indicator: ProgressIndicator) {
        if (selectedDeviceId != null) {
          selectedDevice = WearPairingManager.findDevice(selectedDeviceId)
        }
      }

      override fun onFinished() {
        show(project, selectedDevice)
      }
    }.queue()
  }
}