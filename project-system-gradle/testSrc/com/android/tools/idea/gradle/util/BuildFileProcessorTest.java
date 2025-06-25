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

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.BuildFileProcessor.getCompositeBuildFolderPaths;
import static com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentUtil.resolveAgpVersionSoftwareEnvironment;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.utils.FileUtils;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class BuildFileProcessorTest {
  private GradleProjectSettings myProjectSettings;

  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Before
  public void setup() {
    myProjectSettings = new GradleProjectSettings();
    Project project = projectRule.getProject();
    String projectRootPath = ExternalSystemApiUtil.toCanonicalPath(getBaseDirPath(project).getPath());
    myProjectSettings.setExternalProjectPath(projectRootPath);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(myProjectSettings));
  }

  @Test
  public void testGetCompositeBuildFolders() {
    Project project = projectRule.getProject();
    // Set current project as included build.
    BuildParticipant participant = new BuildParticipant();
    participant.setRootPath(project.getBasePath());

    GradleProjectSettings.CompositeBuild compositeBuild = new GradleProjectSettings.CompositeBuild();
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    myProjectSettings.setCompositeBuild(compositeBuild);

    List<File> folders = getCompositeBuildFolderPaths(project);
    assertThat(folders).hasSize(1);
    assertThat(folders.get(0).getPath()).isEqualTo(FileUtils.toSystemDependentPath(project.getBasePath()));
  }

  /**
   *   This test ensures that any calls to BuildFileProcessor#processRecursively don't pass any null build models to the supplied
   *   function.
   */
  @Test
  public void testNonExistentModuleDoesNotFailToParse() {
    try {
      AndroidGradleTests.prepareProjectForImportCore(
        AndroidCoreTestProject.SIMPLE_APPLICATION.getTemplateAbsolutePath(),
        new File(projectRule.getProject().getBasePath()),
        (root) -> AndroidGradleTests.defaultPatchPreparedProject(root, resolveAgpVersionSoftwareEnvironment(
          AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST), null, false)
      );
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }

    File settingsFile = new File(projectRule.getProject().getBasePath(), FN_SETTINGS_GRADLE);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(settingsFile);
    WriteAction.runAndWait(() -> {
      try {
        VfsUtil.saveText(virtualFile, VfsUtilCore.loadText(virtualFile) + "\ninclude 'notamodule'");
      }
      catch (IOException e) {
        Assert.fail(e.getMessage());
      }
    });
    BuildFileProcessor.processRecursively(
      projectRule.getProject(),
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
