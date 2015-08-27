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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class WipeAvdDataAction extends AvdUiAction {
  private static final Logger LOG = Logger.getInstance(RunAvdAction.class);

  public WipeAvdDataAction(AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "Wipe Data", "Wipe the user data of this AVD", AllIcons.Modules.Edit);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();
    AvdInfo avdInfo = getAvdInfo();
    if (avdInfo == null) {
      return;
    }
    if (connection.isAvdRunning(avdInfo)) {
      Messages.showErrorDialog(myAvdInfoProvider.getComponent(),
                               "The selected AVD is currently running in the Emulator. " +
                               "Please exit the emulator instance and try wiping again.", "Cannot Wipe A Running AVD");
      return;
    }
    int result = Messages.showYesNoDialog(myAvdInfoProvider.getComponent(),
                                          "Do you really want to wipe user files from AVD " + avdInfo.getName() + "?",
                                          "Confirm Data Wipe", AllIcons.General.QuestionDialog);
    if (result == Messages.YES) {
      connection.wipeUserData(avdInfo);
      refreshAvds();
    }
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
