/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab.columns;

import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class SizeOnDiskColumn extends ColumnInfo<AvdInfo, SizeOnDisk> {
  private final @NotNull Map<@NotNull AvdInfo, @NotNull SizeOnDisk> myDeviceToSizeOnDiskMap;
  private final @NotNull TableView<@NotNull AvdInfo> myTable;

  public SizeOnDiskColumn(@NotNull TableView<@NotNull AvdInfo> table) {
    super("Size on Disk");

    myDeviceToSizeOnDiskMap = new HashMap<>();
    myTable = table;
  }

  @Override
  public @NotNull SizeOnDisk valueOf(@NotNull AvdInfo device) {
    return getSizeOnDisk(device);
  }

  private @NotNull SizeOnDisk getSizeOnDisk(@NotNull AvdInfo device) {
    return myDeviceToSizeOnDiskMap.computeIfAbsent(device, d -> new SizeOnDisk(d, myTable));
  }

  @Override
  public @NotNull Class<?> getColumnClass() {
    return SizeOnDisk.class;
  }
}
