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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.flags.StudioFlags

interface ConfigurationSet {
  /**
   * The unique id of this models provider.
   */
  val id: String

  /**
   * The name of this this models provider. The name can be duplicated and it shows on the dropdown menu of configuration set action.
   */
  val name: String
  val visible: Boolean
    get() = true
  fun createModelsProvider(listener: ConfigurationSetListener): VisualizationModelsProvider

  object PixelDevices : ConfigurationSet {
    override val id = "pixelDevices"
    override val name = "Pixel Devices"
    override fun createModelsProvider(listener: ConfigurationSetListener) = PixelDeviceModelsProvider
  }

  object WearDevices : ConfigurationSet {
    override val id = "wearOsDevices"
    override val name = "Wear OS Devices"
    override fun createModelsProvider(listener: ConfigurationSetListener) = WearDeviceModelsProvider
  }

  object ProjectLocal : ConfigurationSet {
    override val id = "projectLocales"
    override val name = "Project Locales"
    override fun createModelsProvider(listener: ConfigurationSetListener) = LocaleModelsProvider
    override val visible = StudioFlags.NELE_VISUALIZATION_LOCALE_MODE.get()
  }

  object Custom : ConfigurationSet {
    override val id = "custom"
    override val name = "Custom"
    override fun createModelsProvider(listener: ConfigurationSetListener) = CustomModelsProvider(listener)
  }

  object ColorBlindMode : ConfigurationSet {
    override val id = "colorBlind"
    override val name = "Color Blind"
    override fun createModelsProvider(listener: ConfigurationSetListener) = ColorBlindModeModelsProvider
  }

  object LargeFont : ConfigurationSet {
    override val id = "fontSizes"
    override val name = "Font Sizes"
    override fun createModelsProvider(listener: ConfigurationSetListener) = LargeFontModelsProvider
  }

  object Tablets : ConfigurationSet {
    override val id = "tablets"
    override val name = "Tablets"
    override fun createModelsProvider(listener: ConfigurationSetListener) = TabletModelsProvider
    override val visible = StudioFlags.NELE_TABLET_SUPPORT.get()
  }
}

object ConfigurationSetProvider {
  @JvmField
  val defaultSet = ConfigurationSet.PixelDevices

  @JvmStatic
  fun getConfigurationSets(): List<ConfigurationSet> =
    listOf(ConfigurationSet.PixelDevices,
           ConfigurationSet.WearDevices,
           ConfigurationSet.Tablets,
           ConfigurationSet.ProjectLocal,
           ConfigurationSet.Custom,
           ConfigurationSet.ColorBlindMode,
           ConfigurationSet.LargeFont)

  @JvmStatic
  fun getGroupedConfigurationSets(): List<List<ConfigurationSet>> =
    listOf(listOf(ConfigurationSet.PixelDevices, ConfigurationSet.WearDevices, ConfigurationSet.Tablets, ConfigurationSet.ProjectLocal),
           listOf(ConfigurationSet.Custom),
           listOf(ConfigurationSet.ColorBlindMode, ConfigurationSet.LargeFont))

  @JvmStatic
  fun getConfigurationById(id: String): ConfigurationSet? = getConfigurationSets().firstOrNull { it.id == id }
}

interface ConfigurationSetListener {
  /**
   * Callback when selected [ConfigurationSet] is changed. For example, the selected [ConfigurationSet] is changed from
   * [ConfigurationSet.PixelDevices] to [ConfigurationSet.ProjectLocal].
   */
  fun onSelectedConfigurationSetChanged(newConfigurationSet: ConfigurationSet)

  /**
   * Callback when the current [ConfigurationSet] changes the provided [com.android.tools.idea.common.model.NlModel]s. For example,
   * assuming the current selected [ConfigurationSet] is [ConfigurationSet.Custom], and user add one more configuration. In such case this
   * callback is triggered because [ConfigurationSet.Custom] now provides one more [com.android.tools.idea.common.model.NlModel].
   */
  fun onCurrentConfigurationSetUpdated()
}
