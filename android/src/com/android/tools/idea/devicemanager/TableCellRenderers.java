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

import com.google.common.annotations.VisibleForTesting;
import java.awt.Color;
import java.util.function.Function;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

public final class TableCellRenderers {
  private TableCellRenderers() {
  }

  public static @NotNull Color getBackground(@NotNull JTable table, boolean selected) {
    if (selected) {
      return table.getSelectionBackground();
    }

    return table.getBackground();
  }

  public static @NotNull Border getBorder(boolean selected, boolean focused) {
    return getBorder(selected, focused, UIManager::getBorder);
  }

  @VisibleForTesting
  static @NotNull Border getBorder(boolean selected, boolean focused, @NotNull Function<@NotNull Object, @NotNull Border> getBorder) {
    if (!focused) {
      return getBorder.apply("Table.cellNoFocusBorder");
    }

    if (selected) {
      return getBorder.apply("Table.focusSelectedCellHighlightBorder");
    }

    return getBorder.apply("Table.focusCellHighlightBorder");
  }
}
