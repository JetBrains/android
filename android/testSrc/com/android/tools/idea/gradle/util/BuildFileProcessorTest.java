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

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.BuildFileProcessor.getCompositeBuildFolderPaths;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

public class BuildFileProcessorTest extends IdeaTestCase {
  private GradleProjectSettings myProjectSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myProjectSettings = new GradleProjectSettings();
    Project project = getProject();
    String projectRootPath = getBaseDirPath(project).getPath();
    myProjectSettings.setExternalProjectPath(projectRootPath);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(myProjectSettings));
  }

  public void testGetCompositeBuildFolders() {
    // Set current project as included build.
    BuildParticipant participant = new BuildParticipant();
    participant.setRootPath(myProject.getBasePath());

    GradleProjectSettings.CompositeBuild compositeBuild = new GradleProjectSettings.CompositeBuild();
    compositeBuild.setCompositeParticipants(Collections.singletonList(participant));
    myProjectSettings.setCompositeBuild(compositeBuild);

    List<File> folders = getCompositeBuildFolderPaths(myProject);
    assertThat(folders).hasSize(1);
    assertThat(folders.get(0).getPath()).isEqualTo(myProject.getBasePath());
  }
}
