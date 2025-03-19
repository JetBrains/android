/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.settingssync.onboarding

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SettingsCategory.CODE
import com.intellij.openapi.components.SettingsCategory.KEYMAP
import com.intellij.openapi.components.SettingsCategory.PLUGINS
import com.intellij.openapi.components.SettingsCategory.SYSTEM
import com.intellij.openapi.components.SettingsCategory.TOOLS
import com.intellij.openapi.components.SettingsCategory.UI
import com.intellij.settingsSync.core.SettingsSyncBundle
import com.intellij.settingsSync.core.config.EDITOR_FONT_SUBCATEGORY_ID
import java.util.Locale

// The below pieces of code are from IJ. We duplicate their code because of lack of access but ease
// of maintain on both sides. TODO: we can polish this when we get to the stable version.
internal class Category(
  val category: SettingsCategory,
  val secondaryGroup: SyncSubcategoryGroup? = null,
) {

  val name: String
    get() {
      return SettingsSyncBundle.message("${categoryKey}.name")
    }

  val description: String
    get() {
      return SettingsSyncBundle.message("${categoryKey}.description")
    }

  private val categoryKey: String
    get() {
      return "settings.category." + category.name.lowercase(Locale.getDefault())
    }

  companion object {
    internal val DESCRIPTORS: List<Category> =
      listOf(
        Category(UI, SyncUiGroup()),
        Category(KEYMAP),
        Category(CODE),
        Category(PLUGINS, SyncPluginsGroup()),
        Category(TOOLS),
        Category(SYSTEM),
      )
  }
}

internal class SyncUiGroup : SyncSubcategoryGroup {

  private val descriptors =
    listOf(
      SettingsSyncSubcategoryDescriptor(
        SettingsSyncBundle.message("settings.category.ui.editor.font"),
        EDITOR_FONT_SUBCATEGORY_ID,
        false,
        false,
      )
    )

  override fun getDescriptors(): List<SettingsSyncSubcategoryDescriptor> {
    return descriptors
  }

  override fun isComplete() = false
}

internal const val BUNDLED_PLUGINS_ID = "bundled"

internal class SyncPluginsGroup : SyncSubcategoryGroup {
  private val storedDescriptors = HashMap<String, SettingsSyncSubcategoryDescriptor>()

  override fun getDescriptors(): List<SettingsSyncSubcategoryDescriptor> {
    val descriptors = ArrayList<SettingsSyncSubcategoryDescriptor>()
    val bundledPluginsDescriptor =
      getOrCreateDescriptor(SettingsSyncBundle.message("plugins.bundled"), BUNDLED_PLUGINS_ID)
    descriptors.add(bundledPluginsDescriptor)
    PluginManagerCore.plugins.forEach {
      if (
        !it.isBundled &&
          SettingsSyncPluginCategoryFinder.getPluginCategory(it) == SettingsCategory.PLUGINS
      ) {
        bundledPluginsDescriptor.isSubGroupEnd = true
        // NOTE: the code in `com.intellij.settingsSync.SettingsSyncFilteringKt.getSubCategory`
        // relies on the value being plugin ID
        descriptors.add(getOrCreateDescriptor(it.name, it.pluginId.idString))
      }
    }
    return descriptors
  }

  private fun getOrCreateDescriptor(name: String, id: String): SettingsSyncSubcategoryDescriptor {
    return if (storedDescriptors.containsKey(id)) {
      storedDescriptors[id]!!
    } else {
      val descriptor = SettingsSyncSubcategoryDescriptor(name, id, true, false)
      storedDescriptors[id] = descriptor
      descriptor
    }
  }
}

internal interface SyncSubcategoryGroup {
  fun getDescriptors(): List<SettingsSyncSubcategoryDescriptor>

  /**
   * Returns `true` if [getDescriptors] covers all the possible synchronizable elements of the
   * group. `false` if there are implicit elements not covered by the returned list of descriptors,
   * in other words a user can't disable the entire group by unselecting the explicitly described
   * items.
   */
  fun isComplete(): Boolean = true
}

internal data class SettingsSyncSubcategoryDescriptor(
  val name: String,
  val id: String,
  var isSelected: Boolean,
  var isSubGroupEnd: Boolean,
) {
  override fun toString(): String {
    return name
  }
}

internal object SettingsSyncPluginCategoryFinder {

  private val UI_CATEGORIES = setOf("Theme", "Editor Color Schemes")

  private val UI_EXTENSIONS = setOf("com.intellij.bundledColorScheme", "com.intellij.themeProvider")

  fun getPluginCategory(descriptor: IdeaPluginDescriptor): SettingsCategory {
    if (UI_CATEGORIES.contains(descriptor.category) || containsOnlyUIExtensions(descriptor)) {
      return UI
    }
    return PLUGINS
  }

  private fun containsOnlyUIExtensions(descriptor: IdeaPluginDescriptor): Boolean {
    if (descriptor is IdeaPluginDescriptorImpl) {
      return descriptor.epNameToExtensions.all { UI_EXTENSIONS.contains(it.key) }
    }
    return false
  }
}
