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

import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.EdtExecutorService;
import icons.StudioIcons;
import java.awt.event.ActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Run an Android virtual device
 */
public class RunAvdAction extends AvdUiAction {
  public RunAvdAction(@NotNull AvdInfoProvider provider) {
    super(provider, "Run", "Launch this AVD in the emulator", StudioIcons.Avd.RUN);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdInfo avdInfo = getAvdInfo();
    if (avdInfo != null) {
      Project project = myAvdInfoProvider.getProject();

      ListenableFuture<IDevice> deviceFuture = AvdManagerConnection.getDefaultAvdManagerConnection().startAvd(project, avdInfo);
      Futures.addCallback(deviceFuture, AvdManagerUtils.newCallback(project), EdtExecutorService.getInstance());
    }
  }

  @Override
  public boolean isEnabled() {
    AvdInfo avdInfo = getAvdInfo();
    return avdInfo != null && avdInfo.getStatus() == AvdInfo.AvdStatus.OK;
  }
}
