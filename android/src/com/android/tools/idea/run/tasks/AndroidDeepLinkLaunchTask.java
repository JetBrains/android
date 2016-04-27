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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.stats.UsageTracker;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class AndroidDeepLinkLaunchTask implements LaunchTask {
  @NotNull private final String myDeepLink;
  @NotNull StartActivityFlagsProvider myStartActivityFlagsProvider;

  public AndroidDeepLinkLaunchTask(@NotNull String deepLink,
                                   @NotNull StartActivityFlagsProvider startActivityFlagsProvider) {
    myDeepLink = deepLink;
    myStartActivityFlagsProvider = startActivityFlagsProvider;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Launching URL";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.LAUNCH_ACTIVITY;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    printer.stdout("Launching deeplink: " + myDeepLink + ".\n");
    UsageTracker.getInstance()
      .trackEvent(UsageTracker.CATEGORY_APP_INDEXING, UsageTracker.ACTION_APP_INDEXING_DEEP_LINK_LAUNCHED, null, null);

    // Enable AppIndexing API log
    ShellCommandLauncher.execute("setprop log.tag.AppIndexApi VERBOSE", device, launchStatus, printer, 5, TimeUnit.SECONDS);

    // Launch deeplink
    String command = getLaunchDeepLinkCommand(myDeepLink, myStartActivityFlagsProvider.getFlags(device));
    return ShellCommandLauncher.execute(command, device, launchStatus, printer, 5, TimeUnit.SECONDS);
  }

  @NotNull
  public static String getLaunchDeepLinkCommand(@NotNull String deepLink,
                                                @NotNull String extraFlags) {
    return "am start" +
           " -a android.intent.action.VIEW" +
           " -c android.intent.category.BROWSABLE" +
           " -d " + deepLink +
           (extraFlags.isEmpty() ? "" : " " + extraFlags);
  }
}
