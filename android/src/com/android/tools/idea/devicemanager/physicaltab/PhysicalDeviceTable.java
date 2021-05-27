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
import com.android.tools.idea.devicemanager.DeviceTableCellRenderer;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceTable extends JBTable {
  PhysicalDeviceTable(@Nullable Project project) {
    super(new PhysicalDeviceTableModel());

    if (project != null) {
      setDefaultEditor(Actions.class, new ActionsTableCellEditor(project));
    }

    setDefaultRenderer(Device.class, new DeviceTableCellRenderer<>(Device.class));
    setDefaultRenderer(Actions.class, new ActionsTableCellRenderer());

    getEmptyText().setText("No physical devices added. Connect a device via USB cable.");
  }

  @VisibleForTesting
  @NotNull Object getData() {
    return IntStream.range(0, getRowCount())
      .mapToObj(this::getRowAt)
      .collect(Collectors.toList());
  }

  @VisibleForTesting
  private @NotNull Object getRowAt(int viewRowIndex) {
    return IntStream.range(0, getColumnCount())
      .mapToObj(viewColumnIndex -> getValueAt(viewRowIndex, viewColumnIndex))
      .collect(Collectors.toList());
  }
}
