/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.IotInstallChecker;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.RetryingInstaller;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * App for Android Things have an IOT_LAUNCHER intent filter so that they
 * are launched on boot. We want to only have one such app installed at
 * all times. This task finds existing Things applications and proposes to
 * uninstall them before installing a new embedded application.
 */
public class UninstallIotLauncherAppsTask implements LaunchTask {
  private final String myPackageName;
  private final RetryingInstaller.Prompter myPrompter;
  private final IotInstallChecker myChecker;

  public UninstallIotLauncherAppsTask(Project project, String packageName) {
    this(packageName, new IotInstallChecker(), new RetryingInstaller.UserPrompter(project));
  }

  @VisibleForTesting
  UninstallIotLauncherAppsTask(String packageName, IotInstallChecker checker, RetryingInstaller.Prompter prompter) {
    myPackageName = packageName;
    myPrompter = prompter;
    myChecker = checker;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Uninstalling other IoT apps";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.UNINSTALL_IOT_APK;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    // Ignore the check if not running on an embedded device.
    if (!device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      return true;
    }

    // Check for IoT Launcher apps
    Set<String> installedIotLauncherApps = myChecker.getInstalledIotLauncherApps(device);
    installedIotLauncherApps.remove(myPackageName);
    if (!installedIotLauncherApps.isEmpty()) {
      String otherApplicationIds = StringUtil.join(installedIotLauncherApps, "\n");
      String reason = AndroidBundle.message("deployment.failed.uninstall.prompt.androidthings.text", otherApplicationIds);
      if (myPrompter.showQuestionPrompt(reason)) {
        Map<String, Throwable> failedUninstallApps = new TreeMap<>();
        for (String app : installedIotLauncherApps) {
          try {
            device.uninstallPackage(app);
          }
          catch (InstallException e) {
            failedUninstallApps.put(app, e);
          }
        }
        if (!failedUninstallApps.isEmpty()) {
          StringBuffer sb = new StringBuffer();
          for (Map.Entry a : failedUninstallApps.entrySet()) {
            sb.append(a.getKey());
            sb.append(": ");
            sb.append(a.getValue());
            sb.append("\n");
          }
          String errorMessage = AndroidBundle.message("deployment.failed.uninstall.prompt.androidthings.errortext", sb.toString());
          myPrompter.showErrorMessage(errorMessage);
          return false;
        }
      } else {
        printer.stdout("Installation aborted");
        return false;
      }
    }
    return true;
  }
}
