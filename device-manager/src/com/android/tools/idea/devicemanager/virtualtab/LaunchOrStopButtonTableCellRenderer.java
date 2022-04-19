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

import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.devicemanager.IconButtonTableCellRenderer;
import icons.StudioIcons;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class LaunchOrStopButtonTableCellRenderer extends IconButtonTableCellRenderer {
  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    VirtualDevice device = ((VirtualDeviceTable)table).getDeviceAt(viewRowIndex);

    if (device.isOnline()) {
      myButton.setDefaultIcon(StudioIcons.Avd.STOP);
      myButton.setEnabled(true);
      myButton.setToolTipText("Stop the emulator running this AVD");
    }
    else {
      myButton.setDefaultIcon(StudioIcons.Avd.RUN);
      myButton.setEnabled(device.getAvdInfo().getStatus().equals(AvdStatus.OK));
      myButton.setToolTipText("Launch this AVD in the emulator");
    }

    super.getTableCellRendererComponent(table, value, selected, focused, viewRowIndex, viewColumnIndex);
    return myButton;
  }
}
