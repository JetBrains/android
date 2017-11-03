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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.editors.strings.table.LocaleColumnFilter;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableColumnFilter;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.JComponent;
import java.util.stream.IntStream;

class FilterLocalesAction extends ComboBoxAction {

  private static final String NO_FILTER_TITLE = "Show All Locales";

  private final StringResourceTable myTable;

  FilterLocalesAction(@NotNull StringResourceTable table) {
    myTable = table;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    StringResourceTableColumnFilter filter = myTable.getColumnFilter();

    if (filter == null) {
      Presentation presentation = event.getPresentation();
      presentation.setIcon(null);
      presentation.setText(NO_FILTER_TITLE);
    }
    else {
      filter.update(event.getPresentation());
    }
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@Nullable JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);

    group.add(new AnAction(NO_FILTER_TITLE) {
      @Override
      public void actionPerformed(@Nullable AnActionEvent event) {
        myTable.setColumnFilter(null);
      }
    });

    StringResourceTableModel model = myTable.getModel();

    IntStream.range(StringResourceTableModel.FIXED_COLUMN_COUNT, model.getColumnCount())
      .mapToObj(model::getLocale)
      .map(this::newShowLocaleAction)
      .forEach(group::add);

    return group;
  }

  @NotNull
  private AnAction newShowLocaleAction(@NotNull Locale locale) {
    String text = "Show " + LocaleMenuAction.getLocaleLabel(locale, false);

    return new AnAction(text, null, locale.getFlagImage()) {
      @Override
      public void actionPerformed(@Nullable AnActionEvent event) {
        myTable.setColumnFilter(new LocaleColumnFilter(locale));
      }
    };
  }
}
