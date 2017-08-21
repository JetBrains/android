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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.settings.AndroidStudioGradleProjectSettings;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.android.tools.idea.testing.ProjectFiles.createFileInProjectRoot;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GradleProjectInfo}.
 */
public class GradleProjectInfoTest extends IdeaTestCase {
  private GradleProjectInfo myProjectInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectInfo = GradleProjectInfo.getInstance(getProject());
  }

  public void testCanUseLocalMavenRepoWithEnabledRepo() {
    AndroidStudioGradleProjectSettings.getInstance(getProject()).DISABLE_EMBEDDED_MAVEN_REPO = false;
    assertTrue(myProjectInfo.canUseLocalMavenRepo());
  }

  public void testCanUseLocalMavenRepoWithDisabledRepo() {
    AndroidStudioGradleProjectSettings.getInstance(getProject()).DISABLE_EMBEDDED_MAVEN_REPO = true;
    assertFalse(myProjectInfo.canUseLocalMavenRepo());
  }

  public void testHasTopLevelGradleBuildFileUsingGradleProject() {
    File projectFolderPath = getBaseDirPath(getProject());
    File buildFilePath = new File(projectFolderPath, "build.gradle");
    assertTrue("Failed to create top-level build.gradle file", createIfNotExists(buildFilePath));

    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(buildFilePath));
    assertTrue(myProjectInfo.hasTopLevelGradleBuildFile());
  }

  public void testHasTopLevelGradleBuildFileUsingNonGradleProject() {
    File projectFolderPath = getBaseDirPath(getProject());
    File buildFilePath = new File(projectFolderPath, "build.gradle");
    if (buildFilePath.exists()) {
      assertTrue("Failed to delete top-level build.gradle file", buildFilePath.delete());
    }

    assertFalse(myProjectInfo.hasTopLevelGradleBuildFile());
  }

  public void testIsBuildWithGradleUsingGradleProject() {
    // Simulate this is a module built with Gradle
    createAndAddGradleFacet(getModule());
    assertTrue(myProjectInfo.isBuildWithGradle());
  }

  public void testIsBuildWithGradleUsingNonGradleProject() {
    // Ensure this module is *not* build by Gradle.
    removeGradleFacetFromModule();

    assertFalse(myProjectInfo.isBuildWithGradle());
  }

  // See https://code.google.com/p/android/issues/detail?id=203384
  public void testIsBuildWithGradleUsingGradleProjectWithoutGradleModules() {
    // Ensure this module is *not* build by Gradle.
    removeGradleFacetFromModule();

    registerLastSyncTimestamp(1L);

    assertTrue(myProjectInfo.isBuildWithGradle());
  }

  // See https://code.google.com/p/android/issues/detail?id=203384
  public void testIsBuildWithGradleUsingProjectWithoutSyncTimestamp() {
    // Ensure this module is *not* build by Gradle.
    removeGradleFacetFromModule();

    registerLastSyncTimestamp(-1L);

    assertFalse(myProjectInfo.isBuildWithGradle());
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
    GradleSyncSummary summary = mock(GradleSyncSummary.class);
    when(summary.getSyncTimestamp()).thenReturn(timestamp);

    GradleSyncState syncState = mock(GradleSyncState.class);
    IdeComponents.replaceService(getProject(), GradleSyncState.class, syncState);
    when(syncState.getSummary()).thenReturn(summary);
  }

  public void testInvokesIndexHonoringExclusion() throws IOException {
    Module module = getModule();
    AndroidFacet facet = createAndAddAndroidFacet(module);

    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    facet.setAndroidModel(androidModel);

    ProjectFileIndex projectFileIndex = mock(ProjectFileIndex.class);
    Project project = getProject();
    myProjectInfo = new GradleProjectInfo(project, mock(AndroidProjectInfo.class), mock(AndroidStudioGradleProjectSettings.class),
                                          projectFileIndex);

    VirtualFile excludedFile = createFileInProjectRoot(project, "something.txt");

    when(projectFileIndex.getModuleForFile(excludedFile, true)).thenReturn(null);
    when(projectFileIndex.getModuleForFile(excludedFile, false)).thenReturn(module);

    AndroidModuleModel found = myProjectInfo.findAndroidModelInModule(excludedFile);
    assertNull(found);
    verify(projectFileIndex).getModuleForFile(excludedFile, true);

    found = myProjectInfo.findAndroidModelInModule(excludedFile, false);
    assertSame(androidModel, found);
    verify(projectFileIndex).getModuleForFile(excludedFile, false);
  }
}