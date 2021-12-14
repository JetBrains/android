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

import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.IconButtonTableCellEditor;
import com.android.tools.idea.devicemanager.legacy.LegacyAvdManagerUtils;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.LaunchInEmulatorValue;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.EdtExecutorService;
import icons.StudioIcons;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class LaunchInEmulatorButtonTableCellEditor extends IconButtonTableCellEditor {
  private VirtualDevice myDevice;

  LaunchInEmulatorButtonTableCellEditor(@Nullable Project project) {
    super(StudioIcons.Avd.RUN, LaunchInEmulatorValue.INSTANCE, "Launch this AVD in the emulator");

    myButton.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.VIRTUAL_LAUNCH_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      ListenableFuture<IDevice> future = AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(project, myDevice.getAvdInfo());
      Futures.addCallback(future, LegacyAvdManagerUtils.newCallback(project), EdtExecutorService.getInstance());
    });
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);

    myDevice = ((VirtualDeviceTable)table).getDeviceAt(viewRowIndex);
    myButton.setEnabled(myDevice.getAvdInfo().getStatus().equals(AvdStatus.OK));

    return myButton;
  }
}
