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

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceItem;
import com.android.tools.idea.editors.strings.table.FrozenColumnTableEvent;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.res.StringResourceWriter;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeleteStringAction extends AbstractAction {

  private final StringResourceViewPanel myPanel;

  public DeleteStringAction(@NotNull StringResourceViewPanel panel) {
    super("Delete");
    myPanel = panel;
  }

  public void update(@NotNull JMenuItem delete, @NotNull FrozenColumnTableEvent event) {
    StringResourceTable table = myPanel.getTable();
    int[] rows = table.getSelectedModelRowIndices();
    int[] cols = table.getSelectedModelColumnIndices();
    int tableRow = event.getViewRowIndex();
    int tableColumn = event.getViewColumnIndex();

    // nothing is selected, select cell under mouse
    if ((rows.length == 0 || cols.length == 0) && tableRow >= 0 && tableColumn >= 0) {
      table.selectCellAt(tableRow, tableColumn);
      rows = table.getSelectedModelRowIndices();
      cols = table.getSelectedModelColumnIndices();
    }

    for (int col : cols) {
      if (col == StringResourceTableModel.KEY_COLUMN ||
          col == StringResourceTableModel.RESOURCE_FOLDER_COLUMN ||
          col == StringResourceTableModel.UNTRANSLATABLE_COLUMN) {

        delete.setText("Delete Key(s)");
        delete.setVisible(true);
        return;
      }
    }

    StringResourceTableModel model = table.getModel();
    for (int row : rows) {
      for (int column : cols) {
        Locale locale = model.getLocale(column);
        StringResource resource = model.getStringResourceAt(row);

        ResourceItem item =
          locale == null ? resource.getDefaultValueAsResourceItem() : resource.getTranslationAsResourceItem(locale);
        if (item != null) {
          delete.setText("Delete String(s)");
          delete.setVisible(true);
          return;
        }
      }
    }

    delete.setVisible(false);
  }

  @Override
  public void actionPerformed(@Nullable ActionEvent event) {
    StringResourceTable table = myPanel.getTable();
    int[] cols = table.getSelectedModelColumnIndices();
    for (int col : cols) {
      if (col == StringResourceTableModel.KEY_COLUMN ||
          col == StringResourceTableModel.RESOURCE_FOLDER_COLUMN ||
          col == StringResourceTableModel.UNTRANSLATABLE_COLUMN) {
        // if it's not a translation we are deleting, then call the delete action for the whole string
        StringResourceTableModel model = table.getModel();
        List<ResourceItem> items = Arrays.stream(table.getSelectedModelRowIndices())
          .mapToObj(model::getKey)
          .flatMap(key -> model.getRepository().getItems(key).stream())
          .collect(Collectors.toUnmodifiableList());
        Project project = myPanel.getFacet().getModule().getProject();
        StringResourceWriter.INSTANCE.safeDelete(project, items, myPanel::reloadData);
        return;
      }
    }
    int[] rows = table.getSelectedModelRowIndices();
    if (rows.length == 1 && cols.length == 1) {
      table.getModel().setValueAt("", rows[0], cols[0]);
    }
    else {
      // remove all in a single action (so we can undo it in 1 go)
      WriteCommandAction.runWriteCommandAction(myPanel.getFacet().getModule().getProject(), "Delete multiple strings", null, () -> {
        for (int row : rows) {
          for (int col : cols) {
            table.getModel().setValueAt("", row, col);
          }
        }
      });
    }
  }
}
