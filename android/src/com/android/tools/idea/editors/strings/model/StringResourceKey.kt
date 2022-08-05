/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * Holds a pair of string name (e.g. "app_name") and resource directory (e.g. app/src/main/res).
 *
 * A given name can exist in multiple resource directories (e.g. app/src/debug/res) and have
 * different values in each. As such, the translations editor must keep track of both separately.
 */
data class StringResourceKey
@JvmOverloads
constructor(val name: String, val directory: VirtualFile? = null) : Comparable<StringResourceKey> {
  override fun compareTo(other: StringResourceKey): Int =
      compareValuesBy(this, other, { it.name }, { it.directory?.path ?: "" })

  override fun toString(): String = if (directory == null) name else "$name ($directory)"
}
