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
package com.android.tools.idea.devicemanager;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

public final class TestTables {
  private TestTables() {
  }

  public static @NotNull Object getData(@NotNull JTable table) {
    return IntStream.range(0, table.getRowCount())
      .mapToObj(viewRowIndex -> getRowAt(table, viewRowIndex))
      .collect(Collectors.toList());
  }

  private static @NotNull Object getRowAt(@NotNull JTable table, int viewRowIndex) {
    return IntStream.range(0, table.getColumnCount())
      .mapToObj(viewColumnIndex -> table.getValueAt(viewRowIndex, viewColumnIndex))
      .collect(Collectors.toList());
  }
}
