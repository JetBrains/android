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

import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.res.StringResourceWriter
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.TestOnly

/** Action to remove string resource keys. */
class RemoveKeysAction
@TestOnly
internal constructor(private val stringResourceWriter: StringResourceWriter) :
    PanelAction(text = "Remove Keys", description = null, icon = AllIcons.General.Remove) {

  constructor() : this(StringResourceWriter.INSTANCE)

  override fun doUpdate(event: AnActionEvent) = event.panel.table.hasSelectedCell()

  override fun actionPerformed(event: AnActionEvent) {
    val table: StringResourceTable = event.panel.table
    val model = table.model
    val repository = model.repository
    val index = table.selectedModelRowIndex
    if (index >= 0) {
      val items = repository.getItems(model.getKey(index))
      stringResourceWriter.safeDelete(event.requiredProject, items, event.panel::reloadData)
    }
  }
}
