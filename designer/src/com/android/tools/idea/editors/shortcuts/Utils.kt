/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.editors.shortcuts

import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.keymap.KeymapUtil

/** Action ID of the IDE declared force refresh action (see PlatformActions.xml). This allows us to re-use the shortcut of the declared action. */
private const val FORCE_REFRESH_ACTION_ID = "ForceRefresh"

/** [ShortcutSet] that triggers a build and refreshes the preview */
fun getBuildAndRefreshShortcut(): ShortcutSet = KeymapUtil.getActiveKeymapShortcuts(FORCE_REFRESH_ACTION_ID)

/**
 * Returns the textual representation of the given [ShortcutSet]. If there is no shortcut, this method will return an empty string.
 * An optional [prefix] and [suffix] can be specified. These are only returned if there is a shortcut and the result string is not empty.
 */
fun ShortcutSet.asString(prefix: String = " (", suffix: String = ")"): String {
  val shortcutString = KeymapUtil.getFirstKeyboardShortcutText(this)
  return if (shortcutString.isNotEmpty()) "${prefix}${shortcutString}${suffix}" else ""
}
