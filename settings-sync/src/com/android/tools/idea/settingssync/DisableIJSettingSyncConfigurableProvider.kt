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
package com.android.tools.idea.settingssync

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP

private val IJ_SETTINGS_SYNC_PLUGIN_ID = PluginId.getId("com.intellij.settingsSync")

internal fun checkIfFeaturePluginEnabled(): Boolean =
  PluginManagerCore.getPlugin(IJ_SETTINGS_SYNC_PLUGIN_ID)?.isEnabled == true

/**
 * This is to hide the IJ feature configurable behind the feature flag.
 *
 * If users explicitly enable the feature plugin from the JetBrains Marketplace, this configurable
 * will still be visible.
 */
class DisableIJSettingSyncConfigurableProvider : ApplicationInitializedListener {

  override suspend fun execute() {
    if (!StudioFlags.SETTINGS_SYNC_ENABLED.get() && !checkIfFeaturePluginEnabled()) {
      disableConfigurable()
    }
  }

  private fun disableConfigurable() {
    val extension =
      Configurable.APPLICATION_CONFIGURABLE.extensionList.firstOrNull {
        it.providerClass == "com.intellij.settingsSync.core.config.SettingsSyncConfigurableProvider"
      } ?: return

    ExtensionPointName<ConfigurableEP<Configurable>>("com.intellij.applicationConfigurable")
      .point
      .unregisterExtension(extension)
  }
}
