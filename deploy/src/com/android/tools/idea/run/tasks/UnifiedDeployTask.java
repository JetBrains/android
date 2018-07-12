/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.deployer.Apk;
import com.android.tools.deployer.DdmDevice;
import com.android.tools.deployer.Deployer;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UnifiedDeployTask implements LaunchTask, Deployer.InstallerCallBack {

  private final Collection<ApkInfo> myApks;

  public UnifiedDeployTask(@NotNull Collection<ApkInfo> apks) {
    myApks = apks;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Installing APK";
  }

  @Override
  public int getDuration() {
    return 20;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    for (ApkInfo apk : myApks) {
      List<String> paths = apk.getFiles().stream().map(
        apkunit -> apkunit.getApkFile().getPath()).collect(Collectors.toList());
      Deployer deployer = new Deployer(apk.getApplicationId(), paths, this, new DdmDevice(device));
      HashMap<String, HashMap<String, Apk.ApkEntryStatus>> changes = deployer.run();
      for (Map.Entry<String, HashMap<String, Apk.ApkEntryStatus>> entry : changes.entrySet()) {
        System.err.println("Apk: " + entry.getKey());
        for (Map.Entry<String, Apk.ApkEntryStatus> statusEntry : entry.getValue().entrySet()) {
          System.err.println("  " + statusEntry.getKey() +
                             " [" + statusEntry.getValue().toString().toLowerCase() + "]");
        }
      }
    }

    return true;
  }

  @Override
  public void onInstallationFinished(boolean status) {
    System.err.println("Installation finished");
  }
}
