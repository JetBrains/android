/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.ui.awt.ComposePanel
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing

internal class ConfigureDeviceStep
internal constructor(model: AddDeviceWizardModel, private val project: Project?) :
  ModelWizardStep<AddDeviceWizardModel>(model, "") {

  private val component = initComponent()

  private fun initComponent(): JComponent {
    @OptIn(ExperimentalJewelApi::class) enableNewSwingCompositing()
    val component = ComposePanel()

    component.setContent {
      ConfigureDevicePanel(
        model.device,
        model.systemImages,
        model.skins,
        Context(component, project),
        { model.device = it },
        ::importSkin,
      )
    }

    return component
  }

  private fun importSkin() {
    // TODO Validate the skin

    val skin =
      FileChooser.chooseFile(
        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        component,
        project,
        null,
      )

    if (skin != null) {
      model.importSkin(skin.toNioPath())
    }
  }

  public override fun getComponent() = component
}
