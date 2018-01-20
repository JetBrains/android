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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.SdkConstants;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.wireless.android.sdk.stats.*;
import com.intellij.openapi.module.ModuleManager;

import java.util.List;

import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;

/**
 * Tests for {@link ProjectStructureUsageTracker}.
 */
public class ProjectStructureUsageTrackerTest extends AndroidGradleTestCase {

  // Used to test the scheduling of usage tracking.
  private VirtualTimeScheduler scheduler;
  // A UsageTracker implementation that allows introspection of logged metrics in tests.
  private TestUsageTracker myUsageTracker;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    scheduler = new VirtualTimeScheduler();
    myUsageTracker = new TestUsageTracker(new AnalyticsSettings(), scheduler);
    UsageTracker.setInstanceForTest(myUsageTracker);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myUsageTracker.close();
    UsageTracker.cleanAfterTesting();
  }

  // b/72260139
  public void ignore_testProductStructureUsageTrackingBasic() throws Exception {
    trackGradleProject(PROJECT_WITH_APPAND_LIB);

    List<LoggedUsage> usages = myUsageTracker.getUsages();

    assertEquals(5, usages.size());
    LoggedUsage usage = usages.get(4);
    assertEquals(0, usage.getTimestamp());
    assertEquals(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS, usage.getStudioEvent().getKind());
    assertEquals(GradleBuildDetails.newBuilder()
                   .setAndroidPluginVersion(AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion())
                   .setGradleVersion(GradleVersions.removeTimestampFromGradleVersion(SdkConstants.GRADLE_LATEST_VERSION))
                   .setUserEnabledIr(true)
                   .setModelSupportsIr(true)
                   .setVariantSupportsIr(true)
                   .addLibraries(GradleLibrary.newBuilder()
                                   .setJarDependencyCount(3)
                                   .setAarDependencyCount(10))
                   .addModules(GradleModule.newBuilder()
                                 .setTotalModuleCount(3)
                                 .setAppModuleCount(1)
                                 .setLibModuleCount(1))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AnonymizerUtil.anonymizeUtf8("app"))
                                        .setIsLibrary(false)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(2)
                                        .setFlavorDimension(1)
                                        .setSigningConfigCount(1))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AnonymizerUtil.anonymizeUtf8("lib"))
                                        .setIsLibrary(true)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(0)
                                        .setFlavorDimension(0)
                                        .setSigningConfigCount(1))
                   .setAppId(AnonymizerUtil.anonymizeUtf8("com.example.projectwithappandlib.app"))
                   .build(), usage.getStudioEvent().getGradleBuildDetails());
  }

  // See https://code.google.com/p/android/issues/detail?id=224985
  // Disabled until the prebuilt SDK has CMake.
  public void /*test*/ProductStructureUsageTrackingJni() throws Exception {
    trackGradleProject(HELLO_JNI);

    List<LoggedUsage> usages = myUsageTracker.getUsages();

    assertEquals(3, usages.size());
    LoggedUsage usage = usages.get(2);
    assertEquals(0, usage.getTimestamp());
    assertEquals(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS, usage.getStudioEvent().getKind());
    assertEquals(GradleBuildDetails.newBuilder()
                   //TODO: add once reenabled
                   .build(), usage.getStudioEvent().getGradleBuildDetails());
  }

  public void testGetApplicationId() throws Exception {
    loadProject(PROJECT_WITH_APPAND_LIB);
    assertEquals("com.example.projectwithappandlib.app", ProjectStructureUsageTracker.getApplicationId(getProject()));
  }

  public void testStringToBuildSystemType() {
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.NDK_BUILD,
                 ProjectStructureUsageTracker.stringToBuildSystemType("ndkBuild"));
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.CMAKE,
                 ProjectStructureUsageTracker.stringToBuildSystemType("cmake"));
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.NDK_COMPILE,
                 ProjectStructureUsageTracker.stringToBuildSystemType("ndkCompile"));
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.GRADLE_EXPERIMENTAL,
                 ProjectStructureUsageTracker.stringToBuildSystemType("gradle"));
    assertEquals(GradleNativeAndroidModule.NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE,
                 ProjectStructureUsageTracker.stringToBuildSystemType("blaze"));
  }

  /**
   * Builds a set of mock objects representing an Android Studio project with a set of modules
   * and calls the tracking code to report metrics on this project.
   */
  private void trackGradleProject(String project) throws Exception {
    loadProject(project);
    ProjectStructureUsageTracker psut = new ProjectStructureUsageTracker(getProject());
    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    psut.trackProjectStructure(moduleManager.getModules());
  }
}
