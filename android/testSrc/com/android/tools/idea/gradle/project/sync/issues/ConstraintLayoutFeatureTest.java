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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConstraintLayoutFeature}.
 */
public class ConstraintLayoutFeatureTest extends IdeaTestCase {
  public void testIsSupportedInSdkManagerWithNoModel() {
    // The module does not have a model. This happens when this is a new project, and modules have not been set up yet.
    assertTrue(ConstraintLayoutFeature.isSupportedInSdkManager(getModule()));
  }

  public void testIsSupportedInSdkManagerWithModelSupportingFeature() {
    AndroidModelFeatures features = mock(AndroidModelFeatures.class);
    when(features.isConstraintLayoutSdkLocationSupported()).thenReturn(true);
    createAndAddModel(features);

    assertTrue(ConstraintLayoutFeature.isSupportedInSdkManager(getModule()));
  }

  public void testIsSupportedInSdkManagerWithModelNotSupportingFeature() {
    AndroidModelFeatures features = mock(AndroidModelFeatures.class);
    when(features.isConstraintLayoutSdkLocationSupported()).thenReturn(false);
    createAndAddModel(features);

    assertFalse(ConstraintLayoutFeature.isSupportedInSdkManager(getModule()));
  }

  private void createAndAddModel(@NotNull AndroidModelFeatures features) {
    AndroidModuleModel model = mock(AndroidModuleModel.class);
    when(model.getFeatures()).thenReturn(features);
    AndroidFacet facet = createAndAddAndroidFacet(getModule());
    facet.setAndroidModel(model);
  }
}