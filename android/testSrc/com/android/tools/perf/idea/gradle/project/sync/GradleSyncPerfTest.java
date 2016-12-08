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
package com.android.tools.perf.idea.gradle.project.sync;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.JournalingUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.BuildEnvironment;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.google.common.io.Files.write;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

/**
 * Tests to collect performance data for Gradle Sync
 */
public class GradleSyncPerfTest extends AndroidGradleTestCase {
  private JournalingUsageTracker myUsageTracker;
  private VirtualTimeScheduler myScheduler;

  @Override
  public void setUp() throws Exception {
    FSRecords.invalidateCaches();
    super.setUp();
    // Setup up an instance of the JournalingUsageTracker using defined spool directory and
    // virtual time scheduler.
    myScheduler = new VirtualTimeScheduler();

    String spoolLocation = System.getenv("ANDROID_SPOOL_HOME");
    if (spoolLocation == null) {
      // Use project directory so it will be cleaned up in tearDown
      spoolLocation = getProject().getBasePath();
    }
    myUsageTracker = new JournalingUsageTracker(new AnalyticsSettings(), myScheduler, Paths.get(spoolLocation));
    UsageTracker.setInstanceForTest(myUsageTracker);

    Project project = getProject();
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myScheduler.advanceBy(0);
      myUsageTracker.close();
      UsageTracker.cleanAfterTesting();
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  @NotNull
  protected File findSdkPath() {
    String localSdkPath = System.getenv("ANDROID_HOME");

    if (localSdkPath != null) {
      File localSdk = new File(localSdkPath);
      if (localSdk.exists()) {
        return localSdk;
      }
    }
    return super.findSdkPath();
  }

  @Override
  protected void updateVersionAndDependencies(File file) throws IOException {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          updateVersionAndDependencies(child);
        }
      }
    }
    else if (file.getPath().endsWith(DOT_GRADLE) && file.isFile()) {
      String contentsOrig = Files.toString(file, Charsets.UTF_8);
      String contents = contentsOrig;
      String localRepositories = getLocalRepositories();

      BuildEnvironment buildEnvironment = BuildEnvironment.getInstance();

      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]",
                                   buildEnvironment.getGradlePluginVersion());
      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle-experimental:(.+)['\"]",
                                   buildEnvironment.getExperimentalPluginVersion());
      contents = contents.replaceAll("repositories[ ]+\\{", "repositories {\n" + localRepositories);

      if (file.getPath().endsWith("dependencies.gradle")) {
        contents = replaceRegexGroup(contents, "buildToolsVersion: ['\"](.+)['\"]", buildEnvironment.getBuildToolsVersion());
        contents = replaceRegexGroup(contents, "minSdkVersion *: ([0-9]+)", "18");
        contents = replaceRegexGroup(contents, "compileSdkVersion *: ([0-9]+)", buildEnvironment.getCompileSdkVersion());
        contents = replaceRegexGroup(contents, "targetSdkVersion *: ([0-9]+)", buildEnvironment.getTargetSdkVersion());
        contents = replaceRegexGroup(contents, "com.squareup.okio:okio:(1.6.0)", "1.8.0");
      }

      if (file.getPath().endsWith("outissue/cyclus/build.gradle")) {
        contents =
          replaceRegexGroup(contents, "dependencies \\{(\n)", "\ncompile deps.support.appCompat\n" + "compile deps.external.rxjava\n");
      }

      if (file.getPath().endsWith("outissue/embrace/build.gradle")) {
        contents = replaceRegexGroup(contents, "dependencies \\{(\n)", "\ncompile deps.external.rxjava\n");
      }

      if (!contents.equals(contentsOrig)) {
        write(contents, file, Charsets.UTF_8);
      }
    }
  }

  public void testGradleSyncPerf() throws Exception {
    loadProject("android-studio-gradle-test");
  }
}
