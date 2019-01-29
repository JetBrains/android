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
package com.android.tools.profilers.energy;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Energy event stack trace data that are shared for the filter configuration and the Called by view.
 */
public final class EnergyTraceCache {

  // Cache each duration's trace data, which is shared by the configuration filtering and the called by view. Each duration need a service
  // call to get the called by data. As the trace data include the called by package, this saves service calls.
  @NotNull private final Map<String, String> myTraceCacheMap = new HashMap<>();
  @NotNull private final EnergyProfilerStage myStage;

  EnergyTraceCache(@NotNull EnergyProfilerStage stage) {
    myStage = stage;
  }

  /**
   * Returns the trace data shared by the configuration filtering and the duration called by view. The returned value includes the called by
   * package and method. As the stack trace raw data is as "package.Class.method (File: Line number)", get the data before the line metadata
   * in the first line, for example, "com.AlarmManager.method(Class line: 50)" results in "com.AlarmManager.method".
   */
  @NotNull
  public String getTraceData(@NotNull String traceId) {
    if (!myTraceCacheMap.containsKey(traceId)) {
      String[] qualifiedMethodSplit = myStage.requestBytes(traceId).toStringUtf8().split("\\(", 2);
      if (qualifiedMethodSplit.length > 0) {
        myTraceCacheMap.put(traceId, qualifiedMethodSplit[0]);
      }
    }
    return myTraceCacheMap.get(traceId);
  }
}
