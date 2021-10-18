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

import com.android.tools.idea.devicemanager.Table;
import com.intellij.ui.table.JBTable;
import java.awt.Point;
import org.jetbrains.annotations.NotNull;

final class VirtualDeviceTable extends JBTable implements Table {
  VirtualDeviceTable() {
    super(new VirtualDeviceTableModel());
  }

  @Override
  public @NotNull VirtualDeviceTableModel getModel() {
    return (VirtualDeviceTableModel)dataModel;
  }

  @Override
  public boolean isActionsColumn(int viewColumnIndex) {
    return false; //TODO
  }

  @Override
  public int viewRowIndexAtPoint(@NotNull Point point) {
    return rowAtPoint(point);
  }

  @Override
  public int viewColumnIndexAtPoint(@NotNull Point point) {
    return columnAtPoint(point);
  }

  @Override
  public int getEditingViewRowIndex() {
    return editingRow;
  }
}
