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
package com.android.tools.idea.editors.strings.action

import com.android.tools.idea.editors.strings.StringResourceData
import com.android.tools.idea.res.StringResourceWriter
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.TestOnly

/** Action to add a new string resource key. */
class AddKeyAction
@TestOnly
internal constructor(private val stringResourceWriter: StringResourceWriter) :
    PanelAction("Add Key", description = null, AllIcons.General.Add) {
  constructor() : this(StringResourceWriter.INSTANCE)
  override fun doUpdate(event: AnActionEvent): Boolean = event.panel.table.data != null

  override fun actionPerformed(event: AnActionEvent) {
    val data: StringResourceData =
        requireNotNull(event.panel.table.data) {
          "Panel's StringResourceTable must contain non-null StringResourceData!"
        }
    val dialog = NewStringKeyDialog(event.panel.facet, data.keys.toSet())
    if (dialog.showAndGet() &&
        stringResourceWriter.addDefault(event.requiredProject, dialog.key, dialog.defaultValue)) {
      event.panel.reloadData()
    }
  }
}
