/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.memorysettings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public final class MemorySettingsRecommendation {
  private static final Logger LOG = Logger.getInstance(MemorySettingsRecommendation.class);

  // Returns a new Xmx if a recommendation exists, or -1 otherwise.
  public static int getRecommended(@Nullable Project project, int currentXmx) {
    if (project == null || currentXmx < 0) {
      return -1;
    }

    // TODO: check performance to count libraries to see if use it.
    int basedOnMachine = getRecommendedBasedOnMachine();
    int basedOnProject = getRecommendedBasedOnModuleCount(project);
    int recommended = Math.min(basedOnMachine, basedOnProject);
    if (basedOnMachine >= 2048 && recommended < 2048 && !SystemInfo.isWindows) {
      // For non-Windows machines with at least 8GB RAM, recommend at least 2GB
      recommended = 2048;
    }
    LOG.info(String.format(Locale.US, "recommendation based on machine: %d, on project: %d",
                           basedOnMachine, basedOnProject));
    return currentXmx < recommended * 0.9 ? recommended : -1;
  }

  private static int getRecommendedBasedOnMachine() {
    int machineMemInGB = MemorySettingsUtil.getMachineMem() >> 10;
    boolean isWindows = SystemInfo.isWindows;
    if (machineMemInGB < 8) {
      return isWindows? 1280 : 1536;
    } else if (machineMemInGB < 12) {
      return isWindows? 1536 : 2048;
    } else if (machineMemInGB < 16) {
      return 3072;
    } else {
      return 4096;
    }
  }

  private static int getRecommendedBasedOnModuleCount(Project project) {
    int count = ModuleManager.getInstance(project).getModules().length;
    if (count < 50) {
      return 1280;
    } else if (count < 100) {
      return 2048;
    } else if (count < 200) {
      return 3072;
    } else {
      return 4096;
    }
  }
}
