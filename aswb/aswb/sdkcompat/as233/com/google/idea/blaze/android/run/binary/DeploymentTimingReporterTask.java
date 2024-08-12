/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary;

import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.blaze.BlazeLaunchContext;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.android.tools.idea.run.tasks.DeployTasksCompat;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.LaunchMetrics;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import java.util.Collection;

/** A wrapper launch task that wraps the given deployment task and logs the deployment latency. */
public class DeploymentTimingReporterTask implements BlazeLaunchTask {
  private final BlazeLaunchTask deployTask;
  private final String launchId;
  private final ImmutableList<ApkInfo> packages;

  public DeploymentTimingReporterTask(
      String launchId, Project project, Collection<ApkInfo> packages, DeployOptions deployOptions) {
    this.launchId = launchId;
    this.deployTask = DeployTasksCompat.createDeployTask(project, packages, deployOptions);
    this.packages = ImmutableList.copyOf(packages);
  }

  @VisibleForTesting
  public ImmutableList<ApkInfo> getPackages() {
    return packages;
  }

  @Override
  public void run(BlazeLaunchContext launchContext) throws ExecutionException {
    Stopwatch s = Stopwatch.createStarted();
    try {
      deployTask.run(launchContext);
      LaunchMetrics.logDeploymentTime(launchId, s.elapsed(), true);
    } catch (ExecutionException e) {
      LaunchMetrics.logDeploymentTime(launchId, s.elapsed(), false);
      throw e;
    }
  }
}
