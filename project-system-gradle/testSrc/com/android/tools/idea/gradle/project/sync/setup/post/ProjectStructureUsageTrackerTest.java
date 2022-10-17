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

import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_WATCHFACE;

import com.android.SdkConstants;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleAndroidModule;
import com.google.wireless.android.sdk.stats.GradleBuildDetails;
import com.google.wireless.android.sdk.stats.GradleLibrary;
import com.google.wireless.android.sdk.stats.GradleModule;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule;
import java.util.List;

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
    myUsageTracker = new TestUsageTracker(scheduler);
    UsageTracker.setWriterForTest(myUsageTracker);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myUsageTracker != null) myUsageTracker.close();
      UsageTracker.cleanAfterTesting();
    }
    finally {
      super.tearDown();
    }
  }

  public void testProductStructureUsageTrackingBasic() throws Exception {
    trackGradleProject(PROJECT_WITH_APP_AND_LIB_DEPENDENCY);

    List<LoggedUsage> usages = myUsageTracker.getUsages();

    assertEquals(1,
                 usages.stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind()).count());
    LoggedUsage usage =
      usages.stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind()).findFirst().get();
    assertEquals(0, usage.getTimestamp());
    assertEquals(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS, usage.getStudioEvent().getKind());
    assertEquals(GradleBuildDetails.newBuilder()
                   .setAndroidPluginVersion(LatestKnownPluginVersionProvider.INSTANCE.get())
                   .setGradleVersion(GradleVersions.inferStableGradleVersion(SdkConstants.GRADLE_LATEST_VERSION))
                   .addLibraries(GradleLibrary.newBuilder()
                                   .setJarDependencyCount(12)
                                   .setAarDependencyCount(49))
                   .addModules(GradleModule.newBuilder()
                                 .setTotalModuleCount(3)
                                 .setAppModuleCount(1)
                                 .setLibModuleCount(1))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AnonymizerUtil.anonymizeUtf8("testProductStructureUsageTrackingBasic.app"))
                                        .setIsLibrary(false)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(2)
                                        .setFlavorDimension(1)
                                        .setSigningConfigCount(1))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AnonymizerUtil.anonymizeUtf8("testProductStructureUsageTrackingBasic.lib"))
                                        .setIsLibrary(true)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(0)
                                        .setFlavorDimension(0)
                                        .setSigningConfigCount(1))
                   .setAppId(AnonymizerUtil.anonymizeUtf8("com.example.projectwithappandlib.app"))
                   .build(), usage.getStudioEvent().getGradleBuildDetails());
  }


  // TODO(b/240662565): Test is flaky.
  public void /*test*/ProductStructureUsageWithWearHardware() throws Exception {
    trackGradleProject(RUN_CONFIG_WATCHFACE);

    List<LoggedUsage> usages = myUsageTracker.getUsages();

    assertEquals(1,
                 usages.stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind()).count());
    LoggedUsage usage =
      usages.stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind()).findFirst().get();
    assertEquals(0, usage.getTimestamp());
    assertEquals(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS, usage.getStudioEvent().getKind());
    String appId = usage.getStudioEvent().getGradleBuildDetails().getAppId();
    assertEquals(GradleBuildDetails.newBuilder()
                   .setAndroidPluginVersion(LatestKnownPluginVersionProvider.INSTANCE.get())
                   .setGradleVersion(GradleVersions.inferStableGradleVersion(SdkConstants.GRADLE_LATEST_VERSION))
                   .addLibraries(GradleLibrary.newBuilder()
                                   .setJarDependencyCount(0)
                                   .setAarDependencyCount(0))
                   .addModules(GradleModule.newBuilder()
                                 .setTotalModuleCount(1)
                                 .setAppModuleCount(1)
                                 .setLibModuleCount(0))
                   .addAndroidModules(GradleAndroidModule.newBuilder()
                                        .setModuleName(AnonymizerUtil.anonymizeUtf8("testProductStructureUsageWithWearHardware"))
                                        .setIsLibrary(false)
                                        .setBuildTypeCount(2)
                                        .setFlavorCount(0)
                                        .setFlavorDimension(0)
                                        .setSigningConfigCount(1)
                                        .setRequiredHardware("android.hardware.type.watch"))
                   .setAppId(appId)
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
  }
}
