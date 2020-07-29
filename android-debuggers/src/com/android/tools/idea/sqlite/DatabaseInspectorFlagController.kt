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
package com.android.tools.idea.sqlite

import com.android.tools.idea.flags.StudioFlags.DATABASE_INSPECTOR_ENABLED
import com.android.tools.idea.flags.StudioFlags.DATABASE_INSPECTOR_OFFLINE_MODE_ENABLED
import com.android.tools.idea.flags.StudioFlags.DATABASE_INSPECTOR_OPEN_FILES_ENABLED
import org.jetbrains.annotations.TestOnly

/**
 * Simple abstraction over enabled/disabling the Database Inspector feature.
 */
object DatabaseInspectorFlagController {
  val isFeatureEnabled get() = DATABASE_INSPECTOR_ENABLED.get()
  val isOpenFileEnabled get() = DATABASE_INSPECTOR_ENABLED.get() && DATABASE_INSPECTOR_OPEN_FILES_ENABLED.get()
  val isOfflineModeEnabled get() = DATABASE_INSPECTOR_ENABLED.get() && DATABASE_INSPECTOR_OFFLINE_MODE_ENABLED.get()

  @TestOnly
  fun enableFeature(enabled: Boolean): Boolean {
    val previous = isFeatureEnabled
    DATABASE_INSPECTOR_ENABLED.clearOverride()
    if (enabled != DATABASE_INSPECTOR_ENABLED.get()) {
      DATABASE_INSPECTOR_ENABLED.override(enabled)
    }
    return previous
  }

  @TestOnly
  fun enableOpenFile(enabled: Boolean): Boolean {
    val previous = isOpenFileEnabled
    DATABASE_INSPECTOR_OPEN_FILES_ENABLED.clearOverride()
    if (enabled != DATABASE_INSPECTOR_OPEN_FILES_ENABLED.get()) {
      DATABASE_INSPECTOR_OPEN_FILES_ENABLED.override(enabled)
    }
    return previous
  }

  @TestOnly
  fun enableOfflineMode(enabled: Boolean): Boolean {
    val previous = isOfflineModeEnabled
    DATABASE_INSPECTOR_OFFLINE_MODE_ENABLED.clearOverride()
    if (enabled != DATABASE_INSPECTOR_OFFLINE_MODE_ENABLED.get()) {
      DATABASE_INSPECTOR_OFFLINE_MODE_ENABLED.override(enabled)
    }
    return previous
  }
}