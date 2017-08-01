/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.sqlite

import org.jetbrains.annotations.TestOnly

import com.android.tools.idea.flags.StudioFlags.SQLITE_VIEWER_ENABLED

object SqliteViewer {
  val isFeatureEnabled: Boolean
    get() = SQLITE_VIEWER_ENABLED.get()

  @TestOnly
  fun enableFeature(enabled: Boolean): Boolean {
    val previous = isFeatureEnabled
    SQLITE_VIEWER_ENABLED.clearOverride()
    if (enabled != SQLITE_VIEWER_ENABLED.get()) {
      SQLITE_VIEWER_ENABLED.override(enabled)
    }
    return previous
  }
}
