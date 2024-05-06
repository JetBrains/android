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
    int modelRow = table.getSelectedModelRowIndex();
    int modelColumn = table.getSelectedModelColumnIndex();
    int viewRow = event.getViewRowIndex();
    int viewColumn = event.getViewColumnIndex();

    // nothing is selected, select cell under mouse
    if ((modelRow == -1 || modelColumn == -1) && viewRow >= 0 && viewColumn >= 0) {
      table.selectCellAt(viewRow, viewColumn);
      modelRow = table.getSelectedModelRowIndex();
      modelColumn = table.getSelectedModelColumnIndex();
    }

    if (!StringResourceTableModel.isStringValueColumn(modelColumn)) {
      delete.setText("Delete Key(s)");
      delete.setVisible(true);
      return;
    }

    StringResourceTableModel model = table.getModel();
    Locale locale = model.getLocale(modelColumn);
    StringResource resource = model.getStringResourceAt(modelRow);

    ResourceItem item =
      locale == null ? resource.getDefaultValueAsResourceItem() : resource.getTranslationAsResourceItem(locale);
    if (item != null) {
      delete.setText("Delete String(s)");
      delete.setVisible(true);
      return;
    }

    delete.setVisible(false);
  }

  @Override
  public void actionPerformed(@Nullable ActionEvent event) {
    StringResourceTable table = myPanel.getTable();
    int modelColumn = table.getSelectedModelColumnIndex();
    int modelRow = table.getSelectedModelRowIndex();
    if (modelColumn < 0 || modelRow < 0) {
      return;
    }

    if (!StringResourceTableModel.isStringValueColumn(modelColumn)) {
      // if it's not a translation we are deleting, then call the delete action for the whole string
      StringResourceTableModel model = table.getModel();
      List<ResourceItem> items = model.getRepository().getItems(model.getKey(modelRow));
      Project project = myPanel.getFacet().getModule().getProject();
      StringResourceWriter.INSTANCE.safeDelete(project, items, myPanel::reloadData);
      return;
    }

    table.getModel().setValueAt("", modelRow, modelColumn);
  }
}
