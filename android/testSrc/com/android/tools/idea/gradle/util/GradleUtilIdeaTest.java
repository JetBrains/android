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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.easymock.EasyMock.*;

/**
 * Tests for {@link GradleUtil}.
 */
public class GradleUtilIdeaTest extends IdeaTestCase {
  private File myModuleRootDir;
  private File myBuildFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File moduleFilePath = new File(myModule.getModuleFilePath());
    myModuleRootDir = moduleFilePath.getParentFile();
    myBuildFile = new File(myModuleRootDir, FN_BUILD_GRADLE);
    createIfNotExists(myBuildFile);
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
    GradleProject project = createMock(GradleProject.class);
    expect(project.getPath()).andReturn(myModule.getName());

    //noinspection unchecked
    DomainObjectSet<? extends GradleTask> tasks = createMock(DomainObjectSet.class);
    project.getTasks();
    expectLastCall().andReturn(tasks);

    expect(tasks.isEmpty()).andReturn(true);
    replay(project, tasks);

    GradleModuleModel gradleModuleModel = new GradleModuleModel(myModule.getName(), project, myBuildFile, "2.2.1");

    GradleFacet facet = createAndAddGradleFacet(myModule);
    facet.setGradleModuleModel(gradleModuleModel);

    VirtualFile buildFile = GradleUtil.getGradleBuildFile(myModule);
    assertIsGradleBuildFile(buildFile);

    verify(project, tasks);
  }

  private static void assertIsGradleBuildFile(@Nullable VirtualFile buildFile) {
    assertNotNull(buildFile);
    assertFalse(buildFile.isDirectory());
    assertTrue(buildFile.isValid());
    assertEquals(FN_BUILD_GRADLE, buildFile.getName());
  }
}
