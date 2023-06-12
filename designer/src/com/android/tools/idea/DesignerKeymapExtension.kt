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
package com.android.tools.idea

import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.keymap.KeymapExtension
import com.intellij.openapi.keymap.KeymapGroup
import com.intellij.openapi.keymap.KeymapGroupFactory
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition

/**
 * The section name displayed in Preferences -> Keymap
 */
private const val KEYMAP_SECTION_NAME = "Android Design Tools"

/**
 * This class add the Keymap section of Android Design Tools which shows as Preferences -> Keymap -> [KEYMAP_SECTION_NAME].
 * Its structure would looks like:
 *
 * Android Design Tools
 *   common_action1
 *   common_action2
 *   ...
 *   Layout Editor
 *     layout_editor_action1
 *     layout_editor_action2
 *     ...
 *   Navigation Editor
 *     navigation_editor_action1
 *     navigation_editor_action2
 *     ...
 *   another_tool1
 *     ...
 *   another_tool2
 *     ...
 *   ...
 */
class DesignerKeymapExtension : KeymapExtension {
  override fun createGroup(filtered: Condition<in AnAction>, project: Project?): KeymapGroup? {
    val keymapGroup = KeymapGroupFactory.getInstance().createGroup(KEYMAP_SECTION_NAME)

    val sharedActionGroup = ActionsTreeUtil.getActions(DesignerActions.GROUP_COMMON)
    for (action in sharedActionGroup) {
      ActionsTreeUtil.addAction(keymapGroup, action, filtered)
    }

    val keymapGroups = ActionsTreeUtil.getActions(DesignerActions.GROUP_TOOLS)
    for (action in keymapGroups) {
      ActionsTreeUtil.addAction(keymapGroup, action, filtered, false)
    }
    return keymapGroup
  }
}
