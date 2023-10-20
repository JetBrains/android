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
package com.android.tools.idea.gradle.project;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Tests for {@link GradleProjectInfo}.
 */
public class GradleProjectInfoTest extends PlatformTestCase {
  private GradleProjectInfo myProjectInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectInfo = GradleProjectInfo.getInstance(getProject());
  }

  public void testGetAndroidModulesUsingGradleProject() {
    // Simulate this is a module built with Gradle
    ApplicationManager.getApplication().runWriteAction(() -> {
      FacetManager facetManager = FacetManager.getInstance(getModule());
      facetManager.addFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName(), null);
      facetManager.addFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
    });

    assertThat(myProjectInfo.getAndroidModules()).hasSize(1);
    assertEquals(myProjectInfo.getAndroidModules().get(0), getModule());
  }

  public void testGetAndroidModulesUsingNonGradleProject() {
    // Ensure this module is *not* build by Gradle.
    removeGradleFacetFromModule();

    assertEmpty(myProjectInfo.getAndroidModules());
  }

  public void testGetAndroidModulesUsingGradleProjectWithoutGradleModules() {
    // Ensure this module is *not* build by Gradle.
    removeGradleFacetFromModule();

    registerLastSyncTimestamp(1L);

    assertEmpty(myProjectInfo.getAndroidModules());
  }

  private void removeGradleFacetFromModule() {
    FacetManager facetManager = FacetManager.getInstance(getModule());
    GradleFacet facet = facetManager.findFacet(GradleFacet.getFacetTypeId(), GradleFacet.getFacetName());
    if (facet != null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        ModifiableFacetModel facetModel = facetManager.createModifiableModel();
        facetModel.removeFacet(facet);
        facetModel.commit();
      });
    }
  }

  private void registerLastSyncTimestamp(long timestamp) {
    GradleSyncState syncState = mock(GradleSyncState.class);
    new IdeComponents(getProject()).replaceProjectService(GradleSyncState.class, syncState);
    when(syncState.getLastSyncFinishedTimeStamp()).thenReturn(timestamp);
  }
}
