/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.avdmanager.EmulatorAdvFeatures;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;

final class Emulator {
  @NotNull ListenableFuture<@NotNull Boolean> supportsColdBootingAsync() {
    return DeviceManagerFutures.appExecutorServiceSubmit(Emulator::supportsColdBooting);
  }

  private static boolean supportsColdBooting() {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    ProgressIndicator indicator = new StudioLoggerProgressIndicator(Emulator.class);

    return EmulatorAdvFeatures.emulatorSupportsFastBoot(handler, indicator, new LogWrapper(Emulator.class));
  }
}
