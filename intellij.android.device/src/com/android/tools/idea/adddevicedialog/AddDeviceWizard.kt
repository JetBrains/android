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
package com.android.tools.idea.adddevicedialog

import androidx.compose.runtime.remember
import com.intellij.openapi.project.Project

internal class AddDeviceWizard(val sources: List<DeviceSource>, val project: Project?) {
  fun createDialog(): ComposeWizard {
    val profiles = sources.flatMap { it.profiles }
    return ComposeWizard(project, "Add Device") {
      nextActionName = "Configure"
      finishActionName = "Add"

      val selectionState = remember { TableSelectionState<DeviceProfile>() }
      DeviceTable(profiles, tableSelectionState = selectionState)

      val selection = selectionState.selection
      val source = sources.find { it.javaClass == selection?.source }

      if (selection == null || source == null) {
        nextAction = WizardAction.Disabled
        finishAction = WizardAction.Disabled
      } else {
        source.apply { selectionUpdated(selection) }
      }
    }
  }
}
