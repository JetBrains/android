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

import static com.android.tools.idea.gradle.project.sync.issues.UnresolvedDependenciesReporter.retrieveDependency;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UnresolvedDependenciesReporter}.
 */
public class UnresolvedDependenciesReporterTest extends IdeaTestCase {
  public void testCanGetConstraintLayoutFromSdkManagerWithNoModel() {
    // The module does not have a model. This happens when this is a new project, and modules have not been set up yet.
    assertTrue(UnresolvedDependenciesReporter.canGetConstraintLayoutFromSdkManager(getModule()));
  }

  public void testCanGetConstraintLayoutFromSdkManagerWithModelSupportingConstraintLayoutInSdk() {
    AndroidModelFeatures features = mock(AndroidModelFeatures.class);
    when(features.isConstraintLayoutSdkLocationSupported()).thenReturn(true);
    createAndAddModel(features);

    assertTrue(UnresolvedDependenciesReporter.canGetConstraintLayoutFromSdkManager(getModule()));
  }

  public void testCanGetConstraintLayoutFromSdkManagerWithModelNotSupportingConstraintLayoutInSdk() {
    AndroidModelFeatures features = mock(AndroidModelFeatures.class);
    when(features.isConstraintLayoutSdkLocationSupported()).thenReturn(false);
    createAndAddModel(features);

    assertFalse(UnresolvedDependenciesReporter.canGetConstraintLayoutFromSdkManager(getModule()));
  }

  public void testRetrieveDependency() {
    String expectedDependency = "com.android.support.constraint:constraint-layout:+";
    assertEquals(expectedDependency,
                 retrieveDependency("any matches for com.android.support.constraint:constraint-layout:+" +
                                    " as no versions of com.android.support.constraint:constraint-layout are available"));
    assertEquals(expectedDependency,
                 retrieveDependency("com.android.support.constraint:constraint-layout:+"));

    assertEquals(expectedDependency,
                 retrieveDependency("Could not find any matches for com.android.support.constraint:constraint-layout:+" +
                                    " as no versions of com.android.support.constraint:constraint-layout are available.\r\n" +
                                    "Searched in the following locations:\r\n"));
    assertEquals(expectedDependency,
                 retrieveDependency("Could not find any matches for com.android.support.constraint:constraint-layout:+" +
                                    " as no versions of com.android.support.constraint:constraint-layout are available.\n" +
                                    "Searched in the following locations:\n"));
  }

  private void createAndAddModel(@NotNull AndroidModelFeatures features) {
    AndroidModuleModel model = mock(AndroidModuleModel.class);
    when(model.getFeatures()).thenReturn(features);
    AndroidFacet facet = createAndAddAndroidFacet(getModule());
    facet.setAndroidModel(model);
  }
}