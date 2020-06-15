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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;

/**
 * Tests for {@link GradleApplicationIdProvider} that use old versions of AGP.
 */
public class GradleApplicationIdProviderOldAgpTest extends GradleApplicationIdProviderTestCase {
  public void testGetPackageNameForInstantApps() throws Exception {
    // Use a plugin with instant app support
    loadProject(INSTANT_APP, null, null, "3.5.0");
    GradleApplicationIdProviderTest.PostBuildModelProviderStub
      modelProvider = new GradleApplicationIdProviderTest.PostBuildModelProviderStub();
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
}
