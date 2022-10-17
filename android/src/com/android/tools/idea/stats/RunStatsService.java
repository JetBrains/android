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
package com.android.tools.idea.stats;

import com.intellij.openapi.project.Project;

public class RunStatsService {

  private RunStats myLastRunStats;
  private Project myProject;

  public synchronized RunStats create() {
    if (myLastRunStats != null) {
      // If this event was logged, then this is a no-op
      myLastRunStats.abort();
    }
    myLastRunStats = new RunStats(myProject);
    return myLastRunStats;
  }

  public static RunStatsService get(Project project) {
    RunStatsService result = project.getService(RunStatsService.class);
    result.myProject = project;
    return result;
  }
}
