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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.LogcatBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction

/** An action that opens a popup menu with Logcat format-related actions */
internal class LogcatSplitterActions(splitterActions: ActionGroup) :
  PopupActionGroupAction(
    LogcatBundle.message("logcat.splitter.actions.text"),
    null,
    AllIcons.Actions.SplitVertically,
  ) {

  private val splitterActions =
    splitterActions.getChildren(null, ActionManager.getInstance()).asList()

  override fun getPopupActions(): List<AnAction> = splitterActions
}
