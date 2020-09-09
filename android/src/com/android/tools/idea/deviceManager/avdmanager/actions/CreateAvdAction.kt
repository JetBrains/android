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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.tools.idea.deviceManager.avdmanager.AvdOptionsModel
import com.android.tools.idea.deviceManager.avdmanager.AvdWizardUtils
import com.intellij.icons.AllIcons
import java.awt.event.ActionEvent

/**
 * Invoke the wizard to create a new AVD
 */
class CreateAvdAction(avdInfoProvider: AvdInfoProvider) : AvdUiAction(
  avdInfoProvider, "Create Virtual Device...", "Create a new Android Virtual Device", AllIcons.General.Add
) {
  override fun actionPerformed(e: ActionEvent?) {
    val avdOptionsModel = AvdOptionsModel(null)
    val dialog = AvdWizardUtils.createAvdWizard(avdInfoProvider.avdProviderComponent, project, avdOptionsModel)
    if (dialog.showAndGet()) {
      // Select the AVD that we just created
      refreshAvdsAndSelect(avdOptionsModel.createdAvd)
    }
  }

  override fun isEnabled(): Boolean = true
}