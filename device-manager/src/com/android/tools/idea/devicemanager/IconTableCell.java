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

import java.awt.Color;
import java.awt.Component;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IconTableCell {
  default @NotNull Component getTableCellComponent(@NotNull JTable table, boolean selected, boolean focused) {
    setBackground(Tables.getBackground(table, selected));
    setBorder(Tables.getBorder(selected, focused));

    Icon icon = getDefaultIcon()
      .map(i -> Tables.getIcon(table, selected, i))
      .orElse(null);

    setIcon(icon);
    return (Component)this;
  }

  @NotNull Optional<Icon> getDefaultIcon();

  void setBackground(@Nullable Color background);

  void setBorder(@Nullable Border border);

  void setIcon(@Nullable Icon icon);
}
