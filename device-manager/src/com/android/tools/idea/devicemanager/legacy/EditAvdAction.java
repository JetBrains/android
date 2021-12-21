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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.avdmanager.AvdWizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.icons.AllIcons;
import java.awt.event.ActionEvent;
import org.jetbrains.annotations.NotNull;

public class EditAvdAction extends AvdUiAction {
  private final boolean myLogDeviceManagerEvents;

  public EditAvdAction(@NotNull AvdInfoProvider avdInfoProvider, boolean logDeviceManagerEvents) {
    super(avdInfoProvider, "Edit", "Edit this AVD", AllIcons.Actions.Edit);
    myLogDeviceManagerEvents = logDeviceManagerEvents;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myLogDeviceManagerEvents) {
      DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.VIRTUAL_EDIT_ACTION)
        .build();

      AndroidStudioEvent.Builder builder = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DEVICE_MANAGER)
        .setDeviceManagerEvent(event);

      UsageTracker.log(builder);
    }

    ModelWizardDialog dialog = AvdWizardUtils.createAvdWizard(myAvdInfoProvider.getAvdProviderComponent(), getProject(), getAvdInfo());
    if (dialog.showAndGet()) {
      refreshAvds();
    }
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
