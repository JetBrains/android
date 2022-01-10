/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab.columns;

import com.android.annotations.Nullable;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.legacy.AvdUiAction;
import com.android.tools.idea.explorer.DeviceExplorer;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import java.awt.event.ActionEvent;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class ExploreAvdAction extends AvdUiAction {
  private final @NotNull AvdInfoProvider myAvdInfoProvider;
  private final boolean myLogDeviceManagerEvents;

  public ExploreAvdAction(@NotNull AvdInfoProvider provider, boolean logDeviceManagerEvents) {
    super(provider, "Explore device filesystem...", "Open Device File Explorer for this device", AllIcons.Actions.MenuOpen);

    myAvdInfoProvider = provider;
    myLogDeviceManagerEvents = logDeviceManagerEvents;
  }

  @Override
  public void actionPerformed(@Nullable ActionEvent event) {
    if (myLogDeviceManagerEvents) {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_DEVICE_FILE_EXPLORER_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
    }

    Project project = myAvdInfoProvider.getProject();
    if (project == null) {
      return;
    }

    AvdInfo avdInfo = Objects.requireNonNull(myAvdInfoProvider.getAvdInfo());
    if (AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(avdInfo)) {
      DeviceExplorer.openAndShowDevice(project, avdInfo);
    }
    else {
      DeviceExplorer.showToolWindow(project);
    }
  }

  @Override
  public boolean isEnabled() {
    return true; // TODO(b/200132812): always return true for now so action works, but will be redone later
  }
}
