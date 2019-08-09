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
package com.android.tools.idea.gradle.project.sync.setup.post.module;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Mockito.mock;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Tests for {@link GradleTestArtifactSearchScopeSetupStep}.
 */
public class TestArtifactSearchScopeSetupStepGradleTest extends PlatformTestCase {
  private GradleTestArtifactSearchScopeSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySetupStep = new GradleTestArtifactSearchScopeSetupStep();
  }

  public void testSetUpModuleWithAndroidModule() {
    AndroidFacet facet = createAndAddAndroidFacet(myModule);
    facet.getConfiguration().setModel(mock(AndroidModuleModel.class));

    mySetupStep.setUpModule(myModule, null);
    assertNotNull(GradleTestArtifactSearchScopes.getInstance(myModule));
  }

  public void testSetUpModuleWithNonAndroidModule() {
    mySetupStep.setUpModule(myModule, null);
    assertNull(GradleTestArtifactSearchScopes.getInstance(myModule));
  }
}
