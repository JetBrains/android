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

import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.IconButtonTableCellEditor;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.RemoveValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import java.awt.Component;
import java.util.function.BiPredicate;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class RemoveButtonTableCellEditor extends IconButtonTableCellEditor {
  private Device myDevice;

  RemoveButtonTableCellEditor(@NotNull PhysicalDevicePanel panel) {
    this(panel, RemoveButtonTableCellEditor::askToRemove);
  }

  @VisibleForTesting
  RemoveButtonTableCellEditor(@NotNull PhysicalDevicePanel panel, @NotNull BiPredicate<@NotNull Object, @NotNull Project> askToRemove) {
    super(RemoveValue.INSTANCE, AllIcons.Actions.GC, "Remove this offline device from the list.");

    myButton.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.PHYSICAL_DELETE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      Project project = panel.getProject();
      assert project != null;

      if (!askToRemove.test(myDevice, project)) {
        fireEditingCanceled();
        return;
      }

      fireEditingStopped();
      panel.getTable().getModel().remove(myDevice.getKey());
    });
  }

  private static boolean askToRemove(@NotNull Object device, @NotNull Project project) {
    return MessageDialogBuilder.okCancel("Remove " + device + " Device", device + " will be removed from the device manager.")
      .yesText("Remove")
      .ask(project);
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);
    myDevice = ((PhysicalDeviceTable)table).getDeviceAt(viewRowIndex);

    return myButton;
  }
}
