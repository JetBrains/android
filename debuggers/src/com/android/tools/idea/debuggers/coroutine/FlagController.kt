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
package com.android.tools.idea.debuggers.coroutine

import com.android.flags.Flag
import com.android.tools.idea.flags.StudioFlags
import org.jetbrains.annotations.TestOnly

object FlagController {
  val isCoroutineDebuggerEnabled get() = StudioFlags.COROUTINE_DEBUGGER_ENABLE.get()

  @TestOnly
  fun enableCoroutineDebugger(enabled: Boolean): Boolean = setFlagState(StudioFlags.COROUTINE_DEBUGGER_ENABLE, enabled)

  private fun setFlagState(flag: Flag<Boolean>, desiredState: Boolean): Boolean {
    val previous = flag.get()
    flag.clearOverride()
    if (desiredState != flag.get()) {
      flag.override(desiredState)
    }
    return previous;
  }
}