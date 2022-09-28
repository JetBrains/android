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

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.IconButtonTableCellEditor;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import java.awt.Component;
import java.util.List;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DownloadButtonTableCellEditor extends IconButtonTableCellEditor {
  private VirtualDevice myDevice;
  private VirtualDeviceTable myTable;

  DownloadButtonTableCellEditor(@Nullable Project project) {
    super(DownloadValue.INSTANCE, AllIcons.Actions.Download, "Download system image");

    myButton.addActionListener(event -> {
      String path = AvdManagerConnection.getRequiredSystemImagePath(myDevice.getAvdInfo());
      assert path != null;

      DialogWrapper dialog = SdkQuickfixUtils.createDialogForPaths(project, List.of(path));

      if (dialog == null) {
        return;
      }

      dialog.show();

      // Multiple AVDs can have the same missing system image, so refresh all
      myTable.refreshAvds();

      fireEditingCanceled();
    });
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);

    myTable = (VirtualDeviceTable)table;
    myDevice = myTable.getDeviceAt(viewRowIndex);

    return myButton;
  }
}
