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

import com.android.tools.adtui.common.ColoredIconGenerator;
import javax.swing.Icon;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TableCellRenderers {
  private TableCellRenderers() {
  }

  static void setIcon(@NotNull JLabel label, @Nullable Icon icon, boolean selected) {
    if (icon == null) {
      label.setIcon(null);
    }
    else if (selected) {
      label.setIcon(ColoredIconGenerator.INSTANCE.generateColoredIcon(icon, label.getForeground()));
    }
    else {
      label.setIcon(icon);
    }
  }
}
