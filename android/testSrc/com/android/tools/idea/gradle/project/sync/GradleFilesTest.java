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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleFiles}.
 */
public class GradleFilesTest extends AndroidGradleTestCase {
  @Mock private FileDocumentManager myDocumentManager;

  private GradleFiles myGradleFiles;
  private long myLastSyncTime;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myLastSyncTime = System.currentTimeMillis() - MINUTES.toMillis(2);
    myGradleFiles = new GradleFiles(getProject(), myDocumentManager);
  }

  @Override
  protected void tearDown() throws Exception {
    myGradleFiles = null;
    super.tearDown();
  }

  public void testAreGradleFilesModifiedWithNegativeReferenceTime() {
    try {
      myGradleFiles.areGradleFilesModified(-1);
      fail();
    }
    catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testAreGradleFilesModifiedWithReferenceTimeEqualToZero() {
    try {
      myGradleFiles.areGradleFilesModified(0);
      fail();
    }
    catch (IllegalArgumentException expected) {
      // expected
    }
  }

  public void testAreGradleFilesModifiedWithModifiedWrapperPropertiesFile() throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(getProject()));
    VirtualFile propertiesFile = wrapper.getPropertiesFile();
    simulateUnsavedChanges(propertiesFile);

    boolean filesModified = myGradleFiles.areGradleFilesModified(myLastSyncTime);
    assertTrue(filesModified);
  }

  public void testAreGradleFilesModifiedWithModifiedSettingsDotGradleFile() {
    findOrCreateFilePathInProjectRootFolder(FN_SETTINGS_GRADLE);
    boolean filesModified = myGradleFiles.areGradleFilesModified(myLastSyncTime);
    assertTrue(filesModified);
  }

  public void testAreGradleFilesModifiedWithUnmodifiedChangesInSettingsDotGradleFile() {
    VirtualFile file = findOrCreateFileInProjectRootFolder(FN_SETTINGS_GRADLE);
    simulateUnsavedChanges(file);

    boolean filesModified = myGradleFiles.areGradleFilesModified(myLastSyncTime);
    assertTrue(filesModified);
  }

  public void testAreGradleFilesModifiedWithModifiedGradleDotPropertiesFile() {
    findOrCreateFilePathInProjectRootFolder(FN_GRADLE_PROPERTIES);
    boolean filesModified = myGradleFiles.areGradleFilesModified(myLastSyncTime);
    assertTrue(filesModified);
  }

  public void testAreGradleFilesModifiedWithUnmodifiedChangesInGradleDotPropertiesFile() {
    VirtualFile file = findOrCreateFileInProjectRootFolder(FN_GRADLE_PROPERTIES);
    simulateUnsavedChanges(file);

    boolean filesModified = myGradleFiles.areGradleFilesModified(myLastSyncTime);
    assertTrue(filesModified);
  }

  public void testAreGradleFilesModifiedWithModifiedBuildDotGradleFile() throws Exception {
    loadSimpleApplication();

    VirtualFile buildFile = getAppBuildFile();
    File buildFilePath = virtualToIoFile(buildFile);
    modifyFile(buildFilePath);

    boolean filesModified = myGradleFiles.areGradleFilesModified(myLastSyncTime);
    assertTrue(filesModified);
  }

  private static void modifyFile(@NotNull File filePath) throws IOException {
    appendToFile(filePath, SystemProperties.getLineSeparator());
  }

  public void testAreGradleFilesModifiedWithUnmodifiedChangesInBuildDotGradleFile() throws Exception {
    loadSimpleApplication();

    VirtualFile buildFile = getAppBuildFile();
    simulateUnsavedChanges(buildFile);

    boolean filesModified = myGradleFiles.areGradleFilesModified(myLastSyncTime);
    assertTrue(filesModified);
  }

  @NotNull
  private VirtualFile getAppBuildFile() {
    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertNotNull(buildFile);
    return buildFile;
  }

  public void testIsGradleFileWithBuildDotGradleFile() {
    PsiFile psiFile = findOrCreatePsiFileInProjectRootFolder(FN_BUILD_GRADLE);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithGradleDotPropertiesFile() {
    PsiFile psiFile = findOrCreatePsiFileInProjectRootFolder(FN_GRADLE_PROPERTIES);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  public void testIsGradleFileWithWrapperPropertiesFile() throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(getProject()));
    VirtualFile propertiesFile = wrapper.getPropertiesFile();
    PsiFile psiFile = findPsiFile(propertiesFile);
    assertTrue(myGradleFiles.isGradleFile(psiFile));
  }

  @NotNull
  private PsiFile findOrCreatePsiFileInProjectRootFolder(@NotNull String fileName) {
    VirtualFile file = findOrCreateFileInProjectRootFolder(fileName);
    return findPsiFile(file);
  }

  @NotNull
  private PsiFile findPsiFile(@NotNull VirtualFile file) {
    PsiFile psiFile = PsiManagerEx.getInstanceEx(getProject()).findFile(file);
    assertNotNull(psiFile);
    return psiFile;
  }

  @NotNull
  private VirtualFile findOrCreateFileInProjectRootFolder(@NotNull String fileName) {
    File filePath = findOrCreateFilePathInProjectRootFolder(fileName);
    VirtualFile file = findFileByIoFile(filePath, true);
    assertNotNull(file);
    return file;
  }

  @NotNull
  private File findOrCreateFilePathInProjectRootFolder(@NotNull String fileName) {
    File filePath = new File(getBaseDirPath(getProject()), fileName);
    assertTrue(createIfNotExists(filePath));
    return filePath;
  }

  private void simulateUnsavedChanges(@NotNull VirtualFile file) {
    when(myDocumentManager.isFileModified(file)).thenReturn(true);
  }
}