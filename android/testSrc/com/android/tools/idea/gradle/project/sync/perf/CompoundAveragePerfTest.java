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
package com.android.tools.idea.gradle.project.sync.perf;

import static com.android.tools.idea.testing.TestProjectPaths.SYNC_AVERAGE_PERF_PROJECT;

import org.jetbrains.annotations.NotNull;

/**
 * Measure performance for compound sync using the average project.
 */
public class CompoundAveragePerfTest extends GradleSyncPerformanceTestCase {
  @NotNull
  @Override
  public String getMetricName() {
    return "Average-Project-Compound-sync";
  }

  @NotNull
  @Override
  public String getRelativePath() {
    return SYNC_AVERAGE_PERF_PROJECT;
  }

  @Override
  protected boolean useNewSyncInfrastructure() {
    return true;
  }

  @Override
  protected boolean useSingleVariantSyncInfrastructure() {
    return true;
  }

  @Override
  protected boolean useCompoundSyncInfrastructure() {
    return true;
  }
}
