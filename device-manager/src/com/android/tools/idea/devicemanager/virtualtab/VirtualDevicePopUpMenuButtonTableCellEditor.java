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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.avdmanager.AvdWizardUtils;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.PopUpMenuButtonTableCellEditor;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.openapi.ui.JBMenuItem;
import java.awt.Component;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class VirtualDevicePopUpMenuButtonTableCellEditor extends PopUpMenuButtonTableCellEditor {
  private final @NotNull VirtualDevicePanel myPanel;
  private VirtualDevice myDevice;

  VirtualDevicePopUpMenuButtonTableCellEditor(@NotNull VirtualDevicePanel panel) {
    myPanel = panel;
  }

  @Override
  public @NotNull List<@NotNull JComponent> newItems() {
    return Collections.singletonList(newDuplicateItem());
  }

  private @NotNull JComponent newDuplicateItem() {
    AbstractButton item = new JBMenuItem("Duplicate");
    item.setToolTipText("Duplicate this AVD");

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_DUPLICATE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
      VirtualDeviceTable table = myPanel.getTable();

      if (AvdWizardUtils.createAvdWizardForDuplication(table, myPanel.getProject(), myDevice.getAvdInfo()).showAndGet()) {
        table.refreshAvds();
      }
    });

    return item;
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);
    myDevice = ((VirtualDeviceTable)table).getDeviceAt(viewRowIndex);

    return myButton;
  }
}
