/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.IDevice;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.avdmanager.EmulatorAdvFeatures;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.DeviceStateCache;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ScreenRecorderAction extends AbstractDeviceAction {
  // Only need to cache if device supports recording, so key can be empty string.
  private static final String PKG_NAME = "";

  private final Project myProject;
  private final DeviceStateCache<CompletableFuture> myCache;
  private boolean mUseEmuScreenRecording = false;

  public ScreenRecorderAction(@NotNull Project project, @NotNull DeviceContext context) {
    super(context,
          AndroidBundle.message("android.ddms.actions.screenrecord"),
          AndroidBundle.message("android.ddms.actions.screenrecord.description"),
          AndroidIcons.Ddms.ScreenRecorder);

    myProject = project;
    myCache = new DeviceStateCache<>(project);
  }

  @Override
  protected boolean isEnabled() {
    if (!super.isEnabled()) {
      return false;
    }

    IDevice device = myDeviceContext.getSelectedDevice();

    // Use emulator recording feature if it is supported.
    if (device.isEmulator()) {
      AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
      mUseEmuScreenRecording = EmulatorAdvFeatures.emulatorSupportsScreenRecording(
                                            handler,
                                            new StudioLoggerProgressIndicator(ScreenRecorderAction.class),
                                            new LogWrapper(Logger.getInstance(ScreenRecorderAction.class)));
      if (mUseEmuScreenRecording) {
        return true;
      }
    }

    CompletableFuture<Boolean> cf = myCache.get(device, PKG_NAME);
    // first time execution for this device, async query if device supports recording and save it in the cache.
    if (cf == null) {
      cf = CompletableFuture.supplyAsync(() -> device.supportsFeature(IDevice.Feature.SCREEN_RECORD));
      myCache.put(device, PKG_NAME, cf);
    }

    // default return false until future is complete since this method will be called each time studio udpates
    return cf.getNow(false);
  }

  @Override
  protected void performAction(@NotNull IDevice device) {
    new com.android.tools.idea.ddms.screenrecord.ScreenRecorderAction(myProject, device, mUseEmuScreenRecording).performAction();
  }
}
