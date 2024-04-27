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
package com.android.tools.idea.diagnostics;

import com.intellij.openapi.util.LowMemoryWatcher;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class MemoryUseReportContributor implements DiagnosticReportContributor {
  private static final int MEGABYTE = 1024 * 1024;
  private String myReport;
  private AtomicInteger myLowMemorySignalsCount;
  private LowMemoryWatcher myLowMemoryWatcher;

  @Override
  public void setup(DiagnosticReportConfiguration configuration) {
  }

  @Override
  public void startCollection(long timeElapsedSoFarMs) {
    myLowMemorySignalsCount = new AtomicInteger(0);
    myLowMemoryWatcher = LowMemoryWatcher.register(myLowMemorySignalsCount::incrementAndGet);
  }

  @Override
  public void stopCollection(long totalDurationMs) {
    myLowMemoryWatcher.stop();
    myLowMemoryWatcher = null;

    final long totalMemory = Runtime.getRuntime().totalMemory() / MEGABYTE;
    final long usedMemory = totalMemory - Runtime.getRuntime().freeMemory() / MEGABYTE;

    myReport = "Low memory events count: " + myLowMemorySignalsCount.get() + "\n" +
               "Total heap: " + totalMemory + "MB\n" +
               "Current heap used: " + usedMemory + "MB\n";
  }

  @Override
  public String getReport() {
    return myReport;
  }

  @Override
  public void generateReport(BiConsumer<String, String> saveReportCallback) {
    saveReportCallback.accept("memoryUseDiagnostics", getReport());
  }
}
