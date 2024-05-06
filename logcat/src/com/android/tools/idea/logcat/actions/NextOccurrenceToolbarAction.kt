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
package com.android.tools.idea.logcat.actions

import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.actions.NextOccurenceAction
import com.intellij.openapi.actionSystem.CustomShortcutSet.EMPTY
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions.ACTION_NEXT_OCCURENCE
import com.intellij.openapi.actionSystem.ex.ActionUtil

/**
 * A replacement for CommonActionsManager.createNextOccurenceAction() that doesn't have a keyboard
 * shortcut.
 *
 * TODO(aalbert): Remove and use CommonActionsManager.createNextOccurenceAction() when we figure out
 *   how to properly register the shortcut. I tried to register with
 *   action.registerCustomShortcutSet(action.shortcutSet, editor.contentComponent) But that works
 *   just one time and stops because the focus moves to the file editor and the shortcut is only
 *   registered on the Logcat editor.
 */
internal class NextOccurrenceToolbarAction(private val navigator: OccurenceNavigator) :
  NextOccurenceAction() {
  init {
    ActionUtil.copyFrom(this, ACTION_NEXT_OCCURENCE)
    shortcutSet = EMPTY
  }

  override fun getNavigator(dataContext: DataContext): OccurenceNavigator = navigator
}
