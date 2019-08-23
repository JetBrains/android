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

import com.android.tools.idea.project.AndroidRunConfigurations;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidRunConfigurationSetupStep}.
 */
public class AndroidRunConfigurationSetupStepTest extends PlatformTestCase {
  @Mock private AndroidRunConfigurations myRunConfigurations;

  private AndroidRunConfigurationSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    mySetupStep = new AndroidRunConfigurationSetupStep() {
      @NotNull
      @Override
      protected AndroidRunConfigurations getConfigurations() {
        return myRunConfigurations;
      }
    };
  }

  public void testSetUpModuleWithAndroidFacetAndAppProject() {
    Module module = getModule();
    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    androidFacet.getProperties().PROJECT_TYPE = PROJECT_TYPE_APP;

    mySetupStep.setUpModule(module, null);

    verify(myRunConfigurations).createRunConfiguration(androidFacet);
  }

  public void testSetUpModuleWithAndroidFacetAndLibraryProject() {
    Module module = getModule();
    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    androidFacet.getProperties().PROJECT_TYPE = PROJECT_TYPE_LIBRARY;

    mySetupStep.setUpModule(module, null);

    verify(myRunConfigurations, never()).createRunConfiguration(androidFacet);
  }

  public void testSetUpModuleWithoutAndroidFacet() {
    mySetupStep.setUpModule(getModule(), null);

    verify(myRunConfigurations, never()).createRunConfiguration(any());
  }
}