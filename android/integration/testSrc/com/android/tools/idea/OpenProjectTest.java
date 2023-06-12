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
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher;
import com.android.tools.asdriver.tests.MemoryUsageReportProcessor;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.PerfData;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;


public class OpenProjectTest {
  @Rule
  public AndroidSystem system = AndroidSystem.standard();

  @Rule
  public MemoryDashboardNameProviderWatcher watcher = new MemoryDashboardNameProviderWatcher();

  private Benchmark benchmark;
  private long startTimeMs;
  private List<String> excludedJars = List.of("android.jar");
  private List<String> expectedJars = List.of("android-project-system-gradle-models.jar", "android-gradle.jar");

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
    installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver");
    installation.addVmOption("-Dstudio.project.sync.debug.mode=true");

    if (shouldDisableThreadingAgent()) {
      installation.addVmOption("-Dandroid.studio.instrumentation.threading.agent.disable=true");
    }

    // Create a new android project, and set a fixed distribution
    AndroidProject project = new AndroidProject("tools/adt/idea/android/integration/testData/minapp");

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(new MavenRepo("tools/adt/idea/android/integration/openproject_deps.manifest"));

    try (AndroidStudio studio = system.runStudio(project)) {
      studio.waitForSync();
      MemoryUsageReportProcessor.Companion.collectMemoryUsageStatistics(studio, system.getInstallation(), watcher, "afterSync");
      inspectAndAssertGradleSyncGradleClasspath();
    }
  }
  private void inspectAndAssertGradleSyncGradleClasspath() throws IOException {
    var allModelProviderClasspath = system.getInstallation().getIdeaLog()
      .findMatchingLines(".*?ModelProvider (.*?) Classpath: (.*)$")
      .stream().collect(Collectors.toMap(
        list -> list.get(1),
        list -> list.get(2)));
    // Must have at-least one match
    Truth.assertThat(allModelProviderClasspath).isNotEmpty();
    var remainingExpectedJarFiles = new HashSet<String>(expectedJars);
    for (var entry: allModelProviderClasspath.entrySet()) {
      String modelProvider = entry.getKey();
      String classPath = entry.getValue();
      excludedJars.forEach(excludedJar ->
        Truth.assertWithMessage("Found excluded jar " + excludedJar + " in " + modelProvider + "'s classpath")
          .that(classPath).doesNotContain(excludedJar)
      );
      remainingExpectedJarFiles.removeIf(classPath::contains);
    }
    Truth.assertThat(remainingExpectedJarFiles).isEmpty();
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
