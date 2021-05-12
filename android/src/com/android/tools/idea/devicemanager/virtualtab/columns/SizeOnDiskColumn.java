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
import com.android.tools.idea.avdmanager.AvdDisplayList.AvdColumnInfo;
import com.intellij.ui.table.TableView;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class SizeOnDiskColumn extends AvdColumnInfo {
  @NotNull
  private final Map<AvdInfo, SizeOnDisk> myDeviceToSizeOnDiskMap;

  @NotNull
  private final TableView<AvdInfo> myTable;

  public SizeOnDiskColumn(@NotNull TableView<AvdInfo> table) {
    super("Size on disk");

    myDeviceToSizeOnDiskMap = new HashMap<>();
    myTable = table;
  }

  @NotNull
  @Override
  public String valueOf(@NotNull AvdInfo device) {
    return getSizeOnDisk(device).toString();
  }

  @NotNull
  @Override
  public Comparator<AvdInfo> getComparator() {
    return Comparator.comparing(this::getSizeOnDisk);
  }

  @NotNull
  private SizeOnDisk getSizeOnDisk(@NotNull AvdInfo device) {
    return myDeviceToSizeOnDiskMap.computeIfAbsent(device, d -> new SizeOnDisk(d, myTable));
  }
}
