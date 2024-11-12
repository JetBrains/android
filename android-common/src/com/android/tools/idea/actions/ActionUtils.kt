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

/** Enables action tooltip that includes the name and description of the action. */
fun AnAction.enableRichTooltip(presentation: Presentation) {
  presentation.putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, HelpTooltip().apply {
    @Suppress("DialogTitleCapitalization")
    setTitle(presentation.text)
    setDescription(presentation.description)
    val shortcut = KeymapUtil.getFirstKeyboardShortcutText(shortcutSet)
    if (shortcut.isNotEmpty()) {
      setShortcut(shortcut)
    }
  })
}
