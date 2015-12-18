/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.stats.UsageTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

public class AndroidDeepLinkLauncher extends  AndroidApplicationLauncher {
  private final String APP_INDEXING_LOG_COMMEND = "setprop log.tag.AppIndexApi VERBOSE"; // Command to enable AppIndexing API log.
  @NotNull private final String myDeepLink;
  @NotNull private final String myExtraFlags;

  public AndroidDeepLinkLauncher(@NotNull String deepLink, @NotNull String extraFlags) {
    myDeepLink = deepLink;
    myExtraFlags = extraFlags;
  }

  @Override
  public LaunchResult launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
    throws IOException, AdbCommandRejectedException, TimeoutException {
    if (state.isStopped()) return LaunchResult.STOP;
    state.getProcessHandler().notifyTextAvailable("Launching application: " + myDeepLink + ".\n", STDOUT);
    UsageTracker.getInstance()
      .trackEvent(UsageTracker.CATEGORY_APP_INDEXING, UsageTracker.ACTION_APP_INDEXING_DEEP_LINK_LAUNCHED, null, null);

    LaunchResult result = executeCommand(APP_INDEXING_LOG_COMMEND, state, device);
    if (result == LaunchResult.STOP) {
      return LaunchResult.STOP;
    }
    String launchDeepLinkCommand = getLaunchDeepLinkCommand(myDeepLink, state.getPackageName(), getDebugFlags(state), myExtraFlags);
    return executeCommand(launchDeepLinkCommand, state, device);
  }

  @VisibleForTesting
  @NotNull
  static String getLaunchDeepLinkCommand(
    @NotNull String deepLink, @Nullable String packageId, @NotNull String debugFlags, @NotNull String extraFlags) {
    return "am start " +
           debugFlags + " " +
           "-a android.intent.action.VIEW " +
           "-c android.intent.category.BROWSABLE " +
           "-d " + deepLink +
           (packageId == null ? "" : " " + packageId) +
           (extraFlags.isEmpty() ? "" : " " + extraFlags);
  }
}
