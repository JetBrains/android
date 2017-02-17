/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.editors.strings.table.*;
import com.android.tools.idea.rendering.Locale;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.ui.DialogBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.stream.IntStream;

final class FilterKeysAction extends ComboBoxAction {
  private final StringResourceTable myTable;

  FilterKeysAction(@NotNull StringResourceTable table) {
    myTable = table;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    StringResourceTableRowFilter filter = myTable.getRowFilter();

    if (filter == null) {
      Presentation presentation = event.getPresentation();

      presentation.setIcon(null);
      presentation.setText("Show All Keys");
    }
    else {
      filter.update(event.getPresentation());
    }
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@Nullable JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);

    group.add(new AnAction("Show All Keys") {
      @Override
      public void actionPerformed(@Nullable AnActionEvent event) {
        myTable.setRowFilter(null);
      }
    });

    group.add(new AnAction("Show Translatable Keys") {
      @Override
      public void actionPerformed(@Nullable AnActionEvent event) {
        myTable.setRowFilter(new NeedsTranslationsRowFilter(true));
      }
    });

    group.add(new AnAction("Show Keys Needing Translations") {
      @Override
      public void actionPerformed(@Nullable AnActionEvent event) {
        myTable.setRowFilter(new NeedsTranslationsRowFilter(false));
      }
    });

    group.add(new AnAction("Filter by Text", "Filter the translations editor table keys by text", AllIcons.General.Filter) {
      @Override
      public void actionPerformed(@Nullable AnActionEvent e) {
        JTextField textField = new JTextField();
        DialogBuilder builder = new DialogBuilder();
        builder.setTitle("Filter by Text");
        builder.setCenterPanel(textField);
        builder.setPreferredFocusComponent(textField);
        if (builder.showAndGet()) {
          String filterString = textField.getText();
          if (!filterString.isEmpty()) {
            myTable.setRowFilter(new TextRowFilter(filterString));
          }
        }
      }
    });

    StringResourceTableModel model = myTable.getModel();

    IntStream.range(StringResourceTableModel.FIXED_COLUMN_COUNT, model.getColumnCount())
      .mapToObj(model::getLocale)
      .map(this::newShowKeysNeedingTranslationForLocaleAction)
      .forEach(group::add);

    return group;
  }

  @NotNull
  private AnAction newShowKeysNeedingTranslationForLocaleAction(@NotNull Locale locale) {
    String text = "Show Keys Needing a Translation for " + LocaleMenuAction.getLocaleLabel(locale, false);

    return new AnAction(text, null, locale.getFlagImage()) {
      @Override
      public void actionPerformed(@Nullable AnActionEvent event) {
        myTable.setRowFilter(new NeedsTranslationForLocaleRowFilter(locale));
      }
    };
  }
}
