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
package com.android.tools.idea.deviceManager.actions;

import com.android.annotations.Nullable;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.AvdUiAction;
import com.android.tools.idea.explorer.DeviceExplorerToolWindowFactory;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import java.awt.event.ActionEvent;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class ExploreAvdAction extends AvdUiAction {
  private final @NotNull AvdInfoProvider myAvdInfoProvider;

  public ExploreAvdAction(@NotNull AvdInfoProvider provider) {
    super(provider, "Explore device filesystem...",
          "Open Device File Explorer for this device", AllIcons.General.OpenDiskHover);
    myAvdInfoProvider = provider;
  }

  @Override
  public void actionPerformed(@Nullable ActionEvent event) {
    Project project = myAvdInfoProvider.getProject();
    if (project == null) {
      return;
    }

    DeviceExplorerToolWindowFactory.openAndShowDevice(project, Objects.requireNonNull(myAvdInfoProvider.getAvdInfo()));
  }

  @Override
  public boolean isEnabled() {
    // TODO: button should be grayed out when not enabled
    AvdInfo avdInfo = myAvdInfoProvider.getAvdInfo();
    assert avdInfo != null;
    return AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(avdInfo);
  }
}
