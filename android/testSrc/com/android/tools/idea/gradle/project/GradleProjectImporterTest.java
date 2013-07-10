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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

/**
 * Tests for {@link GradleProjectImporter}.
 */
public class GradleProjectImporterTest extends IdeaTestCase {
  private String myProjectName;
  private File myProjectRootDir;
  private DataNode<ProjectData> myProjectInfo;

  private GradleProjectImporter myImporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProjectName = "test";
    myProjectRootDir = createTempDir(myProjectName);

    String projectRootDirPath = myProjectRootDir.getAbsolutePath();
    final File projectFile = new File(myProjectRootDir, SdkConstants.FN_BUILD_GRADLE);
    final String configPath = projectFile.getAbsolutePath();
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectRootDirPath, configPath);
    projectData.setName(myProjectName);
    myProjectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    ModuleData moduleData
      = new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), myProjectName, projectRootDirPath, configPath);
    myProjectInfo.createChild(ProjectKeys.MODULE, moduleData);

    GradleProjectImporter.ImporterDelegate delegate = new GradleProjectImporter.ImporterDelegate() {
      @Override
      void importProject(@NotNull Project project, @NotNull ExternalProjectRefreshCallback callback, boolean modal)
        throws ConfigurationException {
        assertNotNull(project);
        assertEquals(myProjectName, project.getName());

        callback.onSuccess(myProjectInfo);
      }
    };

    myImporter = new GradleProjectImporter(delegate);
  }

  @Override
  protected void tearDown() throws Exception {
    Project[] projects = myProjectManager.getOpenProjects();
    for (Project project : projects) {
      if (project != getProject()) {
        myProjectManager.closeAndDispose(project);
      }
    }
    super.tearDown();
  }

  public void testImportProject() throws Exception {
    Sdk sdk = getTestProjectJdk();
    assertNotNull(sdk);

    MyCallback callback = new MyCallback();
    myImporter.importProject(myProjectName, myProjectRootDir, sdk, callback);
  }

  private class MyCallback implements GradleProjectImporter.Callback {
    @Override
    public void projectImported(@NotNull Project project) {
      disposeOnTearDown(project);
      // Verify that project was imported correctly.
      assertEquals(myProjectName, project.getName());
      assertEquals(myProjectRootDir.getAbsolutePath(), project.getBasePath());

      // Verify that '.idea' directory was created.
      File ideaProjectDir = new File(myProjectRootDir, Project.DIRECTORY_STORE_FOLDER);
      assertTrue(ideaProjectDir.isDirectory());

      // Verify that module was created.
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      Module[] modules = moduleManager.getModules();
      assertEquals(1, modules.length);
      assertEquals(myProjectName, modules[0].getName());
    }
  }
}
