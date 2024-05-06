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
package com.android.tools.idea.gradle.util;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.BuildFileProcessor.getCompositeBuildFolderPaths;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.utils.FileUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class BuildFileProcessorTest extends AndroidGradleTestCase {
  private GradleProjectSettings myProjectSettings;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myProjectSettings = new GradleProjectSettings();
    Project project = getProject();
    String projectRootPath = ExternalSystemApiUtil.toCanonicalPath(getBaseDirPath(project).getPath());
    myProjectSettings.setExternalProjectPath(projectRootPath);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(myProjectSettings));
  }

  public void testGetCompositeBuildFolders() {
    // Set current project as included build.
    BuildParticipant participant = new BuildParticipant();
    participant.setRootPath(getProject().getBasePath());

    GradleProjectSettings.CompositeBuild compositeBuild = new GradleProjectSettings.CompositeBuild();
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    myProjectSettings.setCompositeBuild(compositeBuild);

    List<File> folders = getCompositeBuildFolderPaths(getProject());
    assertThat(folders).hasSize(1);
    assertThat(folders.get(0).getPath()).isEqualTo(FileUtils.toSystemDependentPath(getProject().getBasePath()));
  }

  /**
   *   This test ensures that any calls to BuildFileProcessor#processRecursively don't pass any null build models to the supplied
   *   function.
   */
  public void testNonExistentModuleDoesNotFailToParse() throws Exception {
    prepareProjectForImport(SIMPLE_APPLICATION);

    File settingsFile = getSettingsFilePath();
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(settingsFile);
    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Void, Exception>)() -> {
      VfsUtil.saveText(virtualFile, VfsUtilCore.loadText(virtualFile) + "\ninclude 'notamodule'");
      return null;
    });
    BuildFileProcessor.processRecursively(
      getProject(),
      (settingsModel) -> {
        assertThat(settingsModel).isNotNull();
        return true;
      },
      (buildModel) -> {
        assertThat(buildModel).isNotNull();
        return true;
      });
  }
}
