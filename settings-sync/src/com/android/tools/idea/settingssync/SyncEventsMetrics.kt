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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.settingssync

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BackupAndSyncEvent
import com.google.wireless.android.sdk.stats.androidStudioEvent
import com.google.wireless.android.sdk.stats.backupAndSyncEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.settingsSync.core.SettingsSyncEventListener
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SyncSettingsEvent
import com.jetbrains.rd.util.collections.SynchronizedSet

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

  private val hasTrackedUsage = SynchronizedSet<BackupAndSyncEvent.Provider>()

  override fun settingChanged(event: SyncSettingsEvent) {
    val provider = getProvider()
    if (!hasTrackedUsage.add(provider)) return

    trackEvent(
      backupAndSyncEvent {
        type = BackupAndSyncEvent.Type.TYPE_SYNC
        providerInUse = provider
      }
    )
  }

  override fun enabledStateChanged(syncEnabled: Boolean) {
    val event = backupAndSyncEvent {
      providerInUse = getProvider()
      type =
        if (syncEnabled) BackupAndSyncEvent.Type.TYPE_ENABLED
        else BackupAndSyncEvent.Type.TYPE_DISABLED
    }

    trackEvent(event)
  }

  fun getProvider() =
    when (SettingsSyncLocalSettings.getInstance().providerCode) {
      PROVIDER_CODE_GOOGLE -> BackupAndSyncEvent.Provider.GOOGLE
      null -> BackupAndSyncEvent.Provider.UNKNOWN_PROVIDER
      else -> BackupAndSyncEvent.Provider.JETBRAINS // "jba"
    }

  override fun categoriesStateChanged() {
    trackEvent(
      backupAndSyncEvent {
        type = BackupAndSyncEvent.Type.TYPE_CHANGE_CATEGORIES
        providerInUse = getProvider()
      }
    )
  }

  fun trackEvent(event: BackupAndSyncEvent) {
    UsageTracker.log(
      androidStudioEvent {
        kind = AndroidStudioEvent.EventKind.BACKUP_AND_SYNC_EVENT
        backupAndSyncEvent = event
      }
    )
  }

  override fun dispose() = Unit

  companion object {
    fun getInstance() = service<SyncEventsMetrics>()
  }
}
