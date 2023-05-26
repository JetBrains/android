/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.keymap.KeymapExtension
import com.intellij.openapi.keymap.KeymapGroup
import com.intellij.openapi.keymap.KeymapGroupFactory
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition

/** The name of the Settings > Keymap section. */
private const val KEYMAP_SECTION_NAME = "Running Devices"
/** The action group defining the contents of the Settings > Keymap > Running Devices section. */
private const val ACTION_GROUP = "Streaming"

/**
 * Adds the Settings -> Keymap > Running Devices section.
 */
class StreamingKeymapExtension : KeymapExtension {

  override fun createGroup(filtered: Condition<in AnAction>, project: Project?): KeymapGroup? {
    val keymapGroup = KeymapGroupFactory.getInstance().createGroup(KEYMAP_SECTION_NAME)

    for (action in ActionsTreeUtil.getActions(ACTION_GROUP)) {
      ActionsTreeUtil.addAction(keymapGroup, action, filtered)
    }

    return keymapGroup
  }
}