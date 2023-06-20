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

import com.android.flags.Flag
import com.android.tools.idea.flags.StudioFlags.DATABASE_INSPECTOR_OPEN_FILES_ENABLED
import org.jetbrains.annotations.TestOnly

/** Simple abstraction over enabled/disabling the Database Inspector feature. */
object DatabaseInspectorFlagController {
  val isOpenFileEnabled
    get() = DATABASE_INSPECTOR_OPEN_FILES_ENABLED.get()

  @TestOnly
  fun enableOpenFile(enabled: Boolean): Boolean =
    setFlagState(DATABASE_INSPECTOR_OPEN_FILES_ENABLED, enabled)

  /**
   * Clears an existing flag overrides, and if the flag value afterwards is not equal to
   * [desiredState], the method sets an override to the [desiredState].
   *
   * @return previous value of the flag (before overrides are cleared)
   */
  @TestOnly
  private fun setFlagState(flag: Flag<Boolean>, desiredState: Boolean): Boolean {
    val previous = flag.get()
    flag.clearOverride() // clears all existing overrides
    if (desiredState != flag.get()) flag.override(desiredState)
    return previous
  }
}
