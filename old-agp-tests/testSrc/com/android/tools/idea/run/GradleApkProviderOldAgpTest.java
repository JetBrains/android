/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.build.OutputFile;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.ddmlib.IDevice;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link GradleApkProvider} that use old version of AGP.
 */
public class GradleApkProviderOldAgpTest extends GradleApkProviderTestCase {
  public void testOutputModelForInstantApp() throws Exception {
    // Use a plugin with instant app support
    loadProject(INSTANT_APP, null, null, "3.5.0");
    File apk = mock(File.class);
    GradleApkProviderTest.PostBuildModelProviderStub outputProvider = new GradleApkProviderTest.PostBuildModelProviderStub();
    GradleApkProvider provider =
      new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), outputProvider, false);
    outputProvider.setInstantAppProjectBuildOutput(myAndroidFacet, createInstantAppProjectBuildOutputMock("debug", apk));
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertSize(1, apks);
    assertEquals(apk, apks.iterator().next().getFile());
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
}
