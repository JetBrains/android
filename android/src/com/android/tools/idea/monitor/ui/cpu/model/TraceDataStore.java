/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.monitor.ui.cpu.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

// Store all traces generated from ART or SimplePerf. Traces are segregated per project name.
public class TraceDataStore {
  private static TraceDataStore ourInstance;
  private HashMap<String, ArrayList<AppTrace>> traces;

  private TraceDataStore() {
    traces = new HashMap<>();
  }

  static public TraceDataStore getInstance() {
    if (ourInstance == null) {
      synchronized (TraceDataStore.class) {
        if (ourInstance == null) {
          ourInstance = new TraceDataStore();
        }
      }
    }
    return ourInstance;
  }

  public void addTrace(@NotNull String projectName, @NotNull AppTrace trace) {
    ArrayList<AppTrace> projectTraces = traces.get(projectName);
    if (projectTraces == null) {
      projectTraces = new ArrayList<>();
      traces.put(projectName, projectTraces);
    }
    projectTraces.add(trace);
  }

  public AppTrace getLastThreadsActivity(@NotNull String projectName) {
    ArrayList<AppTrace> projectTraces = traces.get(projectName);
    if (projectTraces == null || projectTraces.isEmpty()) {
      return null;
    }
    return projectTraces.get(projectTraces.size() - 1);
  }
}
