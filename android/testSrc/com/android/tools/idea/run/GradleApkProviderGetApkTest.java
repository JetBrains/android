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

import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.ddmlib.IDevice;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.google.common.collect.Lists;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
/**
 * Test methods of {@link GradleApkProvider}.
 */
public class GradleApkProviderGetApkTest extends IdeaTestCase {
  private AndroidFacet myAndroidFacet;
  @Mock private AndroidModelFeatures myModelFeatures;
  @Mock private IdeVariant myVariant;
  @Mock private PostBuildModelProvider myOutputModelProvider;
  @Mock private PostBuildModel myPostBuildModel;
  @Mock private IDevice myDevice;
  @Mock private File myApkFile;
  @Mock private File myTestApkFile;

  private GradleApkProvider myApkProvider;
  private AndroidFacetConfiguration myConfiguration;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    BestOutputFinder bestOutputFinder = mock(BestOutputFinder.class);
    OutputFile outputFile1 = mock(OutputFile.class);
    OutputFile outputFile2 = mock(OutputFile.class);
    OutputFile testOutputFile1 = mock(OutputFile.class);
    OutputFile testOutputFile2 = mock(OutputFile.class);

    when(myVariant.getName()).thenReturn("myVariant");

    when(outputFile1.getOutputFile()).thenReturn(myApkFile);
    when(testOutputFile1.getOutputFile()).thenReturn(myTestApkFile);

    List<OutputFile> bestFoundOutput = Arrays.asList(outputFile1, outputFile2);
    List<OutputFile> testBestFoundOutput = Arrays.asList(testOutputFile1, testOutputFile2);

    when(androidModel.getFeatures()).thenReturn(myModelFeatures);

    myConfiguration = new AndroidFacetConfiguration();
    myAndroidFacet = new AndroidFacet(myModule, AndroidFacet.NAME, myConfiguration);
    myConfiguration.setModel(androidModel);

    List<AndroidArtifactOutput> mainOutputs = Lists.newArrayList(mock(AndroidArtifactOutput.class));
    List<AndroidArtifactOutput> testOutputs = Lists.newArrayList(mock(AndroidArtifactOutput.class));
    List<OutputFile> mainOutputs2 = Lists.transform(mainOutputs, input -> (OutputFile)input);
    List<OutputFile> testOutputs2 = Lists.transform(testOutputs, input -> (OutputFile)input);

    IdeAndroidArtifact mainArtifact = mock(IdeAndroidArtifact.class);
    IdeAndroidArtifact testArtifact = mock(IdeAndroidArtifact.class);
    when(mainArtifact.getOutputs()).thenReturn(mainOutputs);
    when(testArtifact.getOutputs()).thenReturn(testOutputs);

    when(myVariant.getMainArtifact()).thenReturn(mainArtifact);
    when(myVariant.getAndroidTestArtifact()).thenReturn(testArtifact);

    when(bestOutputFinder.findBestOutput(myVariant, myDevice, mainOutputs)).thenReturn(bestFoundOutput);
    when(bestOutputFinder.findBestOutput(myVariant, myDevice, mainOutputs2)).thenReturn(bestFoundOutput);
    when(bestOutputFinder.findBestOutput(myVariant, myDevice, testOutputs)).thenReturn(testBestFoundOutput);
    when(bestOutputFinder.findBestOutput(myVariant, myDevice, testOutputs2)).thenReturn(testBestFoundOutput);

    myApkProvider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), myOutputModelProvider,
                                          bestOutputFinder, true);

    when(myOutputModelProvider.getPostBuildModel()).thenReturn(myPostBuildModel);

    setUpProjectBuildOutputProvider(myAndroidFacet, myVariant.getName(), mainOutputs2, testOutputs2);
  }

  public void testGetApkWithoutModelProvider() throws Exception {
    when(myModelFeatures.isPostBuildSyncSupported()).thenReturn(false);

    File apk = myApkProvider.getApk(myVariant, myDevice, myAndroidFacet, false);
    assertEquals(myApkFile, apk);

    // Pre 3.0 plugins should not use this.
    verify(myOutputModelProvider, never()).getPostBuildModel();
  }

  public void testGetApkWithModelProvider() throws Exception {
    when(myModelFeatures.isPostBuildSyncSupported()).thenReturn(true);

    File apk = myApkProvider.getApk(myVariant, myDevice, myAndroidFacet, false);
    assertEquals(myApkFile, apk);

    // Post 3.0 plugins should use this.
    verify(myOutputModelProvider, atLeastOnce()).getPostBuildModel();
  }

  public void testGetApkFromPreSyncBuild() throws Exception {
    File apk = myApkProvider.getApkFromPreBuildSync(myVariant, myDevice, false);
    assertEquals(myApkFile, apk);
  }

  public void testGetApkFromPreSyncBuildForTests() throws Exception {
    File apk = myApkProvider.getApkFromPreBuildSync(myVariant, myDevice, true);
    assertEquals(myTestApkFile, apk);
  }

  public void testGetApkFromPostBuild() throws Exception {
    File apk = myApkProvider.getApkFromPostBuildSync(myVariant, myDevice, myAndroidFacet, false);
    assertEquals(myApkFile, apk);
  }

  public void testGetApkFromPostBuildForTests() throws Exception {
    File apk = myApkProvider.getApkFromPostBuildSync(myVariant, myDevice, myAndroidFacet, true);
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