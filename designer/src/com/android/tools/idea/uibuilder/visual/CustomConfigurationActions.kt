/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.LightCalloutPopup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiFile
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet

private const val MAX_CUSTOM_CONFIGURATION_NUMBER = 12
private const val ENABLED_TEXT = "Add configuration"
private const val DISABLED_TEXT = "Cannot add more than $MAX_CUSTOM_CONFIGURATION_NUMBER configurations"

/**
 * Action for adding custom configuration into the given [CustomModelsProvider].
 * For now the implementation is showing [CustomConfigurationAttributeCreationPalette] as a popup dialog and add the configuration picked from it.
 */
class AddCustomConfigurationAction(private val file: PsiFile,
                                   private val facet: AndroidFacet,
                                   private val provider: CustomModelsProvider)
  : AnAction(StudioIcons.NavEditor.Toolbar.ADD_DESTINATION) {

  init {
    templatePresentation.text = getDisplayText()
    templatePresentation.description = "Adding a custom configuration"
  }

  private fun getDisplayText() = if (provider.configurationAttributes.size < MAX_CUSTOM_CONFIGURATION_NUMBER) ENABLED_TEXT else DISABLED_TEXT

  private fun isEnabled() = provider.configurationAttributes.size < MAX_CUSTOM_CONFIGURATION_NUMBER

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = LightCalloutPopup()

    val content = CustomConfigurationAttributeCreationPalette(file, facet) { attributes ->
      provider.addCustomConfigurationAttributes(attributes)
      dialog.close()
    }
    val owner = e.inputEvent.component
    val location = owner.locationOnScreen
    location.translate(owner.width / 2, owner.height)

    dialog.show(content, null, location)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = getDisplayText()
    e.presentation.isEnabled = isEnabled()
  }
}
