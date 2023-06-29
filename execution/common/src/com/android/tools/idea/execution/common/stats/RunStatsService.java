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
package com.android.tools.idea.execution.common.stats;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class RunStatsService implements Disposable {

  private final Project myProject;
  private RunStats myLastRunStats;

  public RunStatsService(Project project) {
    myProject = project;
    project.getMessageBus().connect(this).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
        RunStats stats = env.getUserData(RunStats.KEY);
        if (stats != null) {
          stats.abort();
        }
      }
    });
  }

  public synchronized RunStats create() {
    if (myLastRunStats != null) {
      // If this event was logged, then this is a no-op
      myLastRunStats.abandoned();
    }
    myLastRunStats = new RunStats(myProject);
    return myLastRunStats;
  }

  @Override
  public void dispose() { }

  public static RunStatsService get(Project project) {
    return project.getService(RunStatsService.class);
  }
}
