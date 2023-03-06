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
package com.android.tools.idea.ui.resourcemanager.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnActionButton
import org.jetbrains.android.util.AndroidBundle.message


/**
 * Expand/collapse action.
 */
 open class ExpandAction :
  AnActionButton(message("resource.manager.collapse.section"), AllIcons.Ide.Notification.Expand) {

  open var expanded = true
  override fun actionPerformed(e: AnActionEvent) {
    expanded = !expanded
  }

  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    e.presentation.isEnabled = true
    e.presentation.apply {
      if (expanded) {
        icon = AllIcons.Ide.Notification.Expand
        text = message("resource.manager.collapse.section")
      }
      else {
        icon = AllIcons.Ide.Notification.CollapseHover
        text = message("resource.manager.expand.section")
      }
    }
  }
}