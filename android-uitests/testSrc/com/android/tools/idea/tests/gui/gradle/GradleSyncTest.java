/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_COMPILE;
import static com.android.tools.idea.gradle.util.PropertiesFiles.getProperties;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.tests.gui.framework.GuiTests.getFilePathPropertyOrSkipTest;
import static com.android.tools.idea.tests.gui.framework.GuiTests.skipTest;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.backupGlobalGradlePropertiesFile;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.getUserGradlePropertiesFile;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.restoreGlobalGradlePropertiesFile;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.io.FileUtil.createIfNotExists;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProxySettingsDialogFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.net.HttpConfigurable;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class GradleSyncTest {
  @Nullable private File myBackupProperties;

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String ANDROID_SDK_MANAGER_DIALOG_TITLE = "Android SDK Manager";

  /**
   * Generate a backup copy of user gradle.properties since some tests in this class make changes to the proxy that could
   * cause other tests to use an incorrect configuration.
   */
  @Before
  public void backupPropertiesFile() {
    myBackupProperties = backupGlobalGradlePropertiesFile();
  }

  /**
   * Restore user gradle.properties file content to what it had before running the tests, or delete if it did not exist.
   */
  @After
  public void restorePropertiesFile() {
    restoreGlobalGradlePropertiesFile(myBackupProperties);
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=183368
  public void withTestOnlyInterModuleDependencies() throws IOException {
    guiTest.importMultiModule();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Module appModule = guiTest.ideFrame().getModule("app");

    // Set a dependency on a module that does not exist.
    ApplicationManager.getApplication().invokeAndWait(() -> runWriteCommandAction(
      ideFrame.getProject(), () -> {
        GradleBuildModel buildModel = GradleBuildModel.get(appModule);
        buildModel.dependencies().addModule(ANDROID_TEST_COMPILE, ":library3");
        buildModel.applyChanges();
      }));

    ideFrame.actAndWaitForGradleProjectSyncToFinish(it -> it.requestProjectSync());

    for (OrderEntry entry : ModuleRootManager.getInstance(appModule).getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        if (guiTest.ideFrame().getModule("library3").getName().equals(moduleOrderEntry.getModuleName())) {
          assertEquals(DependencyScope.TEST, moduleOrderEntry.getScope());
          return;
        }
      }
    }
    fail("No dependency for library3 found");
  }

  // See https://code.google.com/p/android/issues/detail?id=74341
  @Test
  public void editorShouldFindAppCompatStyle() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("AarDependency");
    EditorFixture editor = guiTest.ideFrame().getEditor();

    editor.open("app/src/main/res/values/strings.xml", Tab.EDITOR);
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }

  // See https://code.google.com/p/android/issues/detail?id=165576
  @Test
  public void javaModelSerialization() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");

    guiTest.ideFrame().requestProjectSyncAndWaitForSyncToFinish().closeProject();

    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(guiTest.ideFrame().getProject());
    // When serialization of Java model fails, libraries are not set up.
    // Here we confirm that serialization works, because the Java module has the dependency declared in its build.gradle file.
    assertThat(libraryTable.getLibraries()).asList().hasSize(1);
  }

  // See https://code.google.com/p/android/issues/detail?id=167378
  @Test
  public void interJavaModuleDependencies() throws IOException {
    guiTest.importMultiModule();

    Module library = guiTest.ideFrame().getModule("library");
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(library);

    // Verify that the module "library" depends on module "library2"
    ModuleOrderEntry moduleDependency = null;
    for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        moduleDependency = (ModuleOrderEntry)orderEntry;
        break;
      }
    }

    assertThat(moduleDependency.getModuleName()).isEqualTo(guiTest.ideFrame().getModule("library2").getName());
  }

  // Verifies that the IDE, during sync, asks the user to copy IDE proxy settings to gradle.properties, if applicable.
  // See https://code.google.com/p/android/issues/detail?id=65325
  @Test
  public void withIdeProxySettings() throws IOException {
    System.getProperties().setProperty("show.do.not.copy.http.proxy.settings.to.gradle", "true");

    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    File gradlePropertiesFile = getUserGradlePropertiesFile();
    createIfNotExists(gradlePropertiesFile);

    String host = "myproxy.test.com";
    int port = 443;

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;
    ideFrame.actAndWaitForGradleProjectSyncToFinish(Wait.seconds(20), it -> {

      ideFrame.requestProjectSync();

      // Expect IDE to ask user to copy proxy settings.
      ProxySettingsDialogFixture proxyDialog = ProxySettingsDialogFixture.find(guiTest.robot());
      proxyDialog.setDoNotShowThisDialog(true);
      proxyDialog.clickYes();
    });

    // Verify gradle.properties has proxy settings.
    gradlePropertiesFile = getUserGradlePropertiesFile();
    assertAbout(file()).that(gradlePropertiesFile).isFile();

    Properties gradleProperties = getProperties(gradlePropertiesFile);
    assertEquals(host, gradleProperties.getProperty("systemProp.http.proxyHost"));
    assertEquals(String.valueOf(port), gradleProperties.getProperty("systemProp.http.proxyPort"));

    // Verifies that the "Do not show this dialog in the future" does not show up. If it does show up the test will timeout and fail.
    ideFrame.actAndWaitForGradleProjectSyncToFinish(it -> it.requestProjectSync());
  }

  // Verifies that the IDE switches SDKs if the IDE and project SDKs are not the same.
  @Test
  public void sdkSwitch() throws IOException {
    File secondSdkPath = getFilePathPropertyOrSkipTest("second.android.sdk.path", "the path of a secondary Android SDK", true);

    IdeSdks ideSdks = IdeSdks.getInstance();
    File originalSdkPath = ideSdks.getAndroidSdkPath();

    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    // Change the SDK in the project. We expect the IDE to have the same SDK as the project.
    LocalProperties localProperties = new LocalProperties(ideFrame.getProject());
    localProperties.setAndroidSdkPath(secondSdkPath);
    localProperties.save();

    ideFrame.actAndWaitForGradleProjectSyncToFinish(it -> {
      ideFrame.requestProjectSync();

      MessagesFixture messages = ideFrame.findMessageDialog(ANDROID_SDK_MANAGER_DIALOG_TITLE);
      messages.click("Use Project's SDK");
    });

    assertThat(ideSdks.getAndroidSdkPath()).isEqualTo(secondSdkPath);

    // Set the project's SDK to be the original one. Now we will choose the IDE's SDK.
    localProperties = new LocalProperties(ideFrame.getProject());
    localProperties.setAndroidSdkPath(originalSdkPath);
    localProperties.save();

    ideFrame.actAndWaitForGradleProjectSyncToFinish(it -> {
      ideFrame.requestProjectSync();

      MessagesFixture messages = ideFrame.findMessageDialog(ANDROID_SDK_MANAGER_DIALOG_TITLE);
      messages.click("Use Android Studio's SDK");
    });

    localProperties = new LocalProperties(ideFrame.getProject());
    assertThat(localProperties.getAndroidSdkPath()).isEqualTo(secondSdkPath);
  }

  // Verifies that after making a change in a build.gradle file, the editor notification saying that sync is needed shows up. This wasn't
  // the case after a project import.
  // See https://code.google.com/p/android/issues/detail?id=171370
  @Test
  public void editorNotificationsWhenSyncNeededAfterProjectImport() throws IOException {
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();
    // @formatter:off
    ideFrame.getEditor()
            .open("app/build.gradle")
            .waitUntilErrorAnalysisFinishes()
            .enterText("Hello World")
            .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.");
    // @formatter:on
  }

  @Test
  public void withModuleLanguageLevelEqualTo8() throws IOException {
    Sdk jdk = IdeSdks.getInstance().getJdk();
    if (jdk == null) {
      skipTest("JDK is null");
    }

    assume().that(JavaSdk.getInstance().getVersion(jdk)).isAtLeast(JavaSdkVersion.JDK_1_8);

    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");
    Module javaLib = guiTest.ideFrame().getModule("javaLib");
    assertEquals(JDK_1_8, LanguageLevelUtil.getCustomLanguageLevel(javaLib));
  }

  @Test
  public void shouldUseLibrary() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Project project = ideFrame.getProject();

    // Make sure the library was added.
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    // Naming scheme follows "Gradle: " + name of the library. See LibraryDependency#setName method
    String libraryName = GradleConstants.SYSTEM_ID.getReadableName() + ": org.apache.http.legacy";
    Library library = libraryTable.getLibraryByName(libraryName);

    // Verify that the library has the right j
    VirtualFile[] jarFiles = library.getFiles(CLASSES);
    assertThat(jarFiles).asList().hasSize(1);
    VirtualFile jarFile = jarFiles[0];
    assertEquals("org.apache.http.legacy.jar", jarFile.getName());

    // Verify that the module depends on the library
    Module appModule = ideFrame.getModule("app");
    AtomicBoolean dependencyFound = new AtomicBoolean();
    ReadAction.run(() -> {
      ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(appModule).getModifiableModel();
      try {
        for (OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
          if (orderEntry instanceof LibraryOrderEntry) {
            LibraryOrderEntry libraryDependency = (LibraryOrderEntry)orderEntry;
            if (libraryDependency.getLibrary() == library) {
              dependencyFound.set(true);
            }
          }
        }
      }
      finally {
        modifiableModel.dispose();
      }
    });
    assertTrue("Module app should depend on library '" + library.getName() + "'", dependencyFound.get());
  }

  // https://code.google.com/p/android/issues/detail?id=185313
  @Test
  public void sdkCreationForAddons() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Project project = ideFrame.getProject();

    Module appModule = ideFrame.getModule("app");
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(ideFrame.getProject());
    projectBuildModel.getModuleBuildModel(appModule).android().compileSdkVersion().setValue("Google Inc.:Google APIs:24");

    ApplicationManager.getApplication().invokeAndWait(() -> runWriteCommandAction(project, () -> projectBuildModel.applyChanges()));

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();

    Sdk sdk = ModuleRootManager.getInstance(appModule).getSdk();

    AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);

    SdkAdditionalData data = sdk.getSdkAdditionalData();
    assertThat(data).isInstanceOf(AndroidSdkAdditionalData.class);

    AndroidSdkAdditionalData androidSdkData = (AndroidSdkAdditionalData)data;
    IAndroidTarget buildTarget = androidSdkData.getBuildTarget(sdkData);

    // By checking that there are no additional libraries in the SDK, we are verifying that an additional SDK was not created for add-ons.
    assertThat(buildTarget.getAdditionalLibraries()).hasSize(0);
  }

  @Test
  public void gradleModelCache() throws IOException {
    File projectDir = guiTest.setUpProject("SimpleApplication");

    // First time, open the project to sync.
    IdeFrameFixture ideFrame = guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    ideFrame.closeProject();
    // Second time, open the project expecting no sync.
    ideFrame = guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);

    ProjectSystemSyncManager.SyncResult lastSyncResult =
      ProjectSystemService.getInstance(ideFrame.getProject()).getProjectSystem().getSyncManager().getLastSyncResult();

    // A sync should not have been performed but the status should have been updated to SKIPPED.
    assertThat(lastSyncResult).isEqualTo(ProjectSystemSyncManager.SyncResult.SKIPPED);

    // But the models should still be present
    assertThat(AndroidModuleModel.get(ideFrame.getModule("app"))).isNotNull();
    assertThat(GradleFacet.getInstance(ideFrame.getModule("app"))).isNotNull();
    ideFrame.closeProject();
  }

  @Test
  public void gradleModelCachedNoModules() throws IOException {
    File projectDir = guiTest.setUpProject("SimpleApplication");

    // First time, open the project to sync.
    IdeFrameFixture ideFrame = guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    ideFrame.closeProject();

    new File(projectDir, ".idea/modules.xml").delete();
    new File(projectDir, ".idea/modules/app/SimpleApplication.app.iml").delete();
    new File(projectDir, ".idea/modules/SimpleApplication.iml").delete();
    // Second time, open the project expecting no sync.
    ideFrame = guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);

    ProjectSystemSyncManager.SyncResult lastSyncResult =
      ProjectSystemService.getInstance(ideFrame.getProject()).getProjectSystem().getSyncManager().getLastSyncResult();


    // A sync should not have been performed but the status should have been updated to SKIPPED.
    assertThat(lastSyncResult).isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS);
    // Make sure lost modules have been removed.
    assertThat(ModuleManager.getInstance(ideFrame.getProject()).getModules().length).isEqualTo(2);

    ideFrame.closeProject();
  }

  @Test
  public void gradleModelCachedNoImlsOnly() throws IOException {
    File projectDir = guiTest.setUpProject("SimpleApplication");

    // First time, open the project to sync.
    IdeFrameFixture ideFrame = guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    ideFrame.closeProject();

    new File(projectDir, ".idea/modules/app/SimpleApplication.app.iml").delete();
    new File(projectDir, ".idea/modules/SimpleApplication.iml").delete();
    // Second time, open the project expecting no sync.
    ideFrame = guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);

    ProjectSystemSyncManager.SyncResult lastSyncResult =
      ProjectSystemService.getInstance(ideFrame.getProject()).getProjectSystem().getSyncManager().getLastSyncResult();


    // A sync should not have been performed but the status should have been updated to SKIPPED.
    assertThat(lastSyncResult).isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS);
    // Make sure lost modules have been removed.
    assertThat(ModuleManager.getInstance(ideFrame.getProject()).getModules().length).isEqualTo(2);

    ideFrame.closeProject();
  }
}
