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

import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.run.ExternalToolRunner;
import com.android.tools.idea.stats.UsageTracker;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmulatorRunner extends ExternalToolRunner {
  public EmulatorRunner(@NotNull Project project,
                        @NotNull String consoleTitle,
                        @NotNull GeneralCommandLine commandLine,
                        @Nullable AvdInfo avdInfo) {
    super(project, consoleTitle, commandLine);

    ISystemImage image = avdInfo == null ? null : avdInfo.getSystemImage();

    String description = image == null ? null : image.toString();
    UsageTracker.getInstance().trackEvent(
      UsageTracker.CATEGORY_DEPLOYMENT, UsageTracker.ACTION_DEPLOYMENT_EMULATOR, description, null);

    if (avdInfo != null) {
      UsageTracker.getInstance().trackEvent(
        UsageTracker.CATEGORY_AVDINFO, UsageTracker.ACTION_AVDINFO_ABI, AvdInfo.getPrettyAbiType(avdInfo), null);

      String version = image == null ? "unknown" : image.getAndroidVersion().toString();
      UsageTracker.getInstance().trackEvent(
        UsageTracker.CATEGORY_AVDINFO, UsageTracker.ACTION_AVDINFO_TARGET_VERSION, version, null);
    }
  }

  @NotNull
  @Override
  protected ProcessHandler createProcessHandler(Process process, @NotNull GeneralCommandLine commandLine) {

    // Override the default process killing behavior:
    // The emulator process should not be killed forcibly since it would leave stale lock files around.
    // We want to preserve the behavior that once an emulator is launched, that process runs even if the IDE is closed
    return new EmulatorProcessHandler(process);
  }

  @Override
  protected void fillToolBarActions(DefaultActionGroup toolbarActions) {
    // override default implementation: we don't want to add a stop action since we can't just kill the emulator process
    // without leaving stale lock files around
  }

  @Override
  protected ConsoleView initConsoleUi() {
    ConsoleView consoleView = super.initConsoleUi();

    String avdHome = System.getenv("ANDROID_SDK_HOME");
    if (!StringUtil.isEmpty(avdHome)) {
      consoleView.print(
        "\n" +
        "Note: The environment variable $ANDROID_SDK_HOME is set, and the emulator uses that variable to locate AVDs.\n" +
        "This may result in the emulator failing to start if it cannot find the AVDs in the folder pointed to by the\n" +
        "given environment variable.\n" +
        "ANDROID_SDK_HOME=" + avdHome + "\n\n",
        ConsoleViewContentType.NORMAL_OUTPUT);
    }

    return consoleView;
  }

}
