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
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public class MemorySettingsRecommendation {
  private static final Logger LOG = Logger.getInstance(MemorySettingsRecommendation.class);

  public static final int DEFAULT_HEAP_SIZE_IN_MB = 2048;
  public static final int LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB = 4096;
  public static final int XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB = 8192;
  public static final int LARGE_MODULE_COUNT = 100;
  public static final int XLARGE_MODULE_COUNT = 200;
  public static final int LARGE_RAM_IN_GB = 16;
  public static final int XLARGE_RAM_IN_GB = 32;

  // Returns a new Xmx if a recommendation exists, or -1 otherwise.
  public static int getRecommended(@Nullable Project project, int currentXmx) {
    if (project == null || currentXmx < 0) {
      return DEFAULT_HEAP_SIZE_IN_MB;
    }
    // TODO: check performance to count libraries to see if use it.
    int basedOnMachine = getRecommendedBasedOnMachine();
    int basedOnProject = getRecommendedBasedOnModuleCount(project);
    // only recommend a larger heap size if their machine supports it and the project is large
    int recommended = Math.min(basedOnMachine, basedOnProject);
    LOG.info(String.format(Locale.US, "recommendation based on machine: %d, on project: %d",
                           basedOnMachine, basedOnProject));
    return Math.max(recommended, currentXmx);
  }

  private static int getRecommendedBasedOnMachine() {
    int machineMemInGB = MemorySettingsUtil.getMachineMem() >> 10;
    if (machineMemInGB >= XLARGE_RAM_IN_GB) {
      return XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
    } else if (machineMemInGB >= LARGE_RAM_IN_GB) {
      return LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
    }
    return DEFAULT_HEAP_SIZE_IN_MB;
  }

  private static int getRecommendedBasedOnModuleCount(Project project) {
    int numberOfModules = ModuleManager.getInstance(project).getModules().length;
    if (numberOfModules >= XLARGE_MODULE_COUNT) {
      return XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
    } else if (numberOfModules >= LARGE_MODULE_COUNT) {
      return LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
    } else {
      return DEFAULT_HEAP_SIZE_IN_MB;
    }
  }
}
