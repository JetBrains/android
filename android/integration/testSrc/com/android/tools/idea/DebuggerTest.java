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
package com.android.tools.idea;

import com.android.tools.asdriver.tests.Adb;
import com.android.tools.asdriver.tests.AndroidProject;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.Emulator;
import com.android.tools.asdriver.tests.MavenRepo;
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher;
import com.android.tools.asdriver.tests.MemoryUsageReportProcessor;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;

public class DebuggerTest {
  @Rule
  public AndroidSystem system = AndroidSystem.standard();

  @Rule
  public MemoryDashboardNameProviderWatcher watcher = new MemoryDashboardNameProviderWatcher();

  public Metric metric = new Metric("Time-elapsed");

  @Test
  public void runDebuggerTest() throws Exception {
    AndroidProject project = new AndroidProject("tools/adt/idea/android/integration/testData/mindebugapp");
    // Create a maven repo and set it up in the installation and environment
    system.installRepo(new MavenRepo("tools/adt/idea/android/integration/buildproject_deps.manifest"));

    long startTime = System.currentTimeMillis();

    try (Adb adb = system.runAdb();
         Emulator emulator = system.runEmulator();
         AndroidStudio studio = system.runStudio(project)) {
      studio.waitForSync();
      studio.waitForIndex();
      studio.executeAction("MakeGradleProject");
      studio.waitForBuild();
      metric.addSamples(new Benchmark.Builder("Debugger-before-boot").setProject("Android Studio E2E").build(),
                        new Metric.MetricSample(Instant.now().toEpochMilli(), System.currentTimeMillis() - startTime));
      emulator.waitForBoot();
      metric.addSamples(new Benchmark.Builder("Debugger-after-boot").setProject("Android Studio E2E").build(),
                        new Metric.MetricSample(Instant.now().toEpochMilli(), System.currentTimeMillis() - startTime));
      adb.waitForDevice(emulator);
      adb.runCommand("logcat");
      System.out.println("Opening a file");
      Path path = project.getTargetProject().resolve("src/main/java/com/example/mindebugapp/MainActivity.kt");
      String projectName = project.getTargetProject().getFileName().toString();
      studio.openFile(projectName, path.toString(), 17, 0);

      System.out.println("Setting a breakpoint");
      studio.executeAction("ToggleLineBreakpoint");
      MemoryUsageReportProcessor.Companion.collectMemoryUsageStatistics(studio, system.getInstallation(), watcher, "breakpointSet");

      System.out.println("Debugging the application");
      studio.executeAction("android.deploy.DebugWithoutBuild");
      studio.waitForDebuggerToHitBreakpoint();
      metric.addSamples(new Benchmark.Builder("Debugger-total-time-boot").setProject("Android Studio E2E").build(),
                        new Metric.MetricSample(Instant.now().toEpochMilli(), System.currentTimeMillis() - startTime));
      metric.commit();
    }
  }
}