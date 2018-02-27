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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenPluginBuildFileHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.dsl.api.repositories.GoogleDefaultRepositoryModel.GOOGLE_METHOD_NAME;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.PLUGIN_IN_APP;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MissingAndroidPluginErrorHandler}.
 */
public class MissingAndroidPluginErrorHandlerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;
  private IdeComponents myIdeComponents;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(project);
    myIdeComponents = new IdeComponents(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testWithGradle4dot0() throws Exception {
    // Check when Gradle Version is 4.0 or higher
    loadProject(SIMPLE_APPLICATION);
    Project project = getProject();
    GradleVersions spyVersions = spy(GradleVersions.getInstance());
    myIdeComponents.replaceService(GradleVersions.class, spyVersions);
    when(spyVersions.getGradleVersion(project)).thenReturn(new GradleVersion(4,0));

    // Make sure no repository is listed
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    buildModel.buildscript().removeRepositoriesBlocks();
    assertTrue(buildModel.isModified());
    runWriteCommandAction(project, buildModel::applyChanges);
    assertFalse(buildModel.isModified());

    // Verify generated hyperlinks
    List<NotificationHyperlink> quickFixes = simulateAndVerifySyncErrors();
    File expectedBuildFile = new File(getProjectFolderPath(), FN_BUILD_GRADLE);
    String expectedPath = expectedBuildFile.getCanonicalPath();
    verifyAddAndOpenHyperlinks(quickFixes, expectedPath);
  }

  public void testWithGradle2dot2dot1() throws Exception {
    // Check when Gradle Version is lower than 4.0
    loadProject(SIMPLE_APPLICATION);
    Project project = getProject();
    GradleVersions spyVersions = spy(GradleVersions.getInstance());
    myIdeComponents.replaceService(GradleVersions.class, spyVersions);
    when(spyVersions.getGradleVersion(project)).thenReturn(new GradleVersion(2,2, 1));

    // Make sure no repository is listed
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    buildModel.buildscript().removeRepositoriesBlocks();
    assertTrue(buildModel.isModified());
    runWriteCommandAction(project, buildModel::applyChanges);
    assertFalse(buildModel.isModified());

    // Verify generated hyperlinks
    List<NotificationHyperlink> quickFixes = simulateAndVerifySyncErrors();
    File expectedBuildFile = new File(getProjectFolderPath(), FN_BUILD_GRADLE);
    String expectedPath = expectedBuildFile.getCanonicalPath();
    verifyAddAndOpenHyperlinks(quickFixes, expectedPath);
  }

  public void testWithPluginSetInAppModule() throws Exception {
    // Check that hyperlinks are applied to app build instead to project build file when the plugin is defined there
    loadProject(PLUGIN_IN_APP);
    Project project = getProject();

    // Make sure no repository is listed
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    buildModel.buildscript().removeRepositoriesBlocks();
    assertTrue(buildModel.isModified());
    runWriteCommandAction(project, buildModel::applyChanges);
    assertFalse(buildModel.isModified());

    // Verify generated hyperlinks
    List<NotificationHyperlink> quickFixes = simulateAndVerifySyncErrors();
    File expectedBuildFile = getBuildFilePath("app");
    String expectedPath = expectedBuildFile.getCanonicalPath();
    verifyAddAndOpenHyperlinks(quickFixes, expectedPath);
  }

  public void testRepositoryAlreadySet() throws Exception {
    // Check that no quickfix to add Google repository is generated when this repository is already on the build file
    loadProject(SIMPLE_APPLICATION);
    Project project = getProject();

    // Make sure google is already in the list of repositories
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    buildModel.buildscript().repositories().addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    runWriteCommandAction(project, buildModel::applyChanges);
    assertFalse(buildModel.isModified());

    // Verify generated hyperlinks
    List<NotificationHyperlink> quickFixes = simulateAndVerifySyncErrors();
    assertThat(quickFixes).hasSize(1);
    File expectedBuildFile = new File(getProjectFolderPath(), FN_BUILD_GRADLE);
    String expectedPath = expectedBuildFile.getCanonicalPath();
    verifyOpenHyperlink(quickFixes.get(0), expectedPath);
  }

  public void testProjectNotInitialized() throws Exception {
    // Check that quickfixes are generated when the project is not initialized
    loadProject(SIMPLE_APPLICATION);
    Project spyProject = spy(getProject());
    when(spyProject.isInitialized()).thenReturn(false);

    // Verify generated hyperlinks
    List<NotificationHyperlink> quickFixes = new MissingAndroidPluginErrorHandler().getQuickFixHyperlinks(spyProject, "Test Error");
    assertThat(quickFixes).hasSize(2);
    assertThat(quickFixes.get(0)).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
    AddGoogleMavenRepositoryHyperlink addHyperlink = (AddGoogleMavenRepositoryHyperlink)quickFixes.get(0);
    assertThat(addHyperlink.getBuildFile()).isNull();
    assertThat(quickFixes.get(1)).isInstanceOf(OpenPluginBuildFileHyperlink.class);
  }

  @NotNull
  private List<NotificationHyperlink> simulateAndVerifySyncErrors() throws Exception {
    // Simulate a sync error
    String errorMessage = "Could not find com.android.tools.build:gradle:10.100.1000-alpha10000.\n" +
                          "Searched in the following locations:\n";
    registerSyncErrorToSimulate(errorMessage);
    requestSyncAndGetExpectedFailure();
    // See if sync generates the expected failure
    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);
    assertThat(notificationUpdate.getText()).isEqualTo(errorMessage);
    return notificationUpdate.getFixes();
  }

  private static void verifyAddAndOpenHyperlinks(@NotNull List<NotificationHyperlink> quickFixes, @NotNull String expectedPath) {
    assertThat(quickFixes).hasSize(2);
    verifyAddHyperlink(quickFixes.get(0), expectedPath);
    verifyOpenHyperlink(quickFixes.get(1), expectedPath);
  }

  private static void verifyAddHyperlink(@NotNull NotificationHyperlink hyperlink, @NotNull String expectedPath) {
    assertThat(hyperlink).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
    AddGoogleMavenRepositoryHyperlink addHyperlink = (AddGoogleMavenRepositoryHyperlink)hyperlink;
    assertThat(toSystemDependentName(addHyperlink.getBuildFile().getPath())).isEqualTo(expectedPath);
  }

  private static void verifyOpenHyperlink(@NotNull NotificationHyperlink hyperlink, @NotNull String expectedPath) {
    assertThat(hyperlink).isInstanceOf(OpenFileHyperlink.class);
    OpenFileHyperlink openHyperlink = (OpenFileHyperlink)hyperlink;
    assertThat(toSystemDependentName(openHyperlink.getFilePath())).isEqualTo(expectedPath);
  }
}
