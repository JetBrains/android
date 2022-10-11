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

import com.android.ide.common.resources.Locale
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.editors.strings.table.filter.NeedsTranslationForLocaleRowFilter
import com.android.tools.idea.editors.strings.table.filter.NeedsTranslationsRowFilter
import com.android.tools.idea.editors.strings.table.filter.StringResourceTableRowFilter
import com.android.tools.idea.editors.strings.table.filter.TextRowFilter
import com.android.tools.idea.editors.strings.table.filter.TranslatableRowFilter
import com.android.tools.idea.rendering.FlagManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.ui.DialogBuilder
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTextField

/** Action to allow the user to filter keys in the Translations Editor by various properties. */
class FilterKeysAction : ComboBoxAction() {
  override fun update(event: AnActionEvent) {
    val editor = event.getData(PlatformDataKeys.FILE_EDITOR) as? StringResourceEditor ?: return
    val filter = editor.panel.table.rowFilter
    event.presentation.icon = filter?.getIcon()
    event.presentation.text = filter?.getDescription() ?: NO_FILTER_TITLE
  }

  override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
    val group = DefaultActionGroup.createPopupGroupWithEmptyText()

    group.add(rowFilterUpdatingAction(NO_FILTER_TITLE) { null })

    group.add(rowFilterUpdatingAction("Show Translatable Keys") { TranslatableRowFilter() })

    group.add(rowFilterUpdatingAction("Show Keys Needing Translation") { NeedsTranslationsRowFilter() })

    group.add(
      object : PanelAction("Filter By Text", "Filter the translations editor table keys by text", AllIcons.General.Filter) {
          override fun doUpdate(event: AnActionEvent): Boolean = true

          override fun actionPerformed(event: AnActionEvent) {
            val textField = JTextField()
            val dialogBuilder =
                DialogBuilder().apply {
                  setTitle("Filter by Text")
                  setCenterPanel(textField)
                  setPreferredFocusComponent(textField)
                }
            if (dialogBuilder.showAndGet()) {
              val filterString = textField.text
              if (filterString.isNotEmpty()) {
                event.panel.table.rowFilter = TextRowFilter(filterString)
              }
            }
          }
        })

    val editor = dataContext.getData(PlatformDataKeys.FILE_EDITOR)
    if (editor is StringResourceEditor) {
      val model = editor.panel.table.model
      StringResourceTableModel.FIXED_COLUMN_COUNT.until(model.columnCount)
        .mapNotNull(model::getLocale)
        .map(::newShowKeysNeedingTranslationForLocaleAction)
        .forEach(group::add)
    }
    return group
  }

  companion object {
    private const val NO_FILTER_TITLE = "Show All Keys"
    /**
     * Returns a [PanelAction] that sets the row filter to filter for strings that need translation
     * to the specified [Locale].
     */
    private fun newShowKeysNeedingTranslationForLocaleAction(locale: Locale): PanelAction {
      val text = "Show Keys Needing a Translation for ${Locale.getLocaleLabel(locale, /* brief= */false)}"
      return rowFilterUpdatingAction(text, icon = FlagManager.getFlagImage(locale)) {
        NeedsTranslationForLocaleRowFilter(locale)
      }
    }

    /** Returns a [PanelAction] that updates the row filter on the table using the result of the [rowFilterSupplier]. */
    private fun rowFilterUpdatingAction(
        text: String,
        description: String? = null,
        icon: Icon? = null,
        rowFilterSupplier: () -> StringResourceTableRowFilter?
    ) =
        object : PanelAction(text, description, icon) {
          override fun doUpdate(event: AnActionEvent): Boolean = true
          override fun actionPerformed(event: AnActionEvent) {
            event.panel.table.rowFilter = rowFilterSupplier.invoke()
          }
        }
  }
}
