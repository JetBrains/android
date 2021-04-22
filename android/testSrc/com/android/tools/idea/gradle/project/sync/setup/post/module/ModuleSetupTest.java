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

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.post.ModuleSetup;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.project.AndroidRunConfigurations;
import com.android.tools.idea.testartifacts.scopes.GradleTestArtifactSearchScopes;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

/**
 * Tests for {@link ModuleSetup}.
 */
public class ModuleSetupTest extends PlatformTestCase {
  @Mock private AndroidRunConfigurations myRunConfigurations;
  @Mock private AndroidModuleModel myAndroidModuleModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    IdeVariant variant = mock(IdeVariant.class);
    when(variant.getName()).thenReturn("MockVariant");
    when(myAndroidModuleModel.getSelectedVariant()).thenReturn(variant);

    new IdeComponents(getProject()).replaceApplicationService(AndroidRunConfigurations.class, myRunConfigurations);
  }

  public void testSetUpModuleWithAndroidFacetAndAppProject() {
    Module module = getModule();
    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    AndroidModel.set(androidFacet, myAndroidModuleModel);
    androidFacet.getProperties().PROJECT_TYPE = PROJECT_TYPE_APP;

    ModuleSetup.setUpModules(getProject());

    verify(myRunConfigurations).createRunConfiguration(androidFacet);
    assertNotNull(GradleTestArtifactSearchScopes.getInstance(getModule()));
  }

  public void testSetUpModuleWithAndroidFacetAndLibraryProject() {
    Module module = getModule();
    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    AndroidModel.set(androidFacet, myAndroidModuleModel);
    androidFacet.getProperties().PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    ModuleSetup.setUpModules(getProject());

    verify(myRunConfigurations, never()).createRunConfiguration(androidFacet);
    assertNotNull(GradleTestArtifactSearchScopes.getInstance(getModule()));
  }

  public void testSetUpModuleWithoutAndroidFacet() {
    ModuleSetup.setUpModules(getProject());

    verify(myRunConfigurations, never()).createRunConfiguration(any());
    assertNull(GradleTestArtifactSearchScopes.getInstance(getModule()));
  }
}
