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
package com.android.tools.idea.gradle;

import com.android.tools.idea.gradle.NewAndroidProjectImporter.GradleProjectImporter;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

/**
 * Tests for {@link NewAndroidProjectImporter}.
 */
public class NewAndroidProjectImporterTest extends IdeaTestCase {
  private String myProjectName;
  private File myProjectRootDir;
  private GradleSettings myDefaultGradleSettings;
  private DataNode<ProjectData> myProjectInfo;

  private NewAndroidProjectImporter myImporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    myDefaultGradleSettings = GradleSettings.getInstance(defaultProject);
    myDefaultGradleSettings.applySettings("", "~/gradle", true, true, "~/.gradle");

    myProjectName = "test";
    myProjectRootDir = createTempDir(myProjectName);

    String projectRootDirPath = myProjectRootDir.getAbsolutePath();
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectRootDirPath);
    projectData.setName(myProjectName);
    myProjectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    ModuleData moduleData = new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), myProjectName, projectRootDirPath);
    myProjectInfo.createChild(ProjectKeys.MODULE, moduleData);

    GradleProjectImporter delegate = new GradleProjectImporter() {
      @NotNull
      @Override
      DataNode<ProjectData> importProject(@NotNull Project newProject, @NotNull String projectFilePath) throws ConfigurationException {
        assertNotNull(newProject);
        assertEquals(myProjectName, newProject.getName());
        File projectFile = new File(myProjectRootDir, "build.gradle");
        assertEquals(projectFile.getAbsolutePath(), projectFilePath);
        return myProjectInfo;
      }
    };

    myImporter = new NewAndroidProjectImporter(delegate);
  }

  public void testImportProject() throws Exception {
    Sdk sdk = getTestProjectJdk();
    assertNotNull(sdk);

    Project project = myImporter.importProject(myProjectName, myProjectRootDir, sdk);
    disposeOnTearDown(project);

    // Verify that project was imported correctly.
    assertEquals(myProjectName, project.getName());
    assertEquals(myProjectRootDir.getAbsolutePath(), project.getBasePath());

    // Verify that '.idea' directory was created.
    File ideaProjectDir = new File(myProjectRootDir, ".idea");
    assertTrue(ideaProjectDir.isDirectory());

    // Verify that module was created.
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    assertEquals(1, modules.length);
    assertEquals(myProjectName, modules[0].getName());

    // Verify that project has correct Gradle settings.
    GradleSettings gradleSettings = GradleSettings.getInstance(project);
    assertEquals(myDefaultGradleSettings.getGradleHome(), gradleSettings.getGradleHome());
    assertEquals(myDefaultGradleSettings.getServiceDirectoryPath(), gradleSettings.getServiceDirectoryPath());
    assertEquals(myDefaultGradleSettings.isPreferLocalInstallationToWrapper(), gradleSettings.isPreferLocalInstallationToWrapper());
    File projectFile = new File(myProjectRootDir, "build.gradle");
    assertEquals(projectFile.getAbsolutePath(), gradleSettings.getLinkedExternalProjectPath());
  }
}
