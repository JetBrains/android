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

import com.android.tools.asdriver.tests.AndroidProject;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.MavenRepo;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.PerfData;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;


public class OpenProjectTest {
  @Rule
  public AndroidSystem system = AndroidSystem.standard();

  private Benchmark benchmark;
  private long startTimeMs;

  @Before
  public void setUp() throws Exception {
    benchmark = createBenchmark(shouldDisableThreadingAgent());
    startTimeMs = System.currentTimeMillis();
  }

  @After
  public void tearDown() {
    benchmark.log("total_time", System.currentTimeMillis() - startTimeMs);
  }

  @Test
  public void openProjectTest() throws Exception {
    AndroidStudioInstallation installation = system.getInstallation();
    if (shouldDisableThreadingAgent()) {
      installation.addVmOption("-Dandroid.studio.instrumentation.threading.agent.disable=true");
    }

    // Create a new android project, and set a fixed distribution
    AndroidProject project = new AndroidProject("tools/adt/idea/android/integration/testData/minapp");
    project.setDistribution("tools/external/gradle/gradle-7.2-bin.zip");

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(new MavenRepo("tools/adt/idea/android/integration/openproject_deps.manifest"));

    try (AndroidStudio studio = system.runStudio(project)) {
      studio.waitForSync();
    }
  }

  private static Benchmark createBenchmark(boolean threadingAgentDisabled) throws Exception {
    String benchmarkName = threadingAgentDisabled ? "OpenProject-withAgentDisabled" : "OpenProject-withAgentEnabled";
    PerfData perfData = new PerfData();

    Benchmark benchmark =
      new Benchmark.Builder(benchmarkName)
        .setProject("Android Studio Threading Agent")
        .build();
    perfData.addBenchmark(benchmark);
    perfData.commit();

    return benchmark;
  }

  private static boolean shouldDisableThreadingAgent() {
    return "true".equalsIgnoreCase(System.getProperty("threading_agent.disabled"));
  }
}
