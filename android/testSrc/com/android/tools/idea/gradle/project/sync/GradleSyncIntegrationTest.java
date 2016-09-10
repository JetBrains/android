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
package com.android.tools.idea.gradle.project.sync;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessageReporterStub;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessages;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.Modules;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.util.GradleUtil.getAndroidProject;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleProjectSettings;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.BASIC;
import static com.android.tools.idea.testing.TestProjectPaths.LOCAL_AARS_AS_MODULES;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for 'Gradle Sync'.
 */
public class GradleSyncIntegrationTest extends AndroidGradleTestCase {
  private Modules myModules;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    myModules = new Modules(project);

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);

    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  // See https://code.google.com/p/android/issues/detail?id=66880
  public void testAutomaticCreationOfMissingWrapper() throws Exception {
    loadSimpleApplication();
    deleteGradleWrapper();
    requestSyncAndWait();
    verifyGradleWrapperExists();
  }

  public void testWithNonExistingInterModuleDependencies() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    assertNotNull(buildModel);
    buildModel.dependencies().addModule(COMPILE, ":fakeLibrary");
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    String failure = requestSyncAndGetExpectedFailure();
    assertThat(failure).startsWith("Project with path ':fakeLibrary' could not be found");

    // TODO verify that a message and "quick fix" has been displayed.
  }

  public void testWithUnresolvedDependencies() throws Exception {
    loadSimpleApplication();

    File buildFilePath = getAppBuildFilePath();
    VirtualFile buildFile = findFileByIoFile(buildFilePath, true);
    assertNotNull(buildFile);

    boolean versionChanged = false;

    Project project = getProject();
    GradleBuildModel buildModel = GradleBuildModel.parseBuildFile(buildFile, project);

    for (ArtifactDependencyModel artifact : buildModel.dependencies().artifacts()) {
      if ("com.android.support".equals(artifact.group().value()) && "appcompat-v7".equals(artifact.name().value())) {
        artifact.setVersion("100.0.0");
        versionChanged = true;
        break;
      }
    }
    assertTrue(versionChanged);

    runWriteCommandAction(project, buildModel::applyChanges);
    LocalFileSystem.getInstance().refresh(false /* synchronous */);

    SyncMessageReporterStub messageReporter = setSyncMessagesForTesting();

    requestSyncAndWait();

    SyncMessage reportedMessage = messageReporter.getReportedMessage();
    assertNotNull(reportedMessage);
    String[] text = reportedMessage.getText();
    assertThat(text).isNotEmpty();
    assertEquals("Failed to resolve: com.android.support:appcompat-v7:100.0.0", text[0]);
  }

  @NotNull
  private SyncMessageReporterStub setSyncMessagesForTesting() {
    Project project = getProject();
    SyncMessageReporterStub messageReporter = new SyncMessageReporterStub(project);
    SyncMessages syncMessages = new SyncMessages(project, mock(ExternalSystemNotificationManager.class), messageReporter);
    IdeComponents.replaceService(project, SyncMessages.class, syncMessages);
    assertSame(syncMessages, SyncMessages.getInstance(project));
    return messageReporter;
  }

  public void testWithUserDefinedLibrarySources() throws Exception {
    loadSimpleApplication();

    String libraryName = "guava-18.0";
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(getProject());
    Library library = libraryTable.getLibraryByName(libraryName);
    assertNotNull(library);

    String url = "jar://$USER_HOME$/fake-dir/fake-sources.jar!/";

    // add an extra source path.
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    libraryModel.addRoot(url, SOURCES);
    ApplicationManager.getApplication().runWriteAction(libraryModel::commit);

    requestSyncAndWait();

    libraryTable = ProjectLibraryTable.getInstance(getProject());
    library = libraryTable.getLibraryByName(libraryName);
    assertNotNull(library);

    String[] urls = library.getUrls(SOURCES);
    assertThat(urls).asList().contains(url);
  }

  public void testSyncShouldNotChangeDependenciesInBuildFiles() throws Exception {
    loadSimpleApplication();

    File appBuildFilePath = getAppBuildFilePath();
    long lastModified = appBuildFilePath.lastModified();

    requestSyncAndWait();

    // See https://code.google.com/p/android/issues/detail?id=78628
    assertEquals(lastModified, appBuildFilePath.lastModified());
  }

  @NotNull
  private File getAppBuildFilePath() {
    File buildFilePath = new File(getProjectFolderPath(), join("app", FN_BUILD_GRADLE));
    assertAbout(file()).that(buildFilePath).isFile();
    return buildFilePath;
  }

  // See https://code.google.com/p/android/issues/detail?id=76444
  public void testWithEmptyGradleSettingsFileInSingleModuleProject() throws Exception {
    loadProject(BASIC);
    createEmptyGradleSettingsFile();
    // Sync should be successful for single-module projects with an empty settings.gradle file.
    requestSyncAndWait();
  }

  private void createEmptyGradleSettingsFile() throws IOException {
    File settingsFilePath = new File(getProjectFolderPath(), FN_SETTINGS_GRADLE);
    assertTrue(delete(settingsFilePath));
    writeToFile(settingsFilePath, " ");
    assertAbout(file()).that(settingsFilePath).isFile();
    LocalFileSystem.getInstance().refresh(false /* synchronous */);
  }

  public void testShouldCreateWrapperWhenLocalDistributionPathIsNotSet() throws Exception {
    loadSimpleApplication();

    setGradleLocalDistribution("");
    deleteGradleWrapper();

    TestMessagesDialog testDialog = new TestMessagesDialog(Messages.OK);
    Messages.setTestDialog(testDialog);

    requestSyncAndWait();

    String message = testDialog.getDisplayedMessage();
    assertThat(message).contains("The path of the local Gradle distribution to use is not set.");
    verifyUserIsAskedToUseGradleWrapper(message);

    verifyGradleWrapperExists();
  }

  public void testShouldCreateWrapperWhenLocalDistributionPathDoesNotExist() throws Exception {
    loadSimpleApplication();

    String nonExistingPath = new File(SystemProperties.getUserHome(), UUID.randomUUID().toString()).getPath();
    setGradleLocalDistribution(nonExistingPath);
    deleteGradleWrapper();

    TestMessagesDialog testDialog = new TestMessagesDialog(Messages.OK);
    Messages.setTestDialog(testDialog);

    requestSyncAndWait();

    String message = testDialog.getDisplayedMessage();
    assertThat(message).contains("'" + nonExistingPath + "'");
    assertThat(message).contains("set as a local Gradle distribution, does not belong to an existing directory.");
    verifyUserIsAskedToUseGradleWrapper(message);

    verifyGradleWrapperExists();
  }

  private void setGradleLocalDistribution(@NotNull String gradleLocalDistributionPath) {
    GradleProjectSettings settings = getGradleProjectSettings(getProject());
    assertNotNull(settings);
    settings.setDistributionType(LOCAL);
    settings.setGradleHome(gradleLocalDistributionPath);
  }

  private void deleteGradleWrapper() {
    File gradleWrapperFolderPath = getGradleWrapperFolderPath();
    delete(gradleWrapperFolderPath);
    assertAbout(file()).that(gradleWrapperFolderPath).named("Gradle wrapper").doesNotExist();
  }

  @NotNull
  private File getGradleWrapperFolderPath() {
    return new File(getProjectFolderPath(), FD_GRADLE);
  }

  private void verifyGradleWrapperExists() {
    File gradleWrapperFolderPath = getGradleWrapperFolderPath();
    assertAbout(file()).that(gradleWrapperFolderPath).named("Gradle wrapper").isDirectory();
  }

  private static void verifyUserIsAskedToUseGradleWrapper(@Nullable String message) {
    assertThat(message).contains("Would you like the project to use the Gradle wrapper?");
  }

  public void testWithLocalAarsAsModules() throws Exception {
    loadProject(LOCAL_AARS_AS_MODULES);

    Module localAarModule = myModules.getModule("library-debug");

    // When AAR files are exposed as artifacts, they don't have an AndroidProject model.
    AndroidFacet androidFacet = AndroidFacet.getInstance(localAarModule);
    assertNull(androidFacet);
    assertNull(getAndroidProject(localAarModule));
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(localAarModule);

    LibraryOrderEntry libraryDependency = null;
    for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        libraryDependency = (LibraryOrderEntry)orderEntry;
        break;
      }
    }
    assertNull(libraryDependency); // Should not expose the AAR as library, instead it should use the "exploded AAR".

    Module appModule = myModules.getAppModule();
    moduleRootManager = ModuleRootManager.getInstance(appModule);
    // Verify that the module depends on the AAR that it contains (in "exploded-aar".)
    libraryDependency = null;
    for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        libraryDependency = (LibraryOrderEntry)orderEntry;
        break;
      }
    }

    assertNotNull(libraryDependency);
    assertThat(libraryDependency.getLibraryName()).isEqualTo("library-debug-unspecified");
    assertFalse(libraryDependency.isExported());
  }
}
