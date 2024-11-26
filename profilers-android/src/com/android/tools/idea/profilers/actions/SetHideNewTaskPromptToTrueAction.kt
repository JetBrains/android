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
package com.android.tools.idea.profilers.actions

import com.android.tools.profilers.taskbased.TaskEntranceTabModel.Companion.HIDE_NEW_TASK_PROMPT
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Test only action, which sets HIDE_NEW_TASK_PROMPT = true.
 * Which prevents the dialog prompt from appearing when starting/importing new tasks,
 * allowing the test to proceed without closing the task tab.
 */
class SetHideNewTaskPromptToTrueAction : ProfilerTaskActionBase() {
  @Suppress("VisibleForTests")
  override fun actionPerformed(e: AnActionEvent) {
    getStudioProfilers(e.project!!).ideServices.persistentProfilerPreferences.setBoolean(HIDE_NEW_TASK_PROMPT, true)
  }
}