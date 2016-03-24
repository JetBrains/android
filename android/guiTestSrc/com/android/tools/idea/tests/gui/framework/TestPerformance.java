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
package com.android.tools.idea.tests.gui.framework;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

class TestPerformance extends TestWatcher {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

  private final List<GarbageCollectorMXBean> myGarbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
  private final MemoryMXBean myMemoryMXBean = ManagementFactory.getMemoryMXBean();
  private long myGcCount, myGcTime;

  @Override
  protected void starting(Description description) {
    printPerfStats();
    System.out.println(DATE_FORMAT.format(new Date()));
  }

  @Override
  protected void finished(Description description) {
    System.out.println(DATE_FORMAT.format(new Date()));
    printPerfStats();
  }

  private void printPerfStats() {
    long gcCount = 0, gcTime = 0;
    for (GarbageCollectorMXBean garbageCollectorMXBean : myGarbageCollectorMXBeans) {
      gcCount += garbageCollectorMXBean.getCollectionCount();
      gcTime += garbageCollectorMXBean.getCollectionTime();
    }

    long gcCountDiff = gcCount - myGcCount;
    long gcTimeDiff = gcTime - myGcTime;
    myGcCount = gcCount;
    myGcTime = gcTime;

    System.out.printf("cumulative garbage collections: %d, %d ms (this test %d, %dms)%n", gcCount, gcTime, gcCountDiff, gcTimeDiff);
    myMemoryMXBean.gc();
    System.out.printf("heap: %s%n", myMemoryMXBean.getHeapMemoryUsage());
    System.out.printf("non-heap: %s%n", myMemoryMXBean.getNonHeapMemoryUsage());
  }
}
