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
package com.android.tools.idea.gradle.roots;

import static com.android.tools.idea.gradle.roots.AndroidGeneratedSourcesFilter.isGeneratedSource;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.Info;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidGeneratedSourcesFilter}.
 */
public class AndroidGeneratedSourcesFilterTest extends HeavyPlatformTestCase {
  @Mock private Info myInfo;
  private AndroidGeneratedSourcesFilter myGeneratedSourcesFilter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(getProject()).replaceProjectService(Info.class, myInfo);
    myGeneratedSourcesFilter = new AndroidGeneratedSourcesFilter();
  }

  public void testIsGeneratedSourceWithAndroidModelAndFileInsideBuildFolder() throws IOException {
    VirtualFile rootFolder = getRootFolder(getModule());
    VirtualFile buildFolder = createBuildFolder(rootFolder);
    VirtualFile target = createFile(buildFolder, "foo.txt");
    GradleAndroidModel androidModel = createAndroidModel(buildFolder);

    assertTrue(isGeneratedSource(target, getProject(), myInfo, androidModel));
  }

  public void testIsGeneratedSourceWithAndroidModelAndFileOutsideBuildFolder() throws IOException {
    VirtualFile rootFolder = getRootFolder(getModule());
    VirtualFile buildFolder = createBuildFolder(rootFolder);
    VirtualFile target = createFile(rootFolder, "foo.txt");
    GradleAndroidModel androidModel = createAndroidModel(buildFolder);

    assertFalse(isGeneratedSource(target, getProject(), myInfo, androidModel));
  }

  @NotNull
  private static VirtualFile getRootFolder(@NotNull Module module) throws IOException {
    Path dir = module.getModuleNioFile().getParent();
    Files.createDirectories(dir);
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir);
  }

  public void testIsGeneratedSourceWithAndroidModelNotFoundAndFileInsideBuildFolderInGradleProject() throws IOException {
    VirtualFile rootFolder = PlatformTestUtil.getOrCreateProjectBaseDir(getProject());
    VirtualFile buildFolder = createBuildFolder(rootFolder);
    VirtualFile target = createFile(buildFolder, "foo.txt");
    when(myInfo.isBuildWithGradle()).thenReturn(true);  // Project is Gradle project.

    assertTrue(isGeneratedSource(target, getProject(), myInfo, null));
  }

  public void testIsGeneratedSourceWithAndroidModelNotFoundAndFileInsideBuildFolderInNonGradleProject() throws IOException {
    VirtualFile rootFolder = PlatformTestUtil.getOrCreateProjectBaseDir(getProject());
    VirtualFile buildFolder = createBuildFolder(rootFolder);
    VirtualFile target = createFile(buildFolder, "foo.txt");
    when(myInfo.isBuildWithGradle()).thenReturn(false); // Project is not Gradle project

    assertFalse(isGeneratedSource(target, getProject(), myInfo, null));
  }

  @NotNull
  private VirtualFile createBuildFolder(@NotNull VirtualFile parent) throws IOException {
    return ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return parent.createChildDirectory(this, "build");
      }
    });
  }

  public void testIsGeneratedSourceWithAndroidModelNotFoundAndFileOutsideBuildFolderInGradleProject() throws IOException {
    VirtualFile rootFolder = PlatformTestUtil.getOrCreateProjectBaseDir(getProject());
    VirtualFile target = createFile(rootFolder, "foo.txt");
    when(myInfo.isBuildWithGradle()).thenReturn(true);  // Project is Gradle project.

    assertFalse(isGeneratedSource(target, getProject(), myInfo, null));
  }

  @NotNull
  private VirtualFile createFile(@NotNull VirtualFile parent, @NotNull String name) throws IOException {
    return ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return parent.createChildData(this, name);
      }
    });
  }

  @NotNull
  private static GradleAndroidModel createAndroidModel(@NotNull VirtualFile buildFolder) {
    IdeAndroidProject androidProject = mock(IdeAndroidProject.class);
    when(androidProject.getBuildFolder()).thenReturn(virtualToIoFile(buildFolder));

    GradleAndroidModel androidModel = mock(GradleAndroidModel.class);
    when(androidModel.getAndroidProject()).thenReturn(androidProject);
    return androidModel;
  }
}
