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
package com.android.tools.idea.deviceManager.avdmanager.emulator

import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel

/**
 * This [EditorNotificationPanel] will display problems described by a [AccelerationErrorCode].
 * Some of these problems have an action the user can invoke to fix the problem, and other errors just
 * have text for the solution (solution == NONE).
 *
 * We show the [AccelerationErrorCode.solutionMessage] as tooltip if there is an action for
 * fixing the problem, and as a popup dialog if (solution == NONE).
 */
class AccelerationErrorNotificationPanel(error: AccelerationErrorCode, project: Project?, refresh: Runnable?) : EditorNotificationPanel() {
  init {
    text = error.problem
    val action = AccelerationErrorSolution.getActionForFix(error, project, refresh, null)
    val link = createActionLabel(error.solution.description, action)
    link.toolTipText = error.solutionMessage.takeUnless { error.solution == AccelerationErrorSolution.SolutionCode.NONE }
  }
}