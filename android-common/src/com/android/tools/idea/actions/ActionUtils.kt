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
@file:JvmName("ActionUtils")
package com.android.tools.idea.actions

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.keymap.KeymapUtil

/**
 * Enables action tooltip that includes the name of the action, the shortcut if any, and
 * the additional text that, if not provided, defaults to the description of the action.
 */
fun Presentation.enableRichTooltip(action: AnAction, detailText: String? = description) {
  putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, HelpTooltip().apply {
    @Suppress("DialogTitleCapitalization")
    setTitle(text)
    setDescription(detailText)
    val shortcut = KeymapUtil.getFirstKeyboardShortcutText(action.shortcutSet)
    if (shortcut.isNotEmpty()) {
      setShortcut(shortcut)
    }
  })
}

/** Disables custom tooltip and reverts to the standard one. */
fun Presentation.disableRichTooltip() {
  putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, null);
}
