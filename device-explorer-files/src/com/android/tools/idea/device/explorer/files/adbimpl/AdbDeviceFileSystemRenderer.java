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
package com.android.tools.idea.device.explorer.files.adbimpl;

import com.android.tools.idea.deviceprovisioner.DeviceHandleRenderer;
import com.android.tools.idea.deviceprovisioner.DeviceHandleRendererKt;
import com.android.tools.idea.file.explorer.toolwindow.adbimpl.AdbDeviceFileSystem;
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystemRenderer;
import com.google.common.collect.Iterables;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.jetbrains.annotations.NotNull;

public final class AdbDeviceFileSystemRenderer implements DeviceFileSystemRenderer<AdbDeviceFileSystem> {
  @NotNull private final DeviceNameRenderer myDeviceNameRenderer = new DeviceNameRenderer();

  @NotNull
  @Override
  public ListCellRenderer<AdbDeviceFileSystem> getDeviceNameListRenderer() {
    return myDeviceNameRenderer;
  }

  private static final class DeviceNameRenderer extends ColoredListCellRenderer<AdbDeviceFileSystem> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends AdbDeviceFileSystem> list,
                                         AdbDeviceFileSystem value,
                                         int index,
                                         boolean selected,
                                         boolean focused) {
      if (value == null) {
        append("No Connected Devices", SimpleTextAttributes.ERROR_ATTRIBUTES);
        return;
      }

      DeviceHandleRenderer.renderDevice(this, value.getDeviceHandle(),
                                        Iterables.transform(DeviceHandleRendererKt.toIterable(list.getModel()),
                                                            AdbDeviceFileSystem::getDeviceHandle));
    }
  }
}
