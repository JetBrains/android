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
package com.android.tools.idea.gradle.editor;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import java.util.Collection;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.ProjectFiles.*;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GeneratedFileWritingAccessProvider}.
 */
public class GeneratedFileWritingAccessProviderTest extends IdeaTestCase {
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeAndroidProject myAndroidProject;
  @Mock private GradleProjectInfo myProjectInfo;
  @Mock private ProjectFileIndex myProjectFileIndex;

  private VirtualFile myGeneratedFile;
  private VirtualFile myRegularFile;
  private GeneratedFileWritingAccessProvider myAccessProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    VirtualFile buildFolder = createFolderInProjectRoot(project, "build");
    myGeneratedFile = createFile(buildFolder, "test.txt");
    myRegularFile = createFileInProjectRoot(project, "test.txt");

    when(myAndroidModel.getAndroidProject()).thenReturn(myAndroidProject);
    when(myAndroidProject.getBuildFolder()).thenReturn(virtualToIoFile(buildFolder));

    myAccessProvider = new GeneratedFileWritingAccessProvider(myProjectInfo, myProjectFileIndex);
  }

  public void testRequestWritingInAndroidModule() {
    simulateIsAndroidModule();

    Collection<VirtualFile> readOnlyFiles = myAccessProvider.requestWriting(myRegularFile, myGeneratedFile);
    assertThat(readOnlyFiles).containsExactly(myGeneratedFile);

    verifyThatModuleSearchIncludedAllFiles();
  }

  public void testRequestWritingInNonAndroidModule() {
    when(myProjectInfo.findAndroidModelInModule(any(), eq(false))).thenReturn(null);

    Collection<VirtualFile> readOnlyFiles = myAccessProvider.requestWriting(myRegularFile, myGeneratedFile);
    assertThat(readOnlyFiles).isEmpty();

    verifyThatModuleSearchIncludedAllFiles();
  }

  // See https://issuetracker.google.com/65032914
  public void testIsPotentiallyWritableWithGeneratedSourceFile() {
    when(myProjectFileIndex.isInSource(myGeneratedFile)).thenReturn(true);

    assertTrue(myAccessProvider.isPotentiallyWritable(myRegularFile));
    assertTrue(myAccessProvider.isPotentiallyWritable(myGeneratedFile));
  }

  public void testIsPotentiallyWritableInAndroidModule() {
    simulateIsAndroidModule();

    assertTrue(myAccessProvider.isPotentiallyWritable(myRegularFile));
    assertFalse(myAccessProvider.isPotentiallyWritable(myGeneratedFile));

    verifyThatModuleSearchIncludedAllFiles();
  }

  private void simulateIsAndroidModule() {
    AndroidFacet androidFacet = createAndAddAndroidFacet(getModule());
    androidFacet.getConfiguration().setModel(myAndroidModel);
    when(myProjectInfo.findAndroidModelInModule(any(), eq(false))).thenReturn(myAndroidModel);
  }

  public void testIsPotentiallyWritableInNonAndroidModule() {
    assertTrue(myAccessProvider.isPotentiallyWritable(myRegularFile));
    assertTrue(myAccessProvider.isPotentiallyWritable(myGeneratedFile));

    verifyThatModuleSearchIncludedAllFiles();
  }

  private void verifyThatModuleSearchIncludedAllFiles() {
    verify(myProjectInfo).findAndroidModelInModule(myGeneratedFile, false);
    verify(myProjectInfo).findAndroidModelInModule(myRegularFile, false);
  }
}