/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static java.util.Collections.emptyList;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link GradleUtil}.
 */
public class GradleUtilIdeaTest extends PlatformTestCase {
  private File myModuleRootDir;
  private File myBuildFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File moduleFilePath = new File(myModule.getModuleFilePath());
    myModuleRootDir = moduleFilePath.getParentFile();
    myBuildFile = new File(myModuleRootDir, FN_BUILD_GRADLE);
    createIfNotExists(myBuildFile);
    // Ensure that the tests and see the file in the virtual file system.
    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(myBuildFile));
  }

  public void testGetGradleBuildFileFromRootDir() {
    VirtualFile buildFile = GradleUtil.getGradleBuildFile(myModuleRootDir);
    assertIsGradleBuildFile(buildFile);
  }

  public void testGetGradleBuildFileFromModuleWithoutGradleFacet() {
    VirtualFile buildFile = GradleUtil.getGradleBuildFile(myModule);
    assertIsGradleBuildFile(buildFile);
  }

  public void testGetGradleBuildFileFromModuleWithGradleFacet() {
    String name = myModuleRootDir.getName();
    GradleProjectStub gradleProject = new GradleProjectStub(name, ":" + name, getBaseDirPath(getProject()), myBuildFile);

    GradleModuleModel gradleModuleModel =
      new GradleModuleModel(myModule.getName(), gradleProject, false, false, myBuildFile, "2.2.1", null);

    GradleFacet facet = createAndAddGradleFacet(myModule);
    facet.setGradleModuleModel(gradleModuleModel);

    VirtualFile buildFile = GradleUtil.getGradleBuildFile(myModule);
    assertIsGradleBuildFile(buildFile);
  }

  private static void assertIsGradleBuildFile(@Nullable VirtualFile buildFile) {
    assertNotNull(buildFile);
    assertFalse(buildFile.isDirectory());
    assertTrue(buildFile.isValid());
    assertEquals(FN_BUILD_GRADLE, buildFile.getName());
  }
}
