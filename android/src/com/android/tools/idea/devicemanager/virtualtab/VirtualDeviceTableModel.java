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

import com.android.annotations.concurrency.UiThread;
import com.android.sdklib.internal.avd.AvdInfo;
import java.util.Collections;
import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;

@UiThread
final class VirtualDeviceTableModel extends AbstractTableModel {
  static final int DEVICE_MODEL_COLUMN_INDEX = 0;
  private @NotNull List<@NotNull AvdInfo> myDevices = Collections.emptyList();

  @NotNull List<@NotNull AvdInfo> getDevices() {
    return myDevices;
  }

  void setDevices(@NotNull List<@NotNull AvdInfo> devices) {
    myDevices = devices;
    fireTableDataChanged();
  }

  @Override
  public int getRowCount() {
    return myDevices.size();
  }

  @Override
  public int getColumnCount() {
    return 4;
  }

  @Override
  public @NotNull Object getValueAt(int modelRowIndex, int modelColumnIndex) {
    // TODO
    throw new AssertionError(modelColumnIndex);
  }
}
