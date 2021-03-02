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

import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper


class WearDevicePairingWizard(val project: Project) {

  fun show() {
    val model = WearDevicePairingModel()
    val modelWizard = ModelWizard.Builder()
      .addStep(DeviceListStep(model, project))
      .build()

    ModelWizardDialog(
      modelWizard,
      "Wear OS emulator pairing assistant",
      null,
      project,
      WizardUtils.toUrl("https://developer.android.com/training/wearables/apps/creating#pair-phone-with-avd"),
      DialogWrapper.IdeModalityType.MODELESS,
      ModelWizardDialog.CancellationPolicy.ALWAYS_CAN_CANCEL
    ).show()
  }
}