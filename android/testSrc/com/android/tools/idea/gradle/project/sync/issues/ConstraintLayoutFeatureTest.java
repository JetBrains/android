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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link ConstraintLayoutFeature}.
 */
public class ConstraintLayoutFeatureTest extends PlatformTestCase {
  public void testIsSupportedInSdkManagerWithNoModel() {
    // The module does not have a model. This happens when this is a new project, and modules have not been set up yet.
    assertTrue(ConstraintLayoutFeature.isSupportedInSdkManager((AndroidModuleModel)null));
  }

  public void testIsSupportedInSdkManagerWithModelSupportingFeature() {
    AndroidModelFeatures features = mock(AndroidModelFeatures.class);
    when(features.isConstraintLayoutSdkLocationSupported()).thenReturn(true);
    assertTrue(ConstraintLayoutFeature.isSupportedInSdkManager(createAndAddModel(features)));
  }

  public void testIsSupportedInSdkManagerWithModelNotSupportingFeature() {
    AndroidModelFeatures features = mock(AndroidModelFeatures.class);
    when(features.isConstraintLayoutSdkLocationSupported()).thenReturn(false);
    assertFalse(ConstraintLayoutFeature.isSupportedInSdkManager(createAndAddModel(features)));
  }

  @NotNull
  private static AndroidModuleModel createAndAddModel(@NotNull AndroidModelFeatures features) {
    AndroidModuleModel model = mock(AndroidModuleModel.class);
    when(model.getFeatures()).thenReturn(features);
    return model;
  }
}
