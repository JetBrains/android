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
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiFile
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import java.util.UUID

private const val MAX_CUSTOM_CONFIGURATION_NUMBER = 12
private const val ENABLED_TEXT = "Add configuration"
private const val DISABLED_TEXT = "Cannot add more than $MAX_CUSTOM_CONFIGURATION_NUMBER configurations"

/**
 * Action for adding custom set in Validation Tool.
 */
class AddCustomConfigurationSetAction(private val onAdd: (String) -> Unit) : AnAction() {

  init {
    templatePresentation.text = "Add a New Custom Category"
    templatePresentation.description = "Create a new custom category in validation tool."
    // FIXME: This icon is chosen because its design looks like a stack of layouts.
    //        We may need to have a new icon or move it to common.
    templatePresentation.icon = StudioIcons.NavEditor.Toolbar.NESTED_GRAPH
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = true
    e.presentation.icon = StudioIcons.NavEditor.Toolbar.NESTED_GRAPH
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = LightCalloutPopup()

    val content = CustomConfigurationSetCreatePalette { customSetName ->
      val id = UUID.randomUUID().toString()
      val createdConfigSet = CustomConfigurationSet(customSetName, emptyList())
      VisualizationUtil.setCustomConfigurationSet(id, createdConfigSet)
      dialog.close()
      onAdd(id)
    }
    val owner = e.inputEvent.component
    val location = owner.locationOnScreen
    location.translate(owner.width / 2, owner.height)

    dialog.show(content, null, location)
  }
}

class RemoveCustomConfigurationSetAction(val configurationSet: ConfigurationSet, private val onRemove: () -> Unit)
  : AnAction(AllIcons.Actions.GC) {
  init {
    templatePresentation.text = "Delete This Category"
    templatePresentation.description = "Delete the current custom custom category"
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = configurationSet is UserDefinedCustom
  }

  override fun actionPerformed(e: AnActionEvent) {
    val idToRemove = configurationSet.id
    VisualizationUtil.setCustomConfigurationSet(idToRemove, null)
    onRemove()
  }
}


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
    templatePresentation.description = "Adding a custom configuration into this category"
  }

  private fun getDisplayText() =
    if (provider.customConfigSet.customConfigAttributes.size < MAX_CUSTOM_CONFIGURATION_NUMBER) ENABLED_TEXT else DISABLED_TEXT

  private fun isEnabled() = provider.customConfigSet.customConfigAttributes.size < MAX_CUSTOM_CONFIGURATION_NUMBER

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
