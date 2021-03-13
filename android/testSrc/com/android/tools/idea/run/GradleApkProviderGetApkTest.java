/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.build.OutputFile;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.ModelCache;
import com.android.ide.common.gradle.model.stubs.AndroidArtifactOutputStub;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Test methods of {@link GradleApkProvider}.
 */
public class GradleApkProviderGetApkTest extends PlatformTestCase {
  private AndroidFacet myAndroidFacet;
  @Mock private AndroidModelFeatures myModelFeatures;
  @Mock private IdeVariant myVariant;
  @Mock private PostBuildModelProvider myOutputModelProvider;
  @Mock private PostBuildModel myPostBuildModel;
  @Mock private File myApkFile;
  @Mock private File myTestApkFile;

  private GradleApkProvider myApkProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    BestOutputFinder bestOutputFinder = mock(BestOutputFinder.class);

    when(myVariant.getName()).thenReturn("myVariant");
    when(androidModel.getFeatures()).thenReturn(myModelFeatures);

    AndroidFacetConfiguration configuration = new AndroidFacetConfiguration();
    myAndroidFacet = new AndroidFacet(myModule, AndroidFacet.NAME, configuration);
    AndroidModel.set(myAndroidFacet, androidModel);

    ModelCache modelCache = ModelCache.create();
    List<OutputFile> mainOutputs2 = ImmutableList.of(new AndroidArtifactOutputStub("main", myApkFile));
    List<OutputFile> testOutputs2 = ImmutableList.of(new AndroidArtifactOutputStub("test", myTestApkFile));
    List<IdeAndroidArtifactOutput> mainOutputs = Lists.transform(mainOutputs2, modelCache::androidArtifactOutputFrom);
    List<IdeAndroidArtifactOutput> testOutputs = Lists.transform(testOutputs2, modelCache::androidArtifactOutputFrom);

    IdeAndroidArtifact mainArtifact = mock(IdeAndroidArtifact.class);
    IdeAndroidArtifact testArtifact = mock(IdeAndroidArtifact.class);
    when(mainArtifact.getOutputs()).thenReturn(mainOutputs);
    when(testArtifact.getOutputs()).thenReturn(testOutputs);

    when(myVariant.getMainArtifact()).thenReturn(mainArtifact);
    when(myVariant.getAndroidTestArtifact()).thenReturn(testArtifact);

    when(bestOutputFinder.findBestOutput(myVariant, emptyList(), mainOutputs)).thenReturn(myApkFile);
    when(bestOutputFinder.findBestOutput(myVariant, emptyList(), testOutputs)).thenReturn(myTestApkFile);

    myApkProvider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), myOutputModelProvider,
                                          bestOutputFinder, true, it -> GradleApkProvider.OutputKind.Default);

    when(myOutputModelProvider.getPostBuildModel()).thenReturn(myPostBuildModel);

    setUpProjectBuildOutputProvider(myAndroidFacet, myVariant.getName(), mainOutputs2, testOutputs2);
  }

  public void testGetApkWithoutModelProvider() throws Exception {
    when(myModelFeatures.isPostBuildSyncSupported()).thenReturn(false);

    File apk = myApkProvider.getApk(myVariant, emptyList(), new AndroidVersion(27), myAndroidFacet, false);
    assertEquals(myApkFile, apk);

    // Pre 3.0 plugins should not use this.
    verify(myOutputModelProvider, never()).getPostBuildModel();
  }

  public void testGetApkWithModelProvider() throws Exception {
    when(myModelFeatures.isPostBuildSyncSupported()).thenReturn(true);

    File apk = myApkProvider.getApk(myVariant, emptyList(), new AndroidVersion(27), myAndroidFacet, false);
    assertEquals(myApkFile, apk);

    // Post 3.0 plugins should use this.
    verify(myOutputModelProvider, atLeastOnce()).getPostBuildModel();
  }

  public void testGetApkFromPreSyncBuild() throws Exception {
    File apk = myApkProvider.getApkFromPreBuildSync(myVariant, emptyList(), false);
    assertEquals(myApkFile, apk);
  }

  public void testGetApkFromPreSyncBuildForTests() throws Exception {
    File apk = myApkProvider.getApkFromPreBuildSync(myVariant, emptyList(), true);
    assertEquals(myTestApkFile, apk);
  }

  public void testGetApkFromPostBuild() throws Exception {
    File apk = myApkProvider.getApkFromPostBuildSync(myVariant, emptyList(), new AndroidVersion(27), myAndroidFacet, false);
    assertEquals(myApkFile, apk);
  }

  public void testGetApkFromPostBuildForTests() throws Exception {
    File apk = myApkProvider.getApkFromPostBuildSync(myVariant, emptyList(), new AndroidVersion(27), myAndroidFacet, true);
    assertEquals(myTestApkFile, apk);
  }

  private void setUpProjectBuildOutputProvider(@NotNull AndroidFacet facet,
                                               @NotNull String variantName,
                                               @NotNull Collection<OutputFile> mainOutputs,
                                               @NotNull Collection<OutputFile> testOutputs) {

    TestVariantBuildOutput testVariantBuildOutput = mock(TestVariantBuildOutput.class);
    when(testVariantBuildOutput.getType()).thenReturn(TestVariantBuildOutput.ANDROID_TEST);
    when(testVariantBuildOutput.getOutputs()).thenReturn(testOutputs);

    VariantBuildOutput variantBuildOutput = mock(VariantBuildOutput.class);
    when(variantBuildOutput.getTestingVariants()).thenReturn(Collections.singleton(testVariantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variantName);
    when(variantBuildOutput.getOutputs()).thenReturn(mainOutputs);

    ProjectBuildOutput projectBuildOutput = mock(ProjectBuildOutput.class);
    when(projectBuildOutput.getVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));

    when(myPostBuildModel.findProjectBuildOutput(facet)).thenReturn(projectBuildOutput);
  }
}
