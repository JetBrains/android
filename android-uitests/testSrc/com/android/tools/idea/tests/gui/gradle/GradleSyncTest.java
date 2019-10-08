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
import static com.android.tools.idea.gradle.util.GradleProperties.getUserGradlePropertiesFile;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.tests.gui.framework.GuiTests.getFilePathPropertyOrSkipTest;
import static com.android.tools.idea.tests.gui.framework.GuiTests.getGradleHomePathOrSkipTest;
import static com.android.tools.idea.tests.gui.framework.GuiTests.getUnsupportedGradleHomeOrSkipTest;
import static com.android.tools.idea.tests.gui.framework.GuiTests.refreshFiles;
import static com.android.tools.idea.tests.gui.framework.GuiTests.skipTest;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.backupGlobalGradlePropertiesFile;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.restoreGlobalGradlePropertiesFile;
import static com.android.tools.idea.util.PropertiesFiles.getProperties;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.io.FileUtil.createIfNotExists;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.LibraryFixture;
import com.android.tools.idea.tests.gui.framework.fixture.LibraryPropertiesDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProxySettingsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.ChooseGradleHomeDialogFixture;
import com.android.tools.idea.ui.GuiTestingService;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.util.net.HttpConfigurable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class GradleSyncTest {
  @Nullable private File myBackupProperties;

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String ANDROID_SDK_MANAGER_DIALOG_TITLE = "Android SDK Manager";
  private static final String GRADLE_SETTINGS_DIALOG_TITLE = "Gradle Settings";
  private static final String GRADLE_SYNC_DIALOG_TITLE = "Gradle Sync";

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

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    for (OrderEntry entry : ModuleRootManager.getInstance(appModule).getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        if ("library3".equals(moduleOrderEntry.getModuleName())) {
          assertEquals(DependencyScope.TEST, moduleOrderEntry.getScope());
          return;
        }
      }
    }
    fail("No dependency for library3 found");
  }

  @Test
  public void updatingGradleVersionWithLocalDistribution() throws IOException {
    File unsupportedGradleHome = getUnsupportedGradleHomeOrSkipTest();
    File gradleHomePath = getGradleHomePathOrSkipTest();

    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    File wrapperDirPath = new File(ideFrame.getProjectPath(), SdkConstants.FD_GRADLE);
    delete(wrapperDirPath);
    ideFrame.useLocalGradleDistribution(unsupportedGradleHome).requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "Cancel" to use local distribution.
    ideFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickCancel();

    ChooseGradleHomeDialogFixture chooseGradleHomeDialog = ChooseGradleHomeDialogFixture.find(guiTest.robot());
    chooseGradleHomeDialog.chooseGradleHome(gradleHomePath).clickOk().requireNotShowing();

    ideFrame.waitForGradleProjectSyncToFinish();
  }

  @Test
  public void userFriendlyErrorWhenUsingUnsupportedVersionOfGradle() throws IOException {
    File unsupportedGradleHome = getUnsupportedGradleHomeOrSkipTest();

    guiTest.importMultiModule();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    File wrapperDirPath = new File(ideFrame.getProjectPath(), SdkConstants.FD_GRADLE);
    delete(wrapperDirPath);
    ideFrame.useLocalGradleDistribution(unsupportedGradleHome).requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    ideFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    ideFrame.waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(wrapperDirPath).named("Gradle wrapper").isDirectory();
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

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish().closeProject();

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

    assertThat(moduleDependency.getModuleName()).isEqualTo("library2");
  }

  // See https://code.google.com/p/android/issues/detail?id=73087
  @Ignore("b/37109081")
  @RunIn(TestGroup.UNRELIABLE)  // b/37109081
  @Test
  public void withUserDefinedLibraryAttachments() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");

    File javadocJarPath = guiTest.getProjectPath("fake-javadoc.jar");
    try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(javadocJarPath)))) {
      zos.putNextEntry(new ZipEntry("allclasses-frame.html"));
      zos.putNextEntry(new ZipEntry("allclasses-noframe.html"));
    }
    refreshFiles();

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    LibraryPropertiesDialogFixture propertiesDialog = ideFrame.showPropertiesForLibrary("guava-18.0");
    propertiesDialog.addAttachment(javadocJarPath).clickOk();

    guiTest.waitForBackgroundTasks();

    String javadocJarUrl = pathToIdeaUrl(javadocJarPath);

    // Verify that the library has the Javadoc attachment we just added.
    LibraryFixture library = propertiesDialog.getLibrary();
    library.requireJavadocUrls(javadocJarUrl);

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    // Verify that the library still has the Javadoc attachment after sync.
    library = propertiesDialog.getLibrary();
    library.requireJavadocUrls(javadocJarUrl);
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

    ideFrame.requestProjectSync();

    // Expect IDE to ask user to copy proxy settings.
    ProxySettingsDialogFixture proxyDialog = ProxySettingsDialogFixture.find(guiTest.robot());
    proxyDialog.setDoNotShowThisDialog(true);
    proxyDialog.clickOk();

    ideFrame.waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish(Wait.seconds(20));

    // Verify gradle.properties has proxy settings.
    gradlePropertiesFile = getUserGradlePropertiesFile();
    assertAbout(file()).that(gradlePropertiesFile).isFile();

    Properties gradleProperties = getProperties(gradlePropertiesFile);
    assertEquals(host, gradleProperties.getProperty("systemProp.http.proxyHost"));
    assertEquals(String.valueOf(port), gradleProperties.getProperty("systemProp.http.proxyPort"));

    // Verifies that the "Do not show this dialog in the future" does not show up. If it does show up the test will timeout and fail.
    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
  }

  // Verifies that the IDE switches SDKs if the IDE and project SDKs are not the same.
  @Test
  public void sdkSwitch() throws IOException {
    File secondSdkPath = getFilePathPropertyOrSkipTest("second.android.sdk.path", "the path of a secondary Android SDK", true);

    GuiTestingService.getInstance().getGuiTestSuiteState().setSkipSdkMerge(true);

    IdeSdks ideSdks = IdeSdks.getInstance();
    File originalSdkPath = ideSdks.getAndroidSdkPath();

    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    // Change the SDK in the project. We expect the IDE to have the same SDK as the project.
    LocalProperties localProperties = new LocalProperties(ideFrame.getProject());
    localProperties.setAndroidSdkPath(secondSdkPath);
    localProperties.save();

    ideFrame.requestProjectSync();

    MessagesFixture messages = ideFrame.findMessageDialog(ANDROID_SDK_MANAGER_DIALOG_TITLE);
    messages.click("Use Project's SDK");

    ideFrame.waitForGradleProjectSyncToFinish();

    assertThat(ideSdks.getAndroidSdkPath()).isEqualTo(secondSdkPath);

    // Set the project's SDK to be the original one. Now we will choose the IDE's SDK.
    localProperties = new LocalProperties(ideFrame.getProject());
    localProperties.setAndroidSdkPath(originalSdkPath);
    localProperties.save();

    ideFrame.requestProjectSync();

    messages = ideFrame.findMessageDialog(ANDROID_SDK_MANAGER_DIALOG_TITLE);
    messages.click("Use Android Studio's SDK");

    ideFrame.waitForGradleProjectSyncToFinish();

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
    assertEquals(JDK_1_8, getJavaLanguageLevel(javaLib));
  }

  @Nullable
  private static LanguageLevel getJavaLanguageLevel(@NotNull Module module) {
    return LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
  }

  @Test
  public void shouldUseLibrary() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Project project = ideFrame.getProject();

    // Make sure the library was added.
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    // Naming scheme follows "Gradle: " + name of the library. See LibraryDependency#setName method
    String libraryName = GradleConstants.SYSTEM_ID.getReadableName() + ": org.apache.http.legacy-" + TestUtils.getLatestAndroidPlatform();
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
    GradleBuildFile buildFile = GradleBuildFile.get(appModule);

    ApplicationManager.getApplication().invokeAndWait(() -> runWriteCommandAction(
      project, () -> buildFile.setValue(BuildFileKey.COMPILE_SDK_VERSION, "Google Inc.:Google APIs:24")));

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

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
    IdeFrameFixture ideFrame = guiTest.openProject(projectDir);
    ideFrame.waitForGradleProjectSyncToFinish();
    ideFrame.closeProject();
    // Second time, open the project expecting no sync.
    ideFrame = guiTest.openProject(projectDir);
    ideFrame.waitForGradleProjectSyncToFinish();

    ProjectSystemSyncManager.SyncResult lastSyncResult =
      ProjectSystemService.getInstance(ideFrame.getProject()).getProjectSystem().getSyncManager().getLastSyncResult();
    ideFrame.closeProject();

    assertThat(lastSyncResult).isEqualTo(ProjectSystemSyncManager.SyncResult.SKIPPED);
  }
}
