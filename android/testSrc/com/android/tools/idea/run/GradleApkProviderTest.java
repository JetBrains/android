/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.OutputFile;
import com.android.builder.model.*;
import com.android.ddmlib.IDevice;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_TEST;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GradleApkProvider}.
 */
public class GradleApkProviderTest extends AndroidGradleTestCase {
  public void testGetApks() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), false);
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertThat(apks).hasSize(1);

    ApkInfo apk = getFirstItem(apks);
    assertNotNull(apk);
    assertEquals("from.gradle.debug", apk.getApplicationId());
    String path = apk.getFile().getPath();
    assertThat(path).endsWith(getName() + "-debug.apk");
  }

  public void testGetApksForTest() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);

    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertThat(apks).hasSize(2);

    // Sort the APKs to keep test consistent.
    List<ApkInfo> apkList = new ArrayList<>(apks);
    Collections.sort(apkList, Comparator.comparing(ApkInfo::getApplicationId));
    ApkInfo mainApk = apkList.get(0);
    ApkInfo testApk = apkList.get(1);

    assertEquals("from.gradle.debug", mainApk.getApplicationId());
    String path = mainApk.getFile().getPath();
    assertThat(path).endsWith(getName() + "-debug.apk");

    assertEquals(testApk.getApplicationId(), "from.gradle.debug.test");
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
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);

    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    ApkInfo testApk = apks.stream().filter(a -> a.getApplicationId().equals("com.example.android.app.testmodule"))
      .findFirst().orElse(null);
    assertThat(testApk).isNotNull();

    GradleVersion modelVersion = getModel().getModelVersion();
    if (modelVersion != null) {
      if (modelVersion.compareIgnoringQualifiers("2.2.0") < 0) {
        // only the test-module apk should be there
        assertThat(apks).hasSize(1);
      } else {
        // both test-module apk and main apk should be there
        assertThat(apks).hasSize(2);
        ApkInfo mainApk = apks.stream().filter(a -> a.getApplicationId().equals("com.example.android.app"))
          .findFirst().orElse(null);

        assertThat(mainApk).isNotNull();
      }
    }
  }

  public void testOutputModelForInstantApp() throws Exception {
    loadProject(INSTANT_APP);
    File apk = mock(File.class);
    PostBuildModelProviderStub outputProvider = new PostBuildModelProviderStub();
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), outputProvider, false);
    outputProvider.setInstantAppProjectBuildOutput(myAndroidFacet, createInstantAppProjectBuildOutputMock("debug", apk));
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertSize(1, apks);
    assertEquals(apk, apks.iterator().next().getFile());
  }

  public void testGetApksForTestBuddyApks() throws Exception {
    loadProject(BUDDY_APKS, "test");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertThat(Iterables.transform(apks, ApkInfo::getApplicationId))
      .containsExactly("google.testapplication", "google.testapplication.test", "com.linkedin.android.testbutler");

    // Check that we don't leak the NIO filesystem, which would prevent us from doing this twice in a row:
    provider.getApks(mock(IDevice.class));
  }

  public void testOutputModel() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    File apk = mock(File.class);
    PostBuildModelProviderStub outputProvider = new PostBuildModelProviderStub();
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), outputProvider, false);
    outputProvider.setProjectBuildOutput(myAndroidFacet, createProjectBuildOutputMock("debug", apk));
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertSize(1, apks);
    assertEquals(apk, apks.iterator().next().getFile());
  }

  public void testOutputModelForTestOnlyModules() throws Exception {
    loadProject(TEST_ONLY_MODULE, "test");
    File apk = mock(File.class);
    File testedApk = mock(File.class);
    PostBuildModelProviderStub outputProvider = new PostBuildModelProviderStub();
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), outputProvider, true);

    assertEquals(PROJECT_TYPE_TEST, myAndroidFacet.getProjectType());
    AndroidModel androidModel = myAndroidFacet.getConfiguration().getModel();
    assertInstanceOf(androidModel, AndroidModuleModel.class);
    for (TestedTargetVariant testedTargetVariant : ((AndroidModuleModel)androidModel).getSelectedVariant().getTestedTargetVariants()) {
      Module targetModule = findModuleByGradlePath(getProject(), testedTargetVariant.getTargetProjectPath());
      assertNotNull(targetModule);
      AndroidFacet facet = AndroidFacet.getInstance(targetModule);
      assertNotNull(facet);
      outputProvider.setProjectBuildOutput(facet, createProjectBuildOutputMock(testedTargetVariant.getTargetVariant(), testedApk));
    }

    outputProvider.setProjectBuildOutput(myAndroidFacet, createProjectBuildOutputMock("debug", apk));
    Collection<ApkInfo> apkInfos = provider.getApks(mock(IDevice.class));
    Collection<File> apks = Collections2.transform(apkInfos, ApkInfo::getFile);
    assertThat(apks).containsExactly(apk, testedApk);
  }

  private static ProjectBuildOutput createProjectBuildOutputMock(@NotNull String variant, @NotNull File file) {
    ProjectBuildOutput projectBuildOutput = mock(ProjectBuildOutput.class);
    VariantBuildOutput variantBuildOutput = mock(VariantBuildOutput.class);
    OutputFile outputFile = mock(OutputFile.class);
    when(projectBuildOutput.getVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getOutputs()).thenReturn(Collections.singleton(outputFile));
    when(outputFile.getOutputFile()).thenReturn(file);
    return projectBuildOutput;
  }

  private static InstantAppProjectBuildOutput createInstantAppProjectBuildOutputMock(@NotNull String variant, @NotNull File file) {
    InstantAppProjectBuildOutput projectBuildOutput = mock(InstantAppProjectBuildOutput.class);
    InstantAppVariantBuildOutput variantBuildOutput = mock(InstantAppVariantBuildOutput.class);
    OutputFile outputFile = mock(OutputFile.class);
    when(projectBuildOutput.getInstantAppVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getOutput()).thenReturn(outputFile);
    when(outputFile.getOutputFile()).thenReturn(file);
    return projectBuildOutput;
  }

  private static class PostBuildModelProviderStub implements PostBuildModelProvider {
    @NotNull private final PostBuildModel myPostBuildModel = mock(PostBuildModel.class);

    void setProjectBuildOutput(@NotNull AndroidFacet facet, @NotNull ProjectBuildOutput projectBuildOutput) {
      when(myPostBuildModel.findProjectBuildOutput(facet)).thenReturn(projectBuildOutput);
    }

    void setInstantAppProjectBuildOutput(@NotNull AndroidFacet facet, @NotNull InstantAppProjectBuildOutput instantAppProjectBuildOutput) {
      when(myPostBuildModel.findInstantAppProjectBuildOutput(facet)).thenReturn(instantAppProjectBuildOutput);
    }

    @Override
    @NotNull
    public PostBuildModel getPostBuildModel() {
      return myPostBuildModel;
    }
  }
}
