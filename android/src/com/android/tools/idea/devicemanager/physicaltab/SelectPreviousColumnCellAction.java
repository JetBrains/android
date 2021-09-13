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

import com.android.tools.idea.devicemanager.Tables;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.Optional;
import javax.swing.AbstractAction;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;

final class SelectPreviousColumnCellAction extends AbstractAction {
  @Override
  public void actionPerformed(@NotNull ActionEvent event) {
    PhysicalDeviceTable table = (PhysicalDeviceTable)event.getSource();
    ListSelectionModel model = table.getColumnModel().getSelectionModel();
    int viewColumnIndex = model.getLeadSelectionIndex();
    int previousViewColumnIndex = viewColumnIndex - 1;

    if (table.isActionsColumn(viewColumnIndex)) {
      Optional<Component> component = table.getActionsCellEditor().getComponent().getFirstEnabledComponentBeforeFocusOwner();

      if (component.isPresent()) {
        component.get().requestFocusInWindow();
      }
      else {
        table.removeEditor();

        if (previousViewColumnIndex >= 0) {
          model.setLeadSelectionIndex(previousViewColumnIndex);
        }
      }
    }
    else if (previousViewColumnIndex >= 0) {
      model.setLeadSelectionIndex(previousViewColumnIndex);
    }
    else {
      Tables.selectPreviousRow(table);

      int lastViewColumnIndex = table.getColumnCount() - 1;
      model.setLeadSelectionIndex(lastViewColumnIndex);

      if (table.isActionsColumn(lastViewColumnIndex)) {
        table.editCellAt(table.getSelectedRow(), lastViewColumnIndex);

        ActionsComponent component = table.getActionsCellEditor().getComponent();
        component.getLastEnabledComponent(component.getComponentCount() - 1).ifPresent(Component::requestFocusInWindow);
      }
    }
  }
}
