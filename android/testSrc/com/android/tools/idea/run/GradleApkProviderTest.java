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
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.ddmlib.IDevice;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.run.ProjectBuildOutputProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ACTIVITY;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
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
    Collections.sort(apkList, (a, b) -> a.getApplicationId().compareTo(b.getApplicationId()));
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

  public void /*test*/GetApksForTestOnlyModule() throws Exception {
    loadProject(TEST_ONLY_MODULE, "test");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);

    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    ApkInfo testApk = apks.stream().filter(a -> a.getApplicationId().equals("com.example.android.app.test"))
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

  public void testOutputModel() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    File apk = mock(File.class);
    ProjectBuildOutputProviderStub outputProvider = new ProjectBuildOutputProviderStub();
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), outputProvider, false);
    outputProvider.setProjectBuildOutput(createMock("debug", apk));
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertSize(1, apks);
    assertEquals(apk, apks.iterator().next().getFile());
  }

  private static ProjectBuildOutput createMock(String variant, File file) {
    ProjectBuildOutput projectBuildOutput = mock(ProjectBuildOutput.class);
    VariantBuildOutput variantBuildOutput = mock(VariantBuildOutput.class);
    OutputFile outputFile = mock(OutputFile.class);
    when(projectBuildOutput.getVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getOutputs()).thenReturn(Collections.singleton(outputFile));
    when(outputFile.getOutputFile()).thenReturn(file);
    return projectBuildOutput;
  }

  private static class ProjectBuildOutputProviderStub implements ProjectBuildOutputProvider {
    @Nullable
    private ProjectBuildOutput myProjectBuildOutput = null;

    void setProjectBuildOutput(@Nullable ProjectBuildOutput projectBuildOutput) {
      myProjectBuildOutput = projectBuildOutput;
    }

    @Nullable
    @Override
    public ProjectBuildOutput getOutputModel() {
      return myProjectBuildOutput;
    }
  }
}
