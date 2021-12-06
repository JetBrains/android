/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.devicemanager.Tables;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.jetbrains.annotations.NotNull;

final class SelectNextRowAction extends AbstractAction {
  @Override
  public void actionPerformed(@NotNull ActionEvent event) {
    VirtualDeviceTable table = (VirtualDeviceTable)event.getSource();
    int viewColumnIndex = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();

    if (table.isActionsColumn(viewColumnIndex)) {
      VirtualDevice device = table.getSelectedDevice().orElseThrow(AssertionError::new);
      int focusedComponent = ((ActionsTableCell)table.getCellEditor()).getComponent(device).getFocusedComponent();
      table.removeEditor();

      Tables.selectNextRow(table);

      table.editCellAt(table.getSelectedRow(), viewColumnIndex);
      device = table.getSelectedDevice().orElseThrow(AssertionError::new);
      ((ActionsTableCell)table.getCellEditor()).getComponent(device).setFocusedComponent(focusedComponent);
    }
    else {
      Tables.selectNextRow(table);
    }
  }
}
