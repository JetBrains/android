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

import com.android.tools.idea.deviceManager.avdmanager.AvdWizardUtils
import com.intellij.icons.AllIcons
import java.awt.event.ActionEvent
import javax.swing.Icon

open class EditAvdAction @JvmOverloads constructor(
  avdInfoProvider: AvdInfoProvider, text: String = "Edit", description: String = "Edit this AVD", icon: Icon = AllIcons.Actions.Edit
) : AvdUiAction(
  avdInfoProvider, text, description, icon
) {
  override fun actionPerformed(e: ActionEvent?) {
    val dialog = AvdWizardUtils.createAvdWizard(avdInfoProvider.avdProviderComponent, project, avdInfo)
    if (dialog.showAndGet()) {
      refreshAvds()
    }
  }

  override fun isEnabled(): Boolean = avdInfo != null
}