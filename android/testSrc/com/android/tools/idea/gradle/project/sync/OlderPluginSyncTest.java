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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgrade;
import com.android.tools.idea.testing.*;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.tools.idea.testing.AndroidGradleTests.*;
import static com.google.common.io.Files.write;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.Mockito.mock;

public class OlderPluginSyncTest extends AndroidGradleTestCase {
  private String myGradleVersion;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    // We don't want the IDE to offer a plugin version upgrade.
    IdeComponents.replaceService(getProject(), PluginVersionUpgrade.class, mock(PluginVersionUpgrade.class));

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  // Syncs a project with Android plugin 1.5.0 and Gradle 2.2.1
  public void testWithPluginOneDotFive() throws Exception {
    myGradleVersion = "2.2.1";
    // We are verifying that sync succeeds without errors.
    loadProject(TestProjectPaths.PROJECT_WITH1_DOT5);
  }

  @Override
  @NotNull
  protected File prepareProjectForImport(@NotNull String relativePath) throws IOException {
    File projectRoot = super.prepareProjectForImport(relativePath);
    createGradleWrapper(projectRoot, myGradleVersion);
    return projectRoot;
  }

  @Override
  protected void createGradleWrapper(@NotNull File projectRoot) throws IOException {
    // Do not create the Gradle wrapper automatically. Let each test method create it with the version of Gradle needed.
  }

  @Override
  protected void updateVersionAndDependencies(@NotNull File projectRoot) throws IOException {
    // In this overriden version we don't update versions of the Android plugin and use the one specified in the test project.
    updateVersionAndDependencies(projectRoot, getLocalRepositories());
  }

  private static void updateVersionAndDependencies(@NotNull File path, @NotNull String localRepositories) throws IOException {
    if (path.isDirectory()) {
      for (File child : notNullize(path.listFiles())) {
        updateVersionAndDependencies(child, localRepositories);
      }
    }
    else if (path.getPath().endsWith(DOT_GRADLE) && path.isFile()) {
      String contentsOrig = Files.toString(path, Charsets.UTF_8);
      String contents = contentsOrig;

      contents = updateBuildToolsVersion(contents);
      contents = updateCompileSdkVersion(contents);
      contents = updateTargetSdkVersion(contents);
      contents = updateLocalRepositories(contents, localRepositories);

      if (!contents.equals(contentsOrig)) {
        write(contents, path, Charsets.UTF_8);
      }
    }
  }
}
