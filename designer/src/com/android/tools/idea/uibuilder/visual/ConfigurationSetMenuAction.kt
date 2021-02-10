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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import icons.StudioIcons

/**
 * Pre-defined Configuration sets for visualization tools.
 */
@Suppress("unused") // Entries are indirectly used by for-loop.
enum class ConfigurationSet(val title: String,
                            val modelsProviderCreator: (ConfigurationSetListener) -> VisualizationModelsProvider,
                            val visible: Boolean = true) {
  PIXEL_DEVICES("Pixel Devices", { PixelDeviceModelsProvider }),
  WEAR_DEVICES("Wear OS Devices", { WearDeviceModelsProvider }),
  PROJECT_LOCALES("Project Locales", { LocaleModelsProvider }, StudioFlags.NELE_VISUALIZATION_LOCALE_MODE.get()),
  CUSTOM("Custom", { CustomModelsProvider(it) }),
  COLOR_BLIND_MODE("Color Blind", { ColorBlindModeModelsProvider }),
  LARGE_FONT("Font Sizes", { LargeFontModelsProvider })
}

interface ConfigurationSetListener {
  /**
   * Callback when selected [ConfigurationSet] is changed. For example, the selected [ConfigurationSet] is changed from
   * [ConfigurationSet.PIXEL_DEVICES] to [ConfigurationSet.PROJECT_LOCALES].
   */
  fun onSelectedConfigurationSetChanged(newConfigurationSet: ConfigurationSet)

  /**
   * Callback when the current [ConfigurationSet] changes the provided [com.android.tools.idea.common.model.NlModel]s. For example,
   * assuming the current selected [ConfigurationSet] is [ConfigurationSet.CUSTOM], and user add one more configuration. In such case this
   * callback is triggered because [ConfigurationSet.CUSTOM] now provides one more [com.android.tools.idea.common.model.NlModel].
   */
  fun onCurrentConfigurationSetUpdated()
}

/**
 * The dropdown action used to choose the configuration set in visualization tool.
 */
class ConfigurationSetMenuAction(private val listener: ConfigurationSetListener,
                                 defaultSet: ConfigurationSet)
  : DropDownAction(null, "Configuration Set", null) {

  companion object {
    val MENU_GROUPS = listOf(
      listOf(ConfigurationSet.PIXEL_DEVICES, ConfigurationSet.WEAR_DEVICES, ConfigurationSet.PROJECT_LOCALES),
      listOf(ConfigurationSet.CUSTOM),
      listOf(ConfigurationSet.COLOR_BLIND_MODE, ConfigurationSet.LARGE_FONT)
    )
  }

  private var currentConfigurationSet = defaultSet

  init {
    var isPreviousGroupEmpty = true
    for (group in MENU_GROUPS) {
      if (!isPreviousGroupEmpty) {
        addSeparator()
      }
      val groupItems = group.filter { it.visible }
      groupItems.forEach { add(SetConfigurationSetAction(it)) }
      isPreviousGroupEmpty = groupItems.isEmpty()
    }
  }

  override fun displayTextInToolbar() = true

  override fun update(e: AnActionEvent) {
    updatePresentation(e.presentation)
  }

  private fun updatePresentation(presentation: Presentation) {
    presentation.text = currentConfigurationSet.title
  }

  private fun selectConfigurationSet(newSet: ConfigurationSet) {
    if (newSet !== currentConfigurationSet) {
      currentConfigurationSet = newSet
      updatePresentation(templatePresentation)
      listener.onSelectedConfigurationSetChanged(newSet)
      getChildren(null).mapNotNull { it as? SetConfigurationSetAction }.forEach { it.updatePresentation() }
    }
  }

  private inner class SetConfigurationSetAction(private val configurationSet: ConfigurationSet)
    : AnAction(configurationSet.title,
               "Set configuration set to ${configurationSet.title}",
               if (currentConfigurationSet === configurationSet) StudioIcons.Common.CHECKED else null) {

    override fun actionPerformed(e: AnActionEvent) {
      selectConfigurationSet(configurationSet)
    }

    fun updatePresentation() {
      templatePresentation.icon = if (currentConfigurationSet === configurationSet) StudioIcons.Common.CHECKED else null
    }
  }
}
