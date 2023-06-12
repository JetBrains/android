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

import com.android.tools.idea.devicemanager.IconButtonTableCellRenderer;
import com.android.tools.idea.devicemanager.IconTableCell;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

final class VirtualDeviceIconButtonTableCellRenderer extends IconButtonTableCellRenderer {
  @NotNull
  @Override
  public Component getTableCellRendererComponent(@NotNull JTable table,
                                                 @NotNull Object value,
                                                 boolean selected,
                                                 boolean focused,
                                                 int viewRowIndex,
                                                 int viewColumnIndex) {
    myButton.setDefaultIcon(((VirtualDeviceTable)table).getDeviceAt(viewRowIndex).getIcon());
    return super.getTableCellRendererComponent(table, value, selected, focused, viewRowIndex, viewColumnIndex);
  }

  @NotNull
  @VisibleForTesting
  IconTableCell getButton() {
    return myButton;
  }
}
