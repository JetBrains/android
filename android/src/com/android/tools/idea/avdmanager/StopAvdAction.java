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
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;

/**
 * Stop the emulator running an AVD
 */
public class StopAvdAction extends AvdUiAction {
  public StopAvdAction(@NotNull AvdInfoProvider provider) {
    super(provider, "Stop", "Stop the emulator running this AVD", AllIcons.Actions.Suspend);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdInfo avdInfo = getAvdInfo();
    if (avdInfo != null) {
      AvdManagerConnection.getDefaultAvdManagerConnection().stopAvd(avdInfo);
    }
  }

  @Override
  public boolean isEnabled() {
    AvdInfo avdInfo = getAvdInfo();
    return avdInfo != null && AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(avdInfo);
  }
}
