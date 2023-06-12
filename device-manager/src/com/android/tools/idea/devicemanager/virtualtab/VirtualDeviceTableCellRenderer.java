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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.devicemanager.DeviceTableCellRenderer;
import com.android.tools.idea.wearpairing.WearPairingManager;
import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceTableCellRenderer extends DeviceTableCellRenderer<VirtualDevice> {
  VirtualDeviceTableCellRenderer(@NotNull WearPairingManager manager) {
    super(VirtualDevice.class, manager);
  }

  @Nullable
  @Override
  protected Icon getStateIcon(@NotNull VirtualDevice device) {
    if (!device.getAvdInfo().getStatus().equals(AvdStatus.OK)) {
      return StudioIcons.Common.WARNING_INLINE;
    }

    return super.getStateIcon(device);
  }

  @NotNull
  @Override
  protected String getLine2(@NotNull VirtualDevice device) {
    AvdInfo avd = device.getAvdInfo();

    if (!avd.getStatus().equals(AvdStatus.OK)) {
      String message = avd.getErrorMessage();

      assert message != null;
      return message;
    }

    return device.getTarget() + " | " + device.getCpuArchitecture();
  }
}
