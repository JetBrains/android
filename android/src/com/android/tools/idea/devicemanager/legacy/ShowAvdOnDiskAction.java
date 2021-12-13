/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.legacy;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.analytics.UsageTracker;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RevealFileAction;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/**
 * Show the contents of the AVD on disk
 */
public class ShowAvdOnDiskAction extends AvdUiAction {
  private final boolean myLogDeviceManagerEvents;

  ShowAvdOnDiskAction(@NotNull AvdInfoProvider avdInfoProvider, boolean logDeviceManagerEvents) {
    super(avdInfoProvider, "Show on Disk", "Open the location of this AVD's data files", AllIcons.Actions.Menu_open);
    myLogDeviceManagerEvents = logDeviceManagerEvents;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myLogDeviceManagerEvents) {
      DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.VIRTUAL_SHOW_ON_DISK_ACTION)
        .build();

      AndroidStudioEvent.Builder builder = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DEVICE_MANAGER)
        .setDeviceManagerEvent(event);

      UsageTracker.log(builder);
    }

    AvdInfo info = getAvdInfo();
    if (info == null) {
      return;
    }
    Path dataFolder = info.getDataFolderPath();
    RevealFileAction.openDirectory(dataFolder);
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
