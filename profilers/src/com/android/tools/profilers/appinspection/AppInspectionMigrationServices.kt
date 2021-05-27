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
package com.android.tools.profilers.appinspection

/**
 * Allows for querying and setting of migration specific persistent preferences.
 *
 * TODO(b/188695273): DELETE ME
 */
interface AppInspectionMigrationServices {
  /**
   * Whether migration is enabled. If not enabled, profilers should behave the same way as before.
   */
  fun isMigrationEnabled(): Boolean

  /**
   * Whether the system events migration dialog has been explicitly dismissed by the user.
   *
   * If migration is disabled, this is set to false. Otherwise this defaults to true.
   */
  var isSystemEventsMigrationDialogEnabled: Boolean

  /**
   * Whether the network profiler migration dialog has been explicitly dismissed by the user.
   *
   * If migration is disabled, this is set to false. Otherwise this defaults to true.
   */
  var isNetworkProfilerMigrationDialogEnabled: Boolean

  fun openAppInspectionToolWindow(tabName: String)
}