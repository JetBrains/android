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
package com.android.tools.idea.profilers

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.JComponent

object ProfilerBuildAndLaunch {
  @JvmStatic
  fun buildAndLaunchAction(profileableMode: Boolean, component: JComponent) {
    val action = if (profileableMode) ProfileProfileableAction() else ProfileDebuggableAction()
    doBuildAndLaunchAction(action, component)
  }

  private fun doBuildAndLaunchAction(action: AnAction, component: JComponent) {
    val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(component))
    action.actionPerformed(event)
  }
}