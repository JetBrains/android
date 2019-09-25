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

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_TEST;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.testing.TestProjectPaths.BUDDY_APKS;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ACTIVITY;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.build.OutputFile;
import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.AppBundleVariantBuildOutput;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.VariantBuildOutput;
import com.android.ddmlib.IDevice;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

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

  public void testOutputModelForInstantApp() throws Exception {
    // Use a plugin with instant app support
    loadProject(INSTANT_APP, null, null, "3.5.0");
    File apk = mock(File.class);
    PostBuildModelProviderStub outputProvider = new PostBuildModelProviderStub();
    GradleApkProvider provider =
      new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), outputProvider, false);
    outputProvider.setInstantAppProjectBuildOutput(myAndroidFacet, createInstantAppProjectBuildOutputMock("debug", apk));
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertSize(1, apks);
    assertEquals(apk, apks.iterator().next().getFile());
  }

  public void testOutputModelForDynamicApp() throws Exception {
    loadProject(DYNAMIC_APP);

    // Create temporary directory with list of apk files
    File apkFolder = FileUtil.createTempDirectory("apk-output", null);
    createApkFiles(apkFolder, "base-master.apk", "feature1-master.apk", "feature2-master.apk");
    PostBuildModelProviderStub outputProvider = new PostBuildModelProviderStub();
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet),
                                                       outputProvider, false, () -> GradleApkProvider.OutputKind.AppBundleOutputModel);
    outputProvider.setAppBundleProjectBuildOutput(myAndroidFacet, createAppBundleBuildOutputMock("debug", apkFolder));
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertSize(1, apks);
    ApkInfo apkInfo = apks.iterator().next();
    assertThat(apkInfo.getFiles().size()).isEqualTo(3);
    assertThat(apkInfo.getFiles().stream().map(x -> x.getApkFile().getName()).collect(Collectors.toList()))
      .containsExactly("base-master.apk", "feature1-master.apk", "feature2-master.apk");
    assertThat(apkInfo.getFiles().stream().map(x -> x.getModuleName()).collect(Collectors.toList()))
      .containsExactly("base", "feature1", "feature2");
  }

  public void testOutputModelForDynamicFeatureInstrumentedTest() throws Exception {
    loadProject(DYNAMIC_APP, "feature1");
    // Get base-app Android Facet
    Module baseModule = myModules.getAppModule();
    AndroidFacet baseAndroidFacet = AndroidFacet.getInstance(baseModule);

    // Create temporary directory with list of apk files
    File apkFolder = FileUtil.createTempDirectory("apk-output", null);
    createApkFiles(apkFolder, "base-master.apk", "feature1-master.apk", "feature2-master.apk");
    File testApk = new File("feature1-debug-androidTest.apk");

    PostBuildModelProviderStub outputProvider = new PostBuildModelProviderStub();
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet),
                                                       outputProvider, true, () -> GradleApkProvider.OutputKind.AppBundleOutputModel);
    outputProvider.setAppBundleProjectBuildOutput(baseAndroidFacet, createAppBundleBuildOutputMock("debug", apkFolder));
    outputProvider.setProjectBuildOutput(myAndroidFacet, createProjectBuildOutputMock("debug", testApk));
    IDevice iDevice = mock(IDevice.class);
    when(iDevice.getVersion()).thenReturn(new AndroidVersion(27));
    Collection<ApkInfo> apks = provider.getApks(iDevice);
    assertSize(2, apks);
    List<ApkInfo> apkList = new ArrayList<>(apks);
    ApkInfo mainApkInfo = apkList.get(0);
    ApkInfo testApkInfo = apkList.get(1);

    assertThat(mainApkInfo.getFiles().size()).isEqualTo(3);
    assertThat(mainApkInfo.getFiles().stream().map(x -> x.getApkFile().getName()).collect(Collectors.toList()))
      .containsExactly("base-master.apk", "feature1-master.apk", "feature2-master.apk");
    assertThat(mainApkInfo.getFiles().stream().map(x -> x.getModuleName()).collect(Collectors.toList()))
      .containsExactly("base", "feature1", "feature2");

    assertThat(testApkInfo.getFiles().size()).isEqualTo(1);
    assertThat(testApkInfo.getFiles().stream().map(x -> x.getApkFile().getName()).collect(Collectors.toList()))
      .containsExactly("feature1-debug-androidTest.apk");
    assertThat(testApkInfo.getFiles().stream().map(x -> x.getModuleName()).collect(Collectors.toList()))
      .containsExactly("");
  }

  private static void createApkFiles(File folder, String... files) throws IOException {
    for (String fileName : files) {
      File file = folder.toPath().resolve(fileName).toFile();
      file.createNewFile();
    }
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
    GradleApkProvider provider =
      new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), outputProvider, false);
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
    GradleApkProvider provider =
      new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), outputProvider, true);

    assertEquals(PROJECT_TYPE_TEST, myAndroidFacet.getConfiguration().getProjectType());
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
    TestVariantBuildOutput testVariantBuildOutput = mock(TestVariantBuildOutput.class);
    OutputFile outputFile = mock(OutputFile.class);

    when(projectBuildOutput.getVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getOutputs()).thenReturn(Collections.singleton(outputFile));
    when(outputFile.getOutputFile()).thenReturn(file);

    when(variantBuildOutput.getTestingVariants()).thenReturn(Collections.singleton(testVariantBuildOutput));
    when(testVariantBuildOutput.getType()).thenReturn(TestVariantBuildOutput.ANDROID_TEST);
    when(testVariantBuildOutput.getOutputs()).thenReturn(Collections.singleton(outputFile));
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

  private static AppBundleProjectBuildOutput createAppBundleBuildOutputMock(@NotNull String variant, @NotNull File apkFolder) {
    AppBundleProjectBuildOutput projectBuildOutput = mock(AppBundleProjectBuildOutput.class);
    AppBundleVariantBuildOutput variantBuildOutput = mock(AppBundleVariantBuildOutput.class);
    when(projectBuildOutput.getAppBundleVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getApkFolder()).thenReturn(apkFolder);
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

    void setAppBundleProjectBuildOutput(@NotNull AndroidFacet facet, @NotNull AppBundleProjectBuildOutput output) {
      when(myPostBuildModel.findAppBundleProjectBuildOutput(facet)).thenReturn(output);
      when(myPostBuildModel.findAppBundleProjectBuildOutput(facet.getModule())).thenReturn(output);
    }

    @Override
    @NotNull
    public PostBuildModel getPostBuildModel() {
      return myPostBuildModel;
    }
  }
}
