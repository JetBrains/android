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
package com.android.tools.idea.run.deployment.selector

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.Objects

/**
 * An item in the [submenu][SnapshotActionGroup] for a virtual device. The [target][Target]
 * determines if an available virtual device will be cold booted, quick booted, or booted with a
 * snapshot.
 */
internal class SelectTargetAction(
  private val target: Target,
  private val device: Device,
  private val comboBoxAction: DeviceAndSnapshotComboBoxAction
) : AnAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.setText(target.getText(device), false)
  }

  override fun actionPerformed(event: AnActionEvent) {
    comboBoxAction.setTargetSelectedWithComboBox(requireNotNull(event.project), target)
  }

  override fun hashCode(): Int = Objects.hash(target, device, comboBoxAction)

  override fun equals(other: Any?): Boolean {
    return other is SelectTargetAction &&
      target == other.target &&
      device == other.device &&
      comboBoxAction == other.comboBoxAction
  }
}
