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
package com.android.tools.idea.profilers.appinspection

import com.android.tools.idea.appinspection.ide.AppInspectionToolWindowService
import com.android.tools.idea.appinspection.ide.ui.AppInspectionToolWindow
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.profilers.ProfilerPreferences
import com.android.tools.profilers.appinspection.AppInspectionMigrationServices
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

// The intellij preference system expects boolean preferences to always start off with a false value and be
// set to true later. This is why the preference settings are defined as HIDE_* as opposed to SHOW_*. It also
// causes the boolean logic below to be a bit harder to read than it needs to be.
private const val HIDE_SYSTEM_EVENTS_MIGRATION_MESSAGING = "hide.system.events.migration.messaging"
private const val HIDE_NETWORK_PROFILER_MIGRATION_MESSAGING = "hide.network.profiler.migration.messaging"

// TODO(b/188695273): DELETE ME
class AppInspectionIntellijMigrationServices(
  private val profilerPreferences: ProfilerPreferences,
  private val project: Project,
) : AppInspectionMigrationServices {
  override fun isMigrationEnabled() = StudioFlags.PROFILER_MIGRATION_TO_APPINSPECTION.get()

  override var isSystemEventsMigrationDialogEnabled: Boolean
    get() = isMigrationEnabled() && !profilerPreferences.getBoolean(HIDE_SYSTEM_EVENTS_MIGRATION_MESSAGING, false)
    set(value) = profilerPreferences.setBoolean(HIDE_SYSTEM_EVENTS_MIGRATION_MESSAGING, !value)

  override var isNetworkProfilerMigrationDialogEnabled: Boolean
    get() = isMigrationEnabled() && !profilerPreferences.getBoolean(HIDE_NETWORK_PROFILER_MIGRATION_MESSAGING, false)
    set(value) = profilerPreferences.setBoolean(HIDE_NETWORK_PROFILER_MIGRATION_MESSAGING, !value)

  override fun openAppInspectionToolWindow(tabName: String) {
    AppInspectionToolWindow.show(project) {
      project.service<AppInspectionToolWindowService>().appInspectionToolWindowControl?.setTab(tabName)
    }
  }
}