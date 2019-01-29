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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class ActionsReportContributor implements DiagnosticReportContributor, LastActionTracker.Listener {
  private final LastActionTracker myTracker;

  // stopCollection and actionStarted/actionFinished can happen concurrently. StringBuffer (unlike StringBuilder)
  // has built-in thread access synchronization.
  private final StringBuffer myReport = new StringBuffer();
  private final AtomicInteger myActionsCount = new AtomicInteger();

  private static final int MAX_ACTIONS_COUNT = 20;

  public ActionsReportContributor(LastActionTracker tracker) {
    myTracker = tracker;
  }

  @Override
  public void setup(DiagnosticReportConfiguration configuration) {
  }

  @Override
  public void startCollection(long timeElapsedSoFarMs) {
    String actionId = myTracker.getCurrentActionId();
    long actionDurationMs = myTracker.getCurrentDurationMs();
    myReport.append("Actions:\n");
    myReport.append("Action when freeze detected: " + actionId + "\n");
    myReport.append("Action duration when freeze detected: " + actionDurationMs + "ms\n");
    myTracker.registerActionDurationListener(this);
  }

  @Override
  public void stopCollection(long totalDurationMs) {
    myTracker.unregisterActionDurationListener(this);
    myReport.append("Action when freeze ended: " + myTracker.getCurrentActionId() + "\n");
    myReport.append("Action duration when freeze ended: " + myTracker.getCurrentDurationMs() + "ms\n");
  }

  @Override
  public void actionStarted(String actionId) {
    int value = myActionsCount.incrementAndGet();
    if (value <= MAX_ACTIONS_COUNT) {
      myReport.append("Action started: " + actionId + "\n");
      if (value == MAX_ACTIONS_COUNT) {
        myReport.append("Maximum number of actions reached.\n");
      }
    }
  }

  @Override
  public void actionFinished(String actionId, long durationMs) {
    if (myActionsCount.get() <= MAX_ACTIONS_COUNT) {
      myReport.append("Action finished: " + actionId + ", duration: " + durationMs + "ms\n");
    }
  }

  @Override
  public String getReport() {
    return myReport.toString();
  }

  @Override
  public void generateReport(BiConsumer<String, String> saveReportCallback) {
    saveReportCallback.accept("actionsDiagnostics", getReport());
  }
}
