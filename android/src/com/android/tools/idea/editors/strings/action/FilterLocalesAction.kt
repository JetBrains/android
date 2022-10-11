/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ide.common.resources.Locale
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.editors.strings.table.filter.LocaleColumnFilter
import com.android.tools.idea.editors.strings.table.filter.StringResourceTableColumnFilter
import com.android.tools.idea.rendering.FlagManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import javax.swing.Icon
import javax.swing.JComponent

/** Action to allow the user to filter [Locale]s in the Translations Editor. */
class FilterLocalesAction : ComboBoxAction() {

  override fun update(event: AnActionEvent) {
    val editor = event.getData(PlatformDataKeys.FILE_EDITOR) as? StringResourceEditor ?: return
    val filter = editor.panel.table.columnFilter
    event.presentation.icon = filter?.getIcon()
    event.presentation.text = filter?.getDescription() ?: NO_FILTER_TITLE
  }

  override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
    val group = DefaultActionGroup.createPopupGroupWithEmptyText()

    group.add(columnFilterUpdatingAction(NO_FILTER_TITLE) { null })

    val editor = dataContext.getData(PlatformDataKeys.FILE_EDITOR)
    if (editor is StringResourceEditor) {
      val model = editor.panel.table.model
      StringResourceTableModel.FIXED_COLUMN_COUNT.until(model.columnCount)
        .mapNotNull(model::getLocale)
        .map(::newShowLocaleAction)
        .forEach(group::add)
    }
    return group
  }

  companion object {
    private const val NO_FILTER_TITLE = "Show All Locales"
    /** Returns a [PanelAction] that sets the column filter to filter for the given [locale]. */
    private fun newShowLocaleAction(locale: Locale): PanelAction {
      val text = "Show ${Locale.getLocaleLabel(locale, /* brief= */ false)}"
      return columnFilterUpdatingAction(text, icon = FlagManager.getFlagImage(locale)) { LocaleColumnFilter(locale) }
    }

    /** Returns a [PanelAction] that updates the column filter on the table using the result of the [columnFilterSupplier]. */
    private fun columnFilterUpdatingAction(
      text: String,
      description: String? = null,
      icon: Icon? = null,
      columnFilterSupplier: () -> StringResourceTableColumnFilter?
    ) =
      object : PanelAction(text, description, icon) {
        override fun doUpdate(event: AnActionEvent): Boolean = true
        override fun actionPerformed(event: AnActionEvent) {
          event.panel.table.columnFilter = columnFilterSupplier.invoke()
        }
      }
  }
}
