/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import static com.google.wireless.android.sdk.stats.MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Alarm;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;

public final class HeapSnapshotTraverseService {

  private static final long REPORT_COLLECTION_DELAY_MILLISECONDS = Duration.ofMinutes(30).toMillis();
  @NotNull
  private final Alarm myAlarm;

  private HeapSnapshotTraverseService() {
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  }

  @NotNull
  public static HeapSnapshotTraverseService getInstance() {
    return ApplicationManager.getApplication().getService(HeapSnapshotTraverseService.class);
  }

  public void addMemoryReportCollectionRequest() {
    if (!HeapSnapshotTraverse.ourAgentWasSuccessfullyLoaded) {
      return;
    }
    myAlarm.addRequest(this::lowerThreadPriorityAndCollectMemoryReport, REPORT_COLLECTION_DELAY_MILLISECONDS);
  }

  public void collectAndPrintMemoryReport() {
    HeapSnapshotTraverse.collectAndPrintMemoryReport();
  }

  private void lowerThreadPriorityAndCollectMemoryReport() {
    Thread currentThread = Thread.currentThread();
    int oldThreadPriority = currentThread.getPriority();

    try {
      currentThread.setPriority(Thread.MIN_PRIORITY);

      StatusCode statusCode = HeapSnapshotTraverse.collectMemoryReport();
      if (statusCode == StatusCode.NO_ERROR) {
        addMemoryReportCollectionRequest();
      }
    }
    finally {
      currentThread.setPriority(oldThreadPriority);
    }
  }
}
