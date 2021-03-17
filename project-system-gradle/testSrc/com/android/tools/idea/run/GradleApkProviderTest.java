/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.tools.idea.testing.TestModuleUtil.findModule;
import static com.android.tools.idea.testing.TestProjectPaths.BUDDY_APKS;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ACTIVITY;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.containers.ContainerUtil.map;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Tests for {@link GradleApkProvider}.
 */
public class GradleApkProviderTest extends GradleApkProviderTestCase {
  public void testGetApks() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);

    // Run build task for main variant.
    String taskName = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName);
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), false);
    Collection<ApkInfo> apks = provider.getApks(mockDevice());
    assertThat(apks).hasSize(1);

    ApkInfo apk = getFirstItem(apks);
    assertNotNull(apk);
    assertEquals("from.gradle.debug", apk.getApplicationId());
    String path = apk.getFile().getPath();
    assertThat(path).endsWith(getName() + "-debug.apk");
  }

  public void testGetApksForTest() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);

    // Run build task for main variant and android test variant.
    String taskName = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    String taskNameForTest = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getAndroidTestArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName, taskNameForTest);
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);

    Collection<ApkInfo> apks = provider.getApks(mockDevice());
    assertThat(apks).hasSize(2);

    // Sort the APKs to keep test consistent.
    List<ApkInfo> apkList = new ArrayList<>(apks);
    Collections.sort(apkList, Comparator.comparing(ApkInfo::getApplicationId));
    ApkInfo mainApk = apkList.get(0);
    ApkInfo testApk = apkList.get(1);

    assertEquals("from.gradle.debug", mainApk.getApplicationId());
    String path = mainApk.getFile().getPath();
    assertThat(path).endsWith(getName() + "-debug.apk");
    assertEquals("from.gradle.debug.test", testApk.getApplicationId());
    path = testApk.getFile().getPath();

    GradleVersion modelVersion = getModel().getModelVersion();
    if (modelVersion != null) {
      if (modelVersion.compareIgnoringQualifiers("2.2.0") < 0
          // Packaging reverted in alpha4?
          || modelVersion.compareTo("2.2.0-alpha4") == 0) {
        assertThat(path).endsWith(getName() + "-debug-androidTest-unaligned.apk");
      }
      else {
        assertThat(path).endsWith(getName() + "-debug-androidTest.apk");
      }
    }
  }

  public void testGetApksForTestOnlyModule() throws Exception {
    loadProject(TEST_ONLY_MODULE, "test");

    // Run build task for main variant.
    String taskName = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName);
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);

    Collection<ApkInfo> apks = provider.getApks(mockDevice());
    ApkInfo testApk = apks.stream().filter(a -> a.getApplicationId().equals("com.example.android.app.testmodule"))
      .findFirst().orElse(null);
    assertThat(testApk).isNotNull();

    GradleVersion modelVersion = getModel().getModelVersion();
    if (modelVersion != null) {
      if (modelVersion.compareIgnoringQualifiers("2.2.0") < 0) {
        // only the test-module apk should be there
        assertThat(apks).hasSize(1);
      }
      else {
        // both test-module apk and main apk should be there
        assertThat(apks).hasSize(2);
        ApkInfo mainApk = apks.stream().filter(a -> a.getApplicationId().equals("com.example.android.app"))
          .findFirst().orElse(null);

        assertThat(mainApk).isNotNull();
      }
    }
  }

  public void testOutputModelForDynamicApp() throws Exception {
    loadProject(DYNAMIC_APP);

    // Run build task for main variant.
    String task = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), task);

    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), false);
    Collection<ApkInfo> apks = provider.getApks(mockDevice());
    assertSize(1, apks);
    ApkInfo apkInfo = apks.iterator().next();
    assertThat(apkInfo.getFiles().size()).isEqualTo(3);
    assertThat(map(apkInfo.getFiles(), x -> x.getApkFile().getName()))
      .containsExactly("app-debug.apk", "feature1-debug.apk", "dependsOnFeature1-debug.apk");
    assertThat(map(apkInfo.getFiles(), x -> x.getModuleName()))
      .containsExactly(findModule(getProject(), "app").getName(),
                       findModule(getProject(), "feature1").getName(),
                       findModule(getProject(), "dependsOnFeature1").getName());
  }

  public void testOutputModelForDynamicFeatureInstrumentedTest() throws Exception {
    loadProject(DYNAMIC_APP);

    // Run build task for main variant and android test variant.
    String taskName = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    String taskNameForTest = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getAndroidTestArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName, taskNameForTest);

    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);
    IDevice iDevice = mockDevice();
    when(iDevice.getVersion()).thenReturn(new AndroidVersion(27));
    Collection<ApkInfo> apks = provider.getApks(iDevice);
    assertSize(2, apks);
    List<ApkInfo> apkList = new ArrayList<>(apks);
    ApkInfo mainApkInfo = apkList.get(0);
    ApkInfo testApkInfo = apkList.get(1);

    assertThat(mainApkInfo.getFiles().size()).isEqualTo(3);
    assertThat(map(mainApkInfo.getFiles(), x -> x.getApkFile().getName()))
      .containsExactly("app-debug.apk", "feature1-debug.apk", "dependsOnFeature1-debug.apk");
    assertThat(map(mainApkInfo.getFiles(), x -> x.getModuleName()))
      .containsExactly(findModule(getProject(), "app").getName(),
                       findModule(getProject(), "feature1").getName(),
                       findModule(getProject(), "dependsOnFeature1").getName());

    assertThat(testApkInfo.getFiles().size()).isEqualTo(1);
    assertThat(map(testApkInfo.getFiles(), x -> x.getApkFile().getName()))
      .containsExactly("app-debug-androidTest.apk");
    assertThat(map(testApkInfo.getFiles(), x -> x.getModuleName()))
      .containsExactly("");
  }

  public void testGetApksForTestBuddyApks() throws Exception {
    loadProject(BUDDY_APKS, "app");

    // Run build task for main variant and android test variant.
    String taskName = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    String taskNameForTest = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getAndroidTestArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName, taskNameForTest);
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);
    Collection<ApkInfo> apks = provider.getApks(mockDevice());
    assertThat(Iterables.transform(apks, ApkInfo::getApplicationId))
      .containsExactly("google.testapplication", "google.testapplication.test", "com.linkedin.android.testbutler");

    // Check that we don't leak the NIO filesystem, which would prevent us from doing this twice in a row:
    provider.getApks(mockDevice());
  }

  public void testValidate() throws Exception {
    loadProject(DYNAMIC_APP);

    // Run build task for main variant and android test variant.
    String taskName = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName);
    AndroidFacet featureFacet = AndroidFacet.getInstance(getModule("feature1"));
    GradleApkProvider provider = new GradleApkProvider(featureFacet, new GradleApplicationIdProvider(featureFacet), true);

    // Invoke method to test. Validation should fail because the feature module is not signed.
    List<ValidationError> errors = provider.validate();
    assertThat(errors).hasSize(1);
    // Verify that the message contain correct output file name.
    assertThat(errors.get(0).getMessage()).isEqualTo(
      "The apk for your currently selected variant (feature1-debug.apk) is not signed. Please specify a signing configuration for this variant (debug).");
  }
}
