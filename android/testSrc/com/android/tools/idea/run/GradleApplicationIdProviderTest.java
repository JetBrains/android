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
package com.android.tools.idea.run;

import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ACTIVITY;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GradleApplicationIdProvider}.
 */
public class GradleApplicationIdProviderTest extends AndroidGradleTestCase {
  public void testGetPackageName() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    // See testData/Projects/runConfig/activity/build.gradle
    assertEquals("from.gradle.debug", provider.getPackageName());
    // Without a specific test package name from the Gradle file, we just get a test prefix.
    assertEquals("from.gradle.debug.test", provider.getTestPackageName());
  }

  public void testGetPackageNameForTest() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    // See testData/Projects/runConfig/activity/build.gradle
    assertEquals("from.gradle.debug", provider.getPackageName());
    // Without a specific test package name from the Gradle file, we just get a test prefix.
    assertEquals("from.gradle.debug.test", provider.getTestPackageName());
  }

  public void testGetPackageNameForInstantApps() throws Exception {
    loadProject(INSTANT_APP);
    PostBuildModelProviderStub modelProvider = new PostBuildModelProviderStub();
    AndroidModuleModel androidModel = AndroidModuleModel.get(myAndroidFacet);
    assertNotNull(androidModel);
    modelProvider.setInstantAppProjectBuildOutput(myAndroidFacet,
                                                  createInstantAppProjectBuildOutputMock(androidModel.getSelectedVariant().getName(),
                                                                                         "mockApplicationId"));
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet, modelProvider);

    GradleVersion modelVersion = getModel().getModelVersion();
    if (modelVersion != null && modelVersion.compareIgnoringQualifiers("3.0.0") >= 0) {
      // Instant app post build model is present only in 3.0.0-beta1 or later
      assertEquals("mockApplicationId", provider.getPackageName());
    }
    else {
      // Get the package name declared in the manifest
      assertEquals("com.example.instantapp", provider.getPackageName());
    }
  }

  public void testGetPackageNameForTestOnlyModule() throws Exception {
    loadProject(TEST_ONLY_MODULE, "test");
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    assertEquals("com.example.android.app", provider.getPackageName());
    assertEquals("com.example.android.app.testmodule", provider.getTestPackageName());
  }

  private static InstantAppProjectBuildOutput createInstantAppProjectBuildOutputMock(@NotNull String variant, @NotNull String applicationId) {
    InstantAppProjectBuildOutput projectBuildOutput = mock(InstantAppProjectBuildOutput.class);
    InstantAppVariantBuildOutput variantBuildOutput = mock(InstantAppVariantBuildOutput.class);
    when(projectBuildOutput.getInstantAppVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getApplicationId()).thenReturn(applicationId);
    return projectBuildOutput;
  }

  private static class PostBuildModelProviderStub implements PostBuildModelProvider {
    @NotNull private final PostBuildModel myPostBuildModel = mock(PostBuildModel.class);

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
