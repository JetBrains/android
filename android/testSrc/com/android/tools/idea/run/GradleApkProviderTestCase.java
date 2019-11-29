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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public abstract class GradleApkProviderTestCase extends AndroidGradleTestCase {
  protected static class PostBuildModelProviderStub implements PostBuildModelProvider {
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
