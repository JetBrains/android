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
package com.android.tools.idea.devicemanager;

import com.intellij.ui.table.JBTable;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;

public class DeviceTable<D> extends JBTable {
  private final @NotNull Class<@NotNull D> myDeviceClass;
  private final int myDeviceModelColumnIndex;

  protected DeviceTable(@NotNull TableModel model, @NotNull Class<@NotNull D> deviceClass, int deviceModelColumnIndex) {
    super(model);

    myDeviceClass = deviceClass;
    myDeviceModelColumnIndex = deviceModelColumnIndex;
  }

  public final @NotNull D getDeviceAt(int viewRowIndex) {
    return myDeviceClass.cast(getValueAt(viewRowIndex, deviceViewColumnIndex()));
  }

  public final int deviceViewColumnIndex() {
    return convertColumnIndexToView(myDeviceModelColumnIndex);
  }
}
