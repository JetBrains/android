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
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.ProjectBuildOutputProvider;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
/**
 * Tests for {@link GradleApkProvider#getApk(Variant, IDevice, AndroidModuleModel)}.
 */
public class GradleApkProviderGetApkTest extends IdeaTestCase {
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private AndroidModelFeatures myModelFeatures;
  @Mock private Variant myVariant;
  @Mock private BestOutputFinder myBestOutputFinder;
  @Mock private ProjectBuildOutputProvider myOutputModelProvider;
  @Mock private IDevice myDevice;
  @Mock private OutputFile myOutputFile1;
  @Mock private OutputFile myOutputFile2;

  private List<OutputFile> myBestFoundOutput;
  private File myApkFile;
  private GradleApkProvider myApkProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    when(myAndroidModel.getFeatures()).thenReturn(myModelFeatures);

    myApkFile = createTempFile("test", "apk");
    when(myOutputFile1.getOutputFile()).thenReturn(myApkFile);
    myBestFoundOutput = Arrays.asList(myOutputFile1, myOutputFile2);

    AndroidFacet facet = createAndAddAndroidFacet(getModule());
    myApkProvider = new GradleApkProvider(facet, new GradleApplicationIdProvider(facet), myOutputModelProvider,
                                          myBestOutputFinder, true);
  }

  public void testGetApk() throws Exception {
    when(myModelFeatures.isPostBuildSyncSupported()).thenReturn(false);

    AndroidArtifactOutput output = mock(AndroidArtifactOutput.class);
    List<AndroidArtifactOutput> outputs = Collections.singletonList(output);

    AndroidArtifact mainArtifact = mock(AndroidArtifact.class);
    when(mainArtifact.getOutputs()).thenReturn(outputs);

    when(myVariant.getMainArtifact()).thenReturn(mainArtifact);
    when(myBestOutputFinder.findBestOutput(myVariant, myDevice, outputs)).thenReturn(myBestFoundOutput);

    File apk = myApkProvider.getApk(myVariant, myDevice, myAndroidModel);
    assertEquals(myApkFile.getPath(), apk.getPath());

    // Pre 3.0 plugins should not use this.
    verify(myOutputModelProvider, never()).getOutputModel();
  }
}