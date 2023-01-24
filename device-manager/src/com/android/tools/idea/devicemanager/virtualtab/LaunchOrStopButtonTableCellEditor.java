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

import com.android.tools.idea.devicemanager.IconButtonTableCellEditor;
import com.google.common.annotations.VisibleForTesting;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class LaunchOrStopButtonTableCellEditor extends IconButtonTableCellEditor {
  private VirtualDevice myDevice;

  LaunchOrStopButtonTableCellEditor() {
    myButton.addActionListener(event -> {
      if (myDevice.isOnline()) {
        myValue = VirtualDevice.State.STOPPING;
      }
      else {
        myValue = VirtualDevice.State.LAUNCHING;
      }

      fireEditingStopped();
    });
  }

  @NotNull
  @VisibleForTesting
  Object getDevice() {
    return myDevice;
  }

  @NotNull
  @Override
  public Component getTableCellEditorComponent(@NotNull JTable table,
                                               @NotNull Object value,
                                               boolean selected,
                                               int viewRowIndex,
                                               int viewColumnIndex) {
    myDevice = ((VirtualDeviceTable)table).getDeviceAt(viewRowIndex);
    VirtualDevice.State state = (VirtualDevice.State)value;

    myButton.setDefaultIcon(state.getIcon());
    myButton.setToolTipText(state.getTooltipText());

    return super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);
  }
}
