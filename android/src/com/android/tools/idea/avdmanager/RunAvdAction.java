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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.welcome.install.Haxm;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;

/**
 * Run an Android virtual device
 */
public class RunAvdAction extends AvdUiAction {
  public RunAvdAction(@NotNull AvdInfoProvider provider) {
    super(provider, "Run", "Launch this AVD in the emulator", AllIcons.Actions.Execute);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdInfo avdInfo = getAvdInfo();
    if (avdInfo != null && checkReady()) {
      AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(myAvdInfoProvider.getProject(), avdInfo);
    }
  }

  @Override
  public boolean isEnabled() {
    AvdInfo avdInfo = getAvdInfo();
    return avdInfo != null && avdInfo.getStatus() == AvdInfo.AvdStatus.OK;
  }

  private boolean checkReady() {
    if (Haxm.canRun() && myAvdInfoProvider.getAvdInfo().getAbiType().contains("x86") &&
        HaxmAlert.getHaxmState(true) == HaxmAlert.HaxmState.NOT_INSTALLED) {
      int result = Messages.showOkCancelDialog(myAvdInfoProvider.getComponent(),
                                               "Intel HAXM is not installed, and is required to run this AVD.\n" +
                                               "Would you like to install it now?",
                                               "Install HAXM", AllIcons.General.WarningDialog);
      if (result == Messages.OK) {
        HaxmWizard wizard = new HaxmWizard();
        wizard.init();
        return wizard.showAndGet();
      }
      else {
        return false;
      }
    }
    return true;
  }
}
