/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.extensions;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_DECLARATIVE;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_DECLARATIVE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiManagerEx;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public class GradleFilesTest extends AndroidGradleTestCase {
  private GradleFiles myGradleFiles;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myGradleFiles = GradleFiles.getInstance(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    myGradleFiles = null;
    super.tearDown();
  }

  public void testIsGradleFileWithBuildDotGradleFile() {
    PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder(FN_BUILD_GRADLE);
    assertThat(myGradleFiles.isGradleFile(psiFile)).isTrue();
  }

  public void testIsGradleFileWithGradleDotPropertiesFile() {
    PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES);
    assertThat(myGradleFiles.isGradleFile(psiFile)).isTrue();
  }

  public void testIsGradleFileWithWrapperPropertiesFile() throws IOException {
    Project project = getProject();
    GradleWrapper wrapper = GradleWrapper.create(getBaseDirPath(project), project);
    VirtualFile propertiesFile = wrapper.getPropertiesFile();
    PsiFile psiFile = findPsiFile(propertiesFile);
    assertThat(myGradleFiles.isGradleFile(psiFile)).isTrue();
  }

  public void testIsGradleFileWithKotlinSettings() {
    // We need to create a file with EventSystemEnabled == false to get the PsiFile to return a null virtual file.
    PsiFile psiFile = PsiFileFactory.getInstance(getProject())
      .createFileFromText(FN_SETTINGS_GRADLE_KTS, FileTypeManager.getInstance().getStdFileType("Kotlin"), "", 0L, false);
    assertThat(myGradleFiles.isGradleFile(psiFile)).isTrue();
  }

  public void testIsGradleFileWithRenamedKts() {
    PsiFile psiFile = PsiFileFactory.getInstance(getProject())
      .createFileFromText("app.gradle.kts", FileTypeManager.getInstance().getStdFileType("Kotlin"), "", 0L, false);
    assertThat(myGradleFiles.isGradleFile(psiFile)).isTrue();
  }

  public void testIsGradleFileWithDeclarativeGradleFile() {
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true);
    try {
      PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder(FN_BUILD_GRADLE_DECLARATIVE);
      assertThat(myGradleFiles.isGradleFile(psiFile)).isTrue();
    }
    finally {
      StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride();
    }
  }

  public void testIsGradleFileWithDeclarativeSettingsFile() {
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true);
    try {
      PsiFile psiFile = PsiFileFactory.getInstance(getProject())
        .createFileFromText(FN_SETTINGS_GRADLE_DECLARATIVE, FileTypeManager.getInstance().getStdFileType(""), "", 0L, false);
      assertThat(myGradleFiles.isGradleFile(psiFile)).isTrue();
    }
    finally {
      StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride();
    }
  }

  public void testIsGradleFileWithVersionsToml() {
    PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder("gradle", "libs.versions.toml");
    assertThat(myGradleFiles.isGradleFile(psiFile)).isTrue();
  }

  public void testNothingInDefaultProject() {
    /* Prior to fix this would throw
    ERROR: Assertion failed: Please don't register startup activities for the default project: they won't ever be run
    java.lang.Throwable: Assertion failed: Please don't register startup activities for the default project: they won't ever be run
    at com.intellij.openapi.diagnostic.Logger.assertTrue(Logger.java:174)
    at com.intellij.ide.startup.impl.StartupManagerImpl.checkNonDefaultProject(StartupManagerImpl.java:80)
    at com.intellij.ide.startup.impl.StartupManagerImpl.registerPostStartupActivity(StartupManagerImpl.java:99)
    at com.android.tools.idea.gradle.project.sync.GradleFiles.<init>(GradleFiles.java:84)
     */

    // Default projects are initialized during the IDE build for example to generate the searchable index.
    GradleFiles gradleFiles = GradleFiles.getInstance(ProjectManager.getInstance().getDefaultProject());
    PsiFile psiFile = findOrCreatePsiFileRelativeToProjectRootFolder(FN_GRADLE_PROPERTIES); // not in the default project
    assertThat(gradleFiles.isGradleFile(psiFile)).isTrue();
  }

  @NotNull
  private PsiFile findOrCreatePsiFileRelativeToProjectRootFolder(@NotNull String... names) {
    VirtualFile file = findOrCreateFileRelativeToProjectRootFolder(names);
    return findPsiFile(file);
  }

  @NotNull
  private PsiFile findPsiFile(@NotNull VirtualFile file) {
    PsiFile psiFile = PsiManagerEx.getInstanceEx(getProject()).findFile(file);
    assertNotNull(psiFile);
    return psiFile;
  }

  @NotNull
  private VirtualFile findOrCreateFileRelativeToProjectRootFolder(@NotNull String... names) {
    File filePath = findOrCreateFilePathRelativeToProjectRootFolder(names);
    VirtualFile file = findFileByIoFile(filePath, true);
    assertNotNull(file);
    return file;
  }

  private @NotNull File findOrCreateFilePathRelativeToProjectRootFolder(@NotNull String... names) {
    File parent = getBaseDirPath(getProject());
    for (int i = 0; i < names.length - 1; i++) {
      File child = new File(parent, names[i]);
      if (!child.exists()) {
        assertTrue(child.mkdirs());
      }
      parent = child;
    }
    File result = new File(parent, names[names.length - 1]);
    assertTrue(createIfNotExists(result));
    return result;
  }
}
