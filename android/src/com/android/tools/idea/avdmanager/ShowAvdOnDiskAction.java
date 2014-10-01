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
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

/**
 * Show the contents of the AVD on disk
 */
public class ShowAvdOnDiskAction extends AvdUiAction {
  private static final Logger LOG = Logger.getInstance(ShowAvdOnDiskAction.class);

  public ShowAvdOnDiskAction(AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "Show on Disk", "Open the location of this AVD's data files", AllIcons.Actions.Menu_open);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdInfo info = getAvdInfo();
    if (info == null) {
      return;
    }
    File dataFolder = new File(info.getDataFolderPath());
    ShowFilePathAction.openDirectory(dataFolder);
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
