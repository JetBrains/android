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

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_NEW;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

/**
 * Tests for {@link GradleProjectImporter}.
 */
public class GradleProjectImporterTest extends PlatformTestCase {
  @Mock private GradleSyncInvoker mySyncInvoker;
  @Mock private NewProjectSetup myProjectSetup;
  @Mock private ProjectFolder myProjectFolder;
  @Mock private GradleSettings myGradleSettings;
  @Mock private GradleProjectInfo myGradleProjectInfo;

  private String myProjectName;
  private File myProjectFolderPath;

  private GradleProjectImporter myProjectImporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myProjectName = "testProject";

    SdkSync sdkSync = mock(SdkSync.class);

    Project project = getProject();
    String projectFolderPathText = project.getBasePath();
    assertNotNull(projectFolderPathText);
    myProjectFolderPath = new File(projectFolderPathText);

    ProjectFolder.Factory projectFolderFactory = mock(ProjectFolder.Factory.class);
    when(projectFolderFactory.create(myProjectFolderPath)).thenReturn(myProjectFolder);

    // Replace GradleSettings service with a mock.
    new IdeComponents(project).replaceProjectService(GradleSettings.class, myGradleSettings);
    assertSame(GradleSettings.getInstance(project), myGradleSettings);

    new IdeComponents(project).replaceProjectService(GradleProjectInfo.class, myGradleProjectInfo);

    myProjectImporter = new GradleProjectImporter(sdkSync, myProjectSetup, projectFolderFactory);
  }

  public void testImportProjectWithNonNullProject() throws Exception {
    GradleProjectImporter.Request importSettings = new GradleProjectImporter.Request(getProject());
    importSettings.javaLanguageLevel = JDK_1_8;

    myProjectImporter.importProjectNoSync(importSettings);

    // Verify project setup before syncing.
    verifyProjectFilesCreation();
    verifyProjectCreation(never());
    verifyProjectPreparation(JDK_1_8);
    verifyGradleVmOptionsCleanup(never());

    // Verify sync.
    GradleSyncListener syncListener = mock(GradleSyncListener.class);
    mySyncInvoker.requestProjectSyncAndSourceGeneration(getProject(), TRIGGER_PROJECT_NEW, syncListener);
    verifyGradleSyncInvocation(syncListener);
  }

  private void verifyProjectFilesCreation() throws IOException {
    verify(myProjectFolder, times(1)).createTopLevelBuildFile();
    verify(myProjectFolder, times(1)).createIdeaProjectFolder();
  }

  private void verifyProjectCreation(@NotNull VerificationMode verificationMode) {
    verify(myProjectSetup, verificationMode).createProject(myProjectName, myProjectFolderPath.getPath());
  }

  private void verifyProjectPreparation(@Nullable LanguageLevel languageLevel) {
    verify(myProjectSetup, times(1)).prepareProjectForImport(getProject(), languageLevel);
  }

  private void verifyGradleVmOptionsCleanup(@NotNull VerificationMode verificationMode) {
    verify(myGradleSettings, verificationMode).setGradleVmOptions("");
  }

  private void verifyGradleSyncInvocation(@Nullable GradleSyncListener syncListener) {
    verify(mySyncInvoker, times(1)).requestProjectSyncAndSourceGeneration(getProject(), TRIGGER_PROJECT_NEW, syncListener);
    verifyProjectWasMarkedAsImported();
  }

  private void verifyProjectWasMarkedAsImported() {
    verify(myGradleProjectInfo, times(1)).setImportedProject(true);
  }
}
