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
package com.android.tools.idea.devicemanager.physicaltab;

import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;

final class SelectNextColumnAction extends AbstractAction {
  @Override
  public void actionPerformed(@NotNull ActionEvent event) {
    PhysicalDeviceTable table = (PhysicalDeviceTable)event.getSource();
    ListSelectionModel model = table.getColumnModel().getSelectionModel();
    int viewColumnIndex = model.getLeadSelectionIndex();
    int nextViewColumnIndex = viewColumnIndex + 1;
    int viewColumnCount = table.getColumnCount();

    if (nextViewColumnIndex < viewColumnCount && table.isActionsColumn(nextViewColumnIndex)) {
      model.setLeadSelectionIndex(nextViewColumnIndex);

      table.editCellAt(table.getSelectedRow(), nextViewColumnIndex);
      table.getActionsCellEditor().getComponent().getFirstEnabledComponent(0).ifPresent(Component::requestFocusInWindow);
    }
    else if (table.isActionsColumn(viewColumnIndex)) {
      table.getActionsCellEditor().getComponent().getFirstEnabledComponentAfterFocusOwner().ifPresent(Component::requestFocusInWindow);
    }
    else if (nextViewColumnIndex < viewColumnCount) {
      model.setLeadSelectionIndex(nextViewColumnIndex);
    }
  }
}
