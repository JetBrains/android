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

package com.android.tools.idea.debug

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.FieldVisibilityProvider
import com.sun.jdi.Field

private val SHADOW_FIELDS = setOf(
  "shadow${'$'}_klass_",
  "shadow${'$'}_monitor_",
)

internal class AndroidFieldVisibilityProvider : FieldVisibilityProvider {
  override fun shouldDisplay(field: Field): Boolean {
    return when {
      !DebuggerUtils.isAndroidVM(field.virtualMachine()) -> true
      field.declaringType().name() != "java.lang.Object" -> true
      else -> field.name() !in SHADOW_FIELDS
    }
  }
}