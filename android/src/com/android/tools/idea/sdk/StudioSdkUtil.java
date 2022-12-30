/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.progress.StudioProgressRunner;

/**
 * Studio-specific utilities for interacting with the SDK.
 */
public final class StudioSdkUtil {
  /**
   * Convenience method to reload the local and remote SDK, showing a modal, non-cancellable progress window.
   */
  public static void reloadRemoteSdkWithModalProgress() {
    final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    StudioProgressRunner runner = new StudioProgressRunner(true, false, "Refreshing SDK", null);
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(StudioSdkUtil.class);
    RepoManager sdkManager = sdkHandler.getSdkManager(progress);
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, null, null, runner, new StudioDownloader(),
                    StudioSettingsController.getInstance());

  }
}
