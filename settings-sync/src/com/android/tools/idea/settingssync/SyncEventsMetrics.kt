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

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BackupAndSyncEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.settingsSync.core.SettingsSyncEventListener
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings

@Service
class SyncEventsMetrics : SettingsSyncEventListener, Disposable {
  init {
    SettingsSyncEvents.getInstance().addListener(this, parentDisposable = this)
  }

  class Initializer : ProjectActivity {
    override suspend fun execute(project: Project) {
      getInstance()
    }
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    if (syncEnabled == false) return

    val provider =
      SettingsSyncLocalSettings.getInstance().providerCode ?: error("Provider code is not set")
    val event =
      BackupAndSyncEvent.newBuilder().apply {
        providerInUse =
          when (provider) {
            PROVIDER_CODE_GOOGLE -> BackupAndSyncEvent.Provider.GOOGLE
            else -> BackupAndSyncEvent.Provider.JETBRAINS // "jba"
          }
      }

    trackEvent(event)
  }

  fun trackEvent(event: BackupAndSyncEvent.Builder) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.BACKUP_AND_SYNC_EVENT)
        .setBackupAndSyncEvent(event)
    )
  }

  override fun dispose() = Unit

  companion object {
    fun getInstance() = service<SyncEventsMetrics>()
  }
}
