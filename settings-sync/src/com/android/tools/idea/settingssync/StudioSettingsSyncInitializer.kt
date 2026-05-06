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
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP

private val IJ_SETTINGS_SYNC_PLUGIN_ID = PluginId.getId("com.intellij.settingsSync")

internal fun checkIfFeaturePluginEnabled(): Boolean = PluginManagerCore.getPlugin(IJ_SETTINGS_SYNC_PLUGIN_ID)?.isEnabled == true

/**
 * Initializes Studio-specific Settings Sync behaviors upon application startup.
 *
 * If the Settings Sync feature is disabled, it unregisters related settings and actions. Otherwise, it wraps the default actions with
 * [StudioSettingsSyncAction] to present the custom onboarding flow.
 */
class StudioSettingsSyncInitializer : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: List<String?>) {
    if (!StudioFlags.SETTINGS_SYNC_ENABLED.get() && !checkIfFeaturePluginEnabled()) {
      disableConfigurable()

      ActionManager.getInstance().unregisterAction("SettingsSyncOpenSettingsAction")
      ActionManager.getInstance().unregisterAction("SettingsSyncStatusAction")
    } else if (StudioFlags.SETTINGS_SYNC_ENABLED.get()) {
      // Replaces upstream IntelliJ Settings Sync actions to show our custom onboarding dialog.
      val actionManager = ActionManager.getInstance()

      val originalOpenAction = actionManager.getAction("SettingsSyncOpenSettingsAction")
      if (originalOpenAction != null) {
        actionManager.replaceAction("SettingsSyncOpenSettingsAction", StudioSettingsSyncAction(originalOpenAction))
      }

      val originalStatusAction = actionManager.getAction("SettingsSyncStatusAction")
      if (originalStatusAction != null) {
        actionManager.replaceAction("SettingsSyncStatusAction", StudioSettingsSyncAction(originalStatusAction))
      }
    }
  }

  private fun disableConfigurable() {
    val extension =
      Configurable.APPLICATION_CONFIGURABLE.extensionList.firstOrNull {
        it.providerClass == "com.intellij.settingsSync.core.config.SettingsSyncConfigurableProvider"
      } ?: return

    ExtensionPointName<ConfigurableEP<Configurable>>("com.intellij.applicationConfigurable").point.unregisterExtension(extension)
  }
}
