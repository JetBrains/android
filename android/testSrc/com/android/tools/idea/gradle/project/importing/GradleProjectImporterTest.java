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
package com.android.tools.idea.gradle.project.importing;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.mockito.verification.VerificationMode;

import java.io.File;
import java.io.IOException;

import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GradleProjectImporter}.
 */
public class GradleProjectImporterTest extends IdeaTestCase {
  private String myProjectName;
  private File myProjectFolderPath;
  private GradleSyncInvoker mySyncInvoker;
  private NewProjectSetup myProjectSetup;
  private GradleSettings myGradleSettings;
  private ProjectFolder myProjectFolder;

  private GradleProjectImporter myProjectImporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectName = "testProject";

    SdkSync sdkSync = mock(SdkSync.class);
    mySyncInvoker = mock(GradleSyncInvoker.class);
    myProjectSetup = mock(NewProjectSetup.class);
    myProjectFolder = mock(ProjectFolder.class);

    Project project = getProject();
    String projectFolderPathText = project.getBasePath();
    assertNotNull(projectFolderPathText);
    myProjectFolderPath = new File(projectFolderPathText);

    ProjectFolder.Factory projectFolderFactory = mock(ProjectFolder.Factory.class);
    when(projectFolderFactory.create(myProjectFolderPath)).thenReturn(myProjectFolder);

    // Replace GradleSettings service with a mock.
    myGradleSettings = mock(GradleSettings.class);
    IdeComponents.replaceService(project, GradleSettings.class, myGradleSettings);
    assertSame(GradleSettings.getInstance(project), myGradleSettings);

    myProjectImporter = new GradleProjectImporter(sdkSync, mySyncInvoker, myProjectSetup, projectFolderFactory);
  }

  public void testImportProjectWithDefaultSettings() throws Exception {
    Project newProject = getProject();
    when(myProjectSetup.createProject(myProjectName, myProjectFolderPath.getPath())).thenReturn(newProject);

    GradleSyncListener syncListener = mock(GradleSyncListener.class);
    myProjectImporter.importProject(myProjectName, myProjectFolderPath, syncListener);

    // Verify project setup before syncing.
    verifyProjectFilesCreation();
    verifyProjectCreation(times(1));
    verifyProjectPreparation(null);
    verifyGradleVmOptionsCleanup(times(1));

    // Verify sync.
    verifyGradleSyncInvocation(new GradleProjectImporter.Request(), syncListener);
  }

  public void testImportProjectWithNullProject() throws Exception {
    GradleProjectImporter.Request importSettings = new GradleProjectImporter.Request();
    importSettings.setProject(null).setLanguageLevel(JDK_1_8);

    Project newProject = getProject();
    when(myProjectSetup.createProject(myProjectName, myProjectFolderPath.getPath())).thenReturn(newProject);

    GradleSyncListener syncListener = mock(GradleSyncListener.class);
    myProjectImporter.importProject(myProjectName, myProjectFolderPath, importSettings, syncListener);

    // Verify project setup before syncing.
    verifyProjectFilesCreation();
    verifyProjectCreation(times(1));
    verifyProjectPreparation(JDK_1_8);
    verifyGradleVmOptionsCleanup(times(1));

    // Verify sync.
    verifyGradleSyncInvocation(importSettings, syncListener);
  }

  public void testImportProjectWithNonNullProject() throws Exception {
    GradleProjectImporter.Request importSettings = new GradleProjectImporter.Request();
    importSettings.setProject(getProject()).setLanguageLevel(JDK_1_8);

    GradleSyncListener syncListener = mock(GradleSyncListener.class);
    myProjectImporter.importProject(myProjectName, myProjectFolderPath, importSettings, syncListener);

    // Verify project setup before syncing.
    verifyProjectFilesCreation();
    verifyProjectCreation(never());
    verifyProjectPreparation(JDK_1_8);
    verifyGradleVmOptionsCleanup(never());

    // Verify sync.
    verifyGradleSyncInvocation(importSettings, syncListener);
  }

  private void verifyProjectFilesCreation() throws IOException {
    verify(myProjectFolder, times(1)).createTopLevelBuildFile();
    verify(myProjectFolder, times(1)).createIdeaProjectFolder();
  }

  private void verifyProjectCreation(@NotNull VerificationMode verificationMode) throws ConfigurationException {
    verify(myProjectSetup, verificationMode).createProject(myProjectName, myProjectFolderPath.getPath());
  }

  private void verifyProjectPreparation(@Nullable LanguageLevel languageLevel) {
    verify(myProjectSetup, times(1)).prepareProjectForImport(getProject(), languageLevel);
  }

  private void verifyGradleVmOptionsCleanup(@NotNull VerificationMode verificationMode) {
    verify(myGradleSettings, verificationMode).setGradleVmOptions("");
  }

  private void verifyGradleSyncInvocation(@NotNull GradleProjectImporter.Request importSettings,
                                          @Nullable GradleSyncListener syncListener) {
    GradleSyncInvoker.Request syncRequest = new GradleSyncInvoker.Request();

    // @formatter:off
    syncRequest.setGenerateSourcesOnSuccess(importSettings.isGenerateSourcesOnSuccess())
               .setNewProject(true)
               .setRunInBackground(false);
    // @formatter:on

    verify(mySyncInvoker, times(1)).requestProjectSync(getProject(), syncRequest, syncListener);
  }
}