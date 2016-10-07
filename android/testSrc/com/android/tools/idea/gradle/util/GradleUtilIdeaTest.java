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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static org.easymock.EasyMock.*;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;

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
    myBuildFile = new File(myModuleRootDir, SdkConstants.FN_BUILD_GRADLE);
    FileUtilRt.createIfNotExists(myBuildFile);
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

    GradleModel gradleModel = GradleModel.create(myModule.getName(), project, myBuildFile, "2.2.1");

    final FacetManager facetManager = FacetManager.getInstance(myModule);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ModifiableFacetModel model = facetManager.createModifiableModel();
        try {
          AndroidGradleFacet facet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
          model.addFacet(facet);
          facet.setGradleModel(gradleModel);
        }
        finally {
          model.commit();
        }
      }
    });

    VirtualFile buildFile = GradleUtil.getGradleBuildFile(myModule);
    assertIsGradleBuildFile(buildFile);

    verify(project, tasks);
  }

  private static void assertIsGradleBuildFile(@Nullable VirtualFile buildFile) {
    assertNotNull(buildFile);
    assertFalse(buildFile.isDirectory());
    assertTrue(buildFile.isValid());
    assertEquals(SdkConstants.FN_BUILD_GRADLE, buildFile.getName());
  }

  public void testCreateGradleWrapperWithSpecificGradleVersion() throws IOException {
    File projectDirPath = new File(myProject.getBasePath());
    File projectWrapperDirPath = new File(projectDirPath, FD_GRADLE_WRAPPER);
    assertFalse(projectWrapperDirPath.exists());

    String gradleVersion = "1.5";
    boolean created = GradleUtil.createGradleWrapper(projectDirPath, gradleVersion);
    assertTrue(created);
    assertGradleWrapperCreated(projectWrapperDirPath, gradleVersion);
  }

  public void testCreateGradleWrapperWithoutSpecificGradleVersion() throws IOException {
    File projectDirPath = new File(myProject.getBasePath());
    File projectWrapperDirPath = new File(projectDirPath, FD_GRADLE_WRAPPER);
    assertFalse(projectWrapperDirPath.exists());

    boolean created = GradleUtil.createGradleWrapper(projectDirPath);
    assertTrue(created);
    assertGradleWrapperCreated(projectWrapperDirPath, SdkConstants.GRADLE_LATEST_VERSION);
  }

  private static void assertGradleWrapperCreated(@NotNull File projectWrapperDirPath, @NotNull String gradleVersion) throws IOException {
    assertTrue(projectWrapperDirPath.isDirectory());
    File[] wrapperFiles = FileUtil.notNullize(projectWrapperDirPath.listFiles());
    assertEquals(2, wrapperFiles.length);

    Properties gradleProperties = PropertiesUtil.getProperties(new File(projectWrapperDirPath, FN_GRADLE_WRAPPER_PROPERTIES));
    String distributionUrl = gradleProperties.getProperty(DISTRIBUTION_URL_PROPERTY);
    assertEquals("https://services.gradle.org/distributions/gradle-" + gradleVersion + "-all.zip", distributionUrl);
  }
}
