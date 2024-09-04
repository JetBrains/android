/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.runsGradle;

import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.verifySyncSkipped;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.plugin.AgpVersions;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectStructureUsageTrackerManager;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectStructureUsageTrackerSyncListener;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject;
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleAndroidModule;
import com.google.wireless.android.sdk.stats.GradleBuildDetails;
import com.google.wireless.android.sdk.stats.GradleLibrary;
import com.google.wireless.android.sdk.stats.GradleModule;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link ProjectStructureUsageTrackerSyncListener}.
 */
@RunsInEdt
public class ProjectStructureUsageTrackerSyncListenerTest {
  @Rule public IntegrationTestEnvironmentRule projectRule = AndroidProjectRule.withIntegrationTestEnvironment();
  // A UsageTracker implementation that allows introspection of logged metrics in tests.
  private TestUsageTracker myUsageTracker;

  private static final long DEFAULT_TIMEOUT_PROJECT_STRUCTURE_TRACKER_MILLIS = 10000;

  @Before
  public void setUp() throws Exception {
    // Used to test the scheduling of usage tracking.
    VirtualTimeScheduler scheduler = new VirtualTimeScheduler();
    myUsageTracker = new TestUsageTracker(scheduler);
    UsageTracker.setWriterForTest(myUsageTracker);
  }

  @After
  public void tearDown() throws Exception {
    if (myUsageTracker != null) myUsageTracker.close();
    UsageTracker.cleanAfterTesting();
  }

  @Test
  public void testProductStructureUsageTrackingBasic() {
    trackGradleProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY, project -> {
      List<LoggedUsage> usages =
        myUsageTracker.getUsages().stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind())
          .toList();
      assertThat(usages).hasSize(1);
      verifyAppAndLibBuildDetails(usages.get(0));
    });
  }

  @Test
  @Ignore("b/354210253")
  public void testProductStructureUsageWithWearHardware() {
    trackGradleProject(AndroidCoreTestProject.RUN_CONFIG_WATCHFACE, project -> {
      List<LoggedUsage> usages =
        myUsageTracker.getUsages().stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind())
          .toList();
      assertThat(usages).hasSize(1);
      LoggedUsage usage = usages.get(0);
      assertThat(usage.getTimestamp()).isEqualTo(0);
      assertThat(usage.getStudioEvent().getKind()).isEqualTo(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS);
      assertThat(usage.getStudioEvent().getGradleBuildDetails()).isEqualTo(
        GradleBuildDetails.newBuilder()
          .setAndroidPluginVersion(AgpVersions.getLatestKnown().toString())
          .setGradleVersion(GradleVersions.inferStableGradleVersion(SdkConstants.GRADLE_LATEST_VERSION))
          .addLibraries(GradleLibrary.newBuilder()
                          .setJarDependencyCount(0)
                          .setAarDependencyCount(0))
          .addModules(GradleModule.newBuilder()
                        .setTotalModuleCount(1)
                        .setAppModuleCount(1)
                        .setLibModuleCount(0)
                        .setDynamicFeatureModuleCount(0)
                        .setTestModuleCount(0)
                        .setKotlinMultiplatformModuleCount(0))
          .addAndroidModules(GradleAndroidModule.newBuilder()
                               .setModuleName(AnonymizerUtil.anonymizeUtf8("project"))
                               .setIsLibrary(false)
                               .setBuildTypeCount(2)
                               .setFlavorCount(0)
                               .setFlavorDimension(0)
                               .setSigningConfigCount(1)
                               .setRequiredHardware("android.hardware.type.watch"))
          .setModuleCount(1)
          .setLibCount(0)
          .setAppId(AnonymizerUtil.anonymizeUtf8("from.gradle.debug"))
          .build());
    });
  }

  @Test
  public void testProductStructureUsageTrackingJni() {
    trackGradleProject(AndroidCoreTestProject.HELLO_JNI, project -> {
      List<LoggedUsage> usages =
        myUsageTracker.getUsages().stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind())
          .toList();
      assertThat(usages).hasSize(1);
      LoggedUsage usage = usages.get(0);
      assertThat(usage.getTimestamp()).isEqualTo(0);
      assertThat(usage.getStudioEvent().getGradleBuildDetails()).isEqualTo(
        GradleBuildDetails.newBuilder()
          .setAndroidPluginVersion(AgpVersions.getLatestKnown().toString())
          .setGradleVersion(GradleVersions.inferStableGradleVersion(SdkConstants.GRADLE_LATEST_VERSION))
          .addLibraries(GradleLibrary.newBuilder()
                          .setJarDependencyCount(5)
                          .setAarDependencyCount(27))
          .addModules(GradleModule.newBuilder()
                        .setTotalModuleCount(2)
                        .setAppModuleCount(1)
                        .setLibModuleCount(0)
                        .setDynamicFeatureModuleCount(0)
                        .setTestModuleCount(0)
                        .setKotlinMultiplatformModuleCount(0))
          .addAndroidModules(GradleAndroidModule.newBuilder()
                               .setModuleName(AnonymizerUtil.anonymizeUtf8("project.app"))
                               .setIsLibrary(false)
                               .setBuildTypeCount(2)
                               .setFlavorCount(5)
                               .setFlavorDimension(1)
                               .setSigningConfigCount(1))
          .addNativeAndroidModules(GradleNativeAndroidModule.newBuilder()
                                     .setModuleName(AnonymizerUtil.anonymizeUtf8("project.app.main"))
                                     .setBuildSystemType(GradleNativeAndroidModule.NativeBuildSystemType.CMAKE)
                                     .setNdkVersion(SdkConstants.NDK_DEFAULT_VERSION))
          .setModuleCount(2)
          .setLibCount(32)
          .setAppId(AnonymizerUtil.anonymizeUtf8("com.example.hellojni"))
          .build());
    });
  }

  /**
   * Confirm that build details are tracked when a sync is skipped (when a previously synced project was reopened)
   */
  @Test
  public void testSkippedSyncTracksBuildDetails() {
    PreparedTestProject preparedProject = trackGradleProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY, project -> {
      List<LoggedUsage> usages =
        myUsageTracker.getUsages().stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind())
          .toList();
      // Only an event should happen the first time it is open
      assertThat(usages).hasSize(1);
    });
    preparedProject.open(it -> it, project -> {
      verifySyncSkipped(project, projectRule.getTestRootDisposable());
      waitForProjectStructureUsageTracker(project);
      List<LoggedUsage> usages =
        myUsageTracker.getUsages().stream().filter(it -> AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS == it.getStudioEvent().getKind())
          .toList();
      // Now 2 events should have been logged
      assertThat(usages).hasSize(2);
      for (LoggedUsage usage : usages) {
        verifyAppAndLibBuildDetails(usage);
      }
    });
  }

  /**
   * Builds a set of mock objects representing an Android Studio project with a set of modules
   * and calls the tracking code to report metrics on this project.
   *
   * @return the prepared project that was created
   */
  private PreparedTestProject trackGradleProject(@NotNull TemplateBasedTestProject testProject, @NotNull Consumer<Project> test) {
    final var preparedProject = prepareTestProject(projectRule, testProject);
    preparedProject.open(it -> it, project -> {
      test.accept(project);
      return null;
    });
    return preparedProject;
  }

  private void verifyAppAndLibBuildDetails(LoggedUsage usage) {
    assertThat(usage.getTimestamp()).isEqualTo(0);
    assertThat(usage.getStudioEvent().getKind()).isEqualTo(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS);
    // The order of the modules is not always the same, and thus we cannot compare the details directly
    // since assertEquals will fail when the order is different to the expected details
    GradleBuildDetails buildDetails = usage.getStudioEvent().getGradleBuildDetails();
    assertThat(buildDetails.getAndroidPluginVersion()).isEqualTo(AgpVersions.getLatestKnown().toString());
    assertThat(buildDetails.getGradleVersion()).isEqualTo(GradleVersions.inferStableGradleVersion(SdkConstants.GRADLE_LATEST_VERSION));
    assertThat(buildDetails.getLibrariesList()).containsExactly(
      GradleLibrary.newBuilder()
        .setJarDependencyCount(9)
        .setAarDependencyCount(49)
        .build());
    assertThat(buildDetails.getModulesList()).containsExactly(
      GradleModule.newBuilder()
        .setTotalModuleCount(3)
        .setAppModuleCount(1)
        .setLibModuleCount(1)
        .setDynamicFeatureModuleCount(0)
        .setTestModuleCount(0)
        .setKotlinMultiplatformModuleCount(0)
        .build());
    assertThat(buildDetails.getAndroidModulesList()).containsExactly(
      GradleAndroidModule.newBuilder()
        .setModuleName(AnonymizerUtil.anonymizeUtf8("project.lib"))
        .setIsLibrary(true)
        .setBuildTypeCount(2)
        .setFlavorCount(0)
        .setFlavorDimension(0)
        .setSigningConfigCount(1)
        .build(),
      GradleAndroidModule.newBuilder()
        .setModuleName(AnonymizerUtil.anonymizeUtf8("project.app"))
        .setIsLibrary(false)
        .setBuildTypeCount(2)
        .setFlavorCount(2)
        .setFlavorDimension(1)
        .setSigningConfigCount(1)
        .build());
    assertThat(buildDetails.getModuleCount()).isEqualTo(3);
    assertThat(buildDetails.getLibCount()).isEqualTo(79);
    assertThat(buildDetails.getAppId()).isEqualTo(AnonymizerUtil.anonymizeUtf8("com.example.projectwithappandlib.app"));
  }

  private void waitForProjectStructureUsageTracker(Project project) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        ProjectStructureUsageTrackerManager.getInstance(project).consumeBulkOperationsState(future -> {
          PlatformTestUtil.waitForFuture(future, DEFAULT_TIMEOUT_PROJECT_STRUCTURE_TRACKER_MILLIS);
          return null;
        });
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }
}