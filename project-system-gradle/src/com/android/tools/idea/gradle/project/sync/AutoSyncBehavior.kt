/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service

enum class AutoSyncBehavior(val labelBundleKey: String) {
  Default("gradle.settings.autoSync.behavior.default"), Manual("gradle.settings.autoSync.behavior.manual")
}

private const val AUTO_SYNC_SETTING_KEY = "gradle.sync.auto.key"

@Service(Service.Level.APP)
object AutoSyncSettingStore {

  var autoSyncBehavior: AutoSyncBehavior
    get() = readAutoSyncPreference().takeUnless { isAutoSyncControlDisabled() } ?: AutoSyncBehavior.Default
    set(behavior) {
      storeAutoSyncPreference(behavior)
    }

  private fun isAutoSyncControlDisabled(): Boolean = !StudioFlags.SHOW_GRADLE_AUTO_SYNC_SETTING_UI.get()

  private fun readAutoSyncPreference(): AutoSyncBehavior? {
    return PropertiesComponent.getInstance().getValue(AUTO_SYNC_SETTING_KEY)?.let { stored ->
      AutoSyncBehavior.entries.find { it.name == stored }
    }
  }

  private fun storeAutoSyncPreference(behavior: AutoSyncBehavior) {
    PropertiesComponent.getInstance().setValue(AUTO_SYNC_SETTING_KEY, behavior.name)
  }
}