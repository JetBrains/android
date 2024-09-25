/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.run.binary.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.configuration.execution.ExecutionUtils;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

/** Provides the launch task for android_binary */
public class AndroidDeepLinkLaunchTask {

  private static final String ID = "LAUNCH_DEEP_LINK";

  @NotNull private final String myDeepLink;
  @NotNull StartActivityFlagsProvider myStartActivityFlagsProvider;

  public AndroidDeepLinkLaunchTask(
      @NotNull String deepLink, @NotNull StartActivityFlagsProvider startActivityFlagsProvider) {
    myDeepLink = deepLink;
    myStartActivityFlagsProvider = startActivityFlagsProvider;
  }

  public void run(IDevice device, ProgressIndicator progressIndicator, ConsoleView consoleView)
      throws ExecutionException {

    final String text = "Launching deeplink: " + myDeepLink + ".\n";
    ExecutionUtils.println(consoleView, "Launching deeplink: " + myDeepLink + ".\n");
    Logger.getInstance(this.getClass()).info(text);

    UsageTracker.log(
        AndroidStudioEvent.newBuilder()
            .setCategory(EventCategory.APP_INDEXING)
            .setKind(EventKind.APP_INDEXING_DEEP_LINK_LAUNCHED));
    // Enable AppIndexing API log
    ExecutionUtils.executeShellCommand(
        device, "setprop log.tag.AppIndexApi VERBOSE", consoleView, progressIndicator);

    // Launch deeplink
    String command =
        getLaunchDeepLinkCommand(myDeepLink, myStartActivityFlagsProvider.getFlags(device));
    ExecutionUtils.executeShellCommand(device, command, consoleView, progressIndicator);
  }

  @NotNull
  public static String getLaunchDeepLinkCommand(
      @NotNull String deepLink, @NotNull String extraFlags) {
    return "am start"
        + " -a android.intent.action.VIEW"
        + " -c android.intent.category.BROWSABLE"
        + " -d "
        + singleQuoteShell(deepLink)
        + (extraFlags.isEmpty() ? "" : " " + extraFlags);
  }

  @NotNull
  private static String singleQuoteShell(@NotNull String literal) {
    return "'" + literal.replace("'", "'\\''") + "'";
  }
}
