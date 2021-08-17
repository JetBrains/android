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

import com.android.tools.idea.emulator.EmulatorSettings
import com.android.tools.idea.ui.wizard.SimpleStudioWizardLayout
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.model.ModelWizardDialog.CancellationPolicy.ALWAYS_CAN_CANCEL
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.MODELESS
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.PROJECT
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import org.jetbrains.android.actions.RunAndroidAvdManagerAction
import java.net.URL

class WearDevicePairingWizard {
  private var wizardDialog: ModelWizardDialog? = null

  @Synchronized
  fun show(project: Project) {
    wizardDialog?.apply {
      window?.toFront()  // We already have a dialog, just bring it to front and return
      return
    }

    val wizardAction = object : WizardAction {
      override fun closeAndStartAvd(project: Project) {
        wizardDialog?.close(CANCEL_EXIT_CODE)
        (ActionManager.getInstance().getAction(RunAndroidAvdManagerAction.ID) as RunAndroidAvdManagerAction).openAvdManager(project)
      }

      override fun restart(project: Project) {
        wizardDialog?.close(CANCEL_EXIT_CODE)
        show(project)
      }
    }

    val model = WearDevicePairingModel()
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
}