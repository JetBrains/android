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

import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.projectView.AndroidTreeStructureProvider;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.ChooseGradleHomeDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.net.HttpConfigurable;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.ANDROID_TEST_COMPILE;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.FilePaths.pathToUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.getCachedProjectData;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.PropertiesFiles.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesFiles.savePropertiesToFile;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture.findImportProjectDialog;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.google.common.truth.Truth.*;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.*;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static com.intellij.util.SystemProperties.getLineSeparator;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.jetbrains.android.AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY;
import static org.jetbrains.android.AndroidPlugin.getGuiTestSuiteState;
import static org.junit.Assert.*;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradleSyncTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String ANDROID_SDK_MANAGER_DIALOG_TITLE = "Android SDK Manager";
  private static final String GRADLE_SETTINGS_DIALOG_TITLE = "Gradle Settings";
  private static final String GRADLE_SYNC_DIALOG_TITLE = "Gradle Sync";

  private File myAndroidRepoPath;
  private File myAndroidRepoTempPath;

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Before
  public void restoreAndroidRepository() throws IOException {
    File androidExtrasPath = new File(IdeSdks.getInstance().getAndroidSdkPath(), join("extras", "android"));
    myAndroidRepoPath = new File(androidExtrasPath, "m2repository");
    myAndroidRepoTempPath = new File(androidExtrasPath, "m2repository.temp");

    if (!myAndroidRepoPath.isDirectory() && myAndroidRepoTempPath.isDirectory()) {
      rename(myAndroidRepoTempPath, myAndroidRepoPath);
    }
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void withMissingInterModuleDependencies() throws IOException {
    GradleExperimentalSettings.getInstance().SELECT_MODULES_ON_PROJECT_IMPORT = true;
    guiTest.importProject("ModuleDependencies");

    ModulesToImportDialogFixture projectSubsetDialog = ModulesToImportDialogFixture.find(guiTest.robot());
    projectSubsetDialog.setSelected("javalib1", false).clickOk();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    ContentFixture messages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    String expectedError = "Unable to find module with Gradle path ':javalib1' (needed by modules: 'androidlib1', 'app'.)";
    messages.findMessageContainingText(ERROR, expectedError);

    // Click "quick fix" to find and include any missing modules.
    MessageFixture quickFixMsg = messages.findMessageContainingText(INFO, "The missing modules may have been excluded");
    HyperlinkFixture quickFix = quickFixMsg.findHyperlink("Find and include missing modules");
    quickFix.click();

    guiTest.waitForBackgroundTasks();
    guiTest.ideFrame().getModule("javalib1"); // Fails if the module is not found.
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=183368
  public void withTestOnlyInterModuleDependencies() throws IOException {
    guiTest.importMultiModule();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Module appModule = guiTest.ideFrame().getModule("app");

    // Set a dependency on a module that does not exist.
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        runWriteCommandAction(
          ideFrame.getProject(), () -> {
            GradleBuildModel buildModel = GradleBuildModel.get(appModule);
            buildModel.dependencies().addModule(ANDROID_TEST_COMPILE, ":library3");
            buildModel.applyChanges();
          });
      }
    });

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

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void missingAppCompat() throws IOException {
    if (myAndroidRepoPath.isDirectory()) {
      // Instead of deleting the Android repo folder, we rename it and later on restore it in a @SetUp method, so if this fails, the SDK
      // will be in good state.
      delete(myAndroidRepoTempPath);
      rename(myAndroidRepoPath, myAndroidRepoTempPath);
    }
    assertAbout(file()).that(myAndroidRepoPath).doesNotExist();

    guiTest.importSimpleApplication();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    MessagesToolWindowFixture messagesToolWindow = ideFrame.getMessagesToolWindow();
    MessageFixture message = messagesToolWindow.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to resolve:"));

    HyperlinkFixture hyperlink = message.findHyperlink("Install Repository and sync project");
    hyperlink.clickAndContinue();

    // TODO implement a proper "SDK Quick Fix wizard" fixture that wraps a SdkQuickfixWizard
    DialogFixture quickFixDialog = findDialog(Matchers.byTitle(Dialog.class, "Install Missing Components"))
      .withTimeout(TimeUnit.MINUTES.toMillis(2)).using(guiTest.robot());

    JButtonFixture finish = quickFixDialog.button(withText("Finish"));

    // Wait until installation is finished. By then the "Finish" button will be enabled.
    Wait.seconds(1).expecting("Android Support Repository to be installed").until(finish::isEnabled);

    // Installation finished. Click finish to resync project.
    finish.click();

    ideFrame.waitForGradleProjectSyncToFinish();

    assertWithMessage("Android Support Repository must have been reinstalled").about(file()).that(myAndroidRepoPath).isDirectory();
  }

  @Test
  public void jdkNodeModificationInProjectView() throws IOException {
    guiTest.importSimpleApplication();

    Project project = guiTest.ideFrame().getProject();
    AndroidTreeStructureProvider treeStructureProvider = null;
    TreeStructureProvider[] treeStructureProviders = Extensions.getExtensions(TreeStructureProvider.EP_NAME, project);
    for (TreeStructureProvider current : treeStructureProviders) {
      if (current instanceof AndroidTreeStructureProvider) {
        treeStructureProvider = (AndroidTreeStructureProvider)current;
      }
    }

    List<AbstractTreeNode> changedNodes = Lists.newArrayList();
    treeStructureProvider.addChangeListener((parent, newChildren) -> changedNodes.add(parent));

    ProjectViewFixture projectView = guiTest.ideFrame().getProjectView();
    ProjectViewFixture.PaneFixture projectPane = projectView.selectProjectPane();
    ProjectViewFixture.NodeFixture externalLibrariesNode = projectPane.findExternalLibrariesNode();
    projectPane.expand();

    // 2 nodes should be changed: JDK (remove all children except rt.jar) and rt.jar (remove all children except packages 'java' and
    // 'javax'.
    Wait.seconds(1).expecting("'Project View' to be customized").until(() -> changedNodes.size() == 2);

    List<ProjectViewFixture.NodeFixture> libraryNodes = externalLibrariesNode.getChildren();

    ProjectViewFixture.NodeFixture jdkNode = null;
    // Find JDK node.
    for (ProjectViewFixture.NodeFixture node : libraryNodes) {
      if (node.isJdk()) {
        jdkNode = node;
        break;
      }
    }

    ProjectViewFixture.NodeFixture finalJdkNode = jdkNode;
    Wait.seconds(1).expecting("JDK node to be customized").until(() -> finalJdkNode.getChildren().size() == 1);

    // Now we verify that the JDK node has only these children:
    // - jdk
    //   - rt.jar
    //     - java
    //     - javax
    List<ProjectViewFixture.NodeFixture> jdkChildren = jdkNode.getChildren();
    assertThat(jdkChildren).hasSize(1);

    ProjectViewFixture.NodeFixture rtJarNode = jdkChildren.get(0);
    rtJarNode.requireDirectory("rt.jar");

    List<ProjectViewFixture.NodeFixture> rtJarChildren = rtJarNode.getChildren();
    assertThat(rtJarChildren).hasSize(2);

    rtJarChildren.get(0).requireDirectory("java");
    rtJarChildren.get(1).requireDirectory("javax");
  }

  // See https://code.google.com/p/android/issues/detail?id=75060
  @Test
  public void handlingOfOutOfMemoryErrors() throws IOException {
    guiTest.importSimpleApplication();

    // Force a sync failure by allocating not enough memory for the Gradle daemon.
    Properties gradleProperties = new Properties();
    gradleProperties.setProperty("org.gradle.jvmargs", "-XX:MaxHeapSize=8m");
    File gradlePropertiesFilePath = new File(guiTest.ideFrame().getProjectPath(), FN_GRADLE_PROPERTIES);
    savePropertiesToFile(gradleProperties, gradlePropertiesFilePath, null);

    guiTest.ideFrame().requestProjectSyncAndExpectFailure();

    MessagesToolWindowFixture messages = guiTest.ideFrame().getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Out of memory"));

    // Verify that at least we offer some sort of hint.
    MessagesToolWindowFixture.HyperlinkFixture hyperlink = message.findHyperlink("Read Gradle's configuration guide");
    hyperlink.requireUrl("http://www.gradle.org/docs/current/userguide/build_environment.html");
  }

  // See https://code.google.com/p/android/issues/detail?id=73872
  @Test
  public void handlingOfClassLoadingErrors() throws IOException {
    guiTest.importSimpleApplication();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.requestProjectSyncAndSimulateError(new ClassNotFoundException("com.android.utils.ILogger not found."));

    MessagesToolWindowFixture messages = ideFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Unable to load class"));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
    message.findHyperlink("Open Gradle Daemon documentation");
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=72556
  public void handlingOfUnexpectedEndOfBlockData() throws IOException {
    guiTest.importSimpleApplication();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.requestProjectSyncAndSimulateError("unexpected end of block data");

    MessagesToolWindowFixture messages = ideFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("An unexpected I/O error occurred."));

    message.findHyperlink("Build Project");
    message.findHyperlink("Open Android SDK Manager");
  }

  @Test
  @Ignore("http://b.android.com/218578")
  public void gradleDslMethodNotFoundInBuildFile() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    File topLevelBuildFile = new File(ideFrame.getProjectPath(), FN_BUILD_GRADLE);
    assertAbout(file()).that(topLevelBuildFile).isFile();
    String content = "asdf()" + getLineSeparator() + loadFile(topLevelBuildFile);
    writeToFile(topLevelBuildFile, content);

    ideFrame.requestProjectSyncAndExpectFailure();

    ContentFixture gradleSyncMessages = ideFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = gradleSyncMessages.findMessage(ERROR, firstLineStartingWith("Gradle DSL method not found: 'asdf()'"));

    EditorFixture editor = ideFrame.getEditor();
    editor.close();

    // Verify that at least we offer some sort of hint.
    message.findHyperlink("Open Gradle wrapper file");
  }

  @Test
  @Ignore("http://b.android.com/218578")
  public void gradleDslMethodNotFoundInSettingsFile() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    File settingsFile = new File(ideFrame.getProjectPath(), FN_SETTINGS_GRADLE);
    assertAbout(file()).that(settingsFile).isFile();
    writeToFile(settingsFile, "incude ':app'");

    ideFrame.requestProjectSyncAndExpectFailure();

    ContentFixture gradleSyncMessages = ideFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = gradleSyncMessages.findMessage(ERROR, firstLineStartingWith("Could not find method incude() for arguments"));

    // Ensure the error message contains the location of the error.
    message.requireLocation(settingsFile, 1);
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=76797
  public void handlingOfZipFileOpeningError() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    ideFrame.requestProjectSyncAndSimulateError("error in opening zip file");

    MessagesToolWindowFixture messages = ideFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to open zip file."));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=75520
  public void connectionPermissionDeniedError() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    ideFrame.requestProjectSyncAndSimulateError(new SocketException("Permission denied: connect"));

    MessagesToolWindowFixture messages = ideFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Connection to the Internet denied."));

    HyperlinkFixture hyperlink = message.findHyperlink("More details (and potential fix)");
    hyperlink.requireUrl("http://tools.android.com/tech-docs/project-sync-issues-android-studio");
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=76984
  public void daemonContextMismatchError() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    String failure = "The newly created daemon process has a different context than expected.\n" +
                     "It won't be possible to reconnect to this daemon. Context mismatch: \n" +
                     "Java home is different.\n" +
                     "javaHome=c:\\Program Files\\Java\\jdk,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=7868,idleTimeout=null]\n" +
                     "javaHome=C:\\Program Files\\Java\\jdk\\jre,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=4792,idleTimeout=10800000]";
    ideFrame.requestProjectSyncAndSimulateError(failure);
    MessagesToolWindowFixture messages = ideFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("The newly created daemon"));

    message.findHyperlink("Open JDK Settings");
  }

  @Test
  public void updatingGradleVersionWithLocalDistribution() throws IOException {
    File unsupportedGradleHome = getUnsupportedGradleHomeOrSkipTest();
    File gradleHomePath = getGradleHomePathOrSkipTest();

    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    ideFrame.deleteGradleWrapper().useLocalGradleDistribution(unsupportedGradleHome).requestProjectSync();

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

    ideFrame.deleteGradleWrapper().useLocalGradleDistribution(unsupportedGradleHome).requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    ideFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    ideFrame.waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  // See https://code.google.com/p/android/issues/detail?id=74842
  @Test
  public void withPrematureEndOfContentLength() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    // Simulate this Gradle error.
    String failure = "Premature end of Content-Length delimited message body (expected: 171012; received: 50250.";
    ideFrame.requestProjectSyncAndSimulateError(failure);

    String prefix = "Gradle's dependency cache seems to be corrupt or out of sync";
    MessagesToolWindowFixture messages = ideFrame.getMessagesToolWindow();

    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith(prefix));
    HyperlinkFixture quickFix = message.findHyperlink("Re-download dependencies and sync project (requires network)");
    quickFix.click();

    ideFrame.waitForGradleProjectSyncToFinish();

    // This is the only way we can at least know that we pass the right command-line option.
    String[] commandLineOptions = ApplicationManager.getApplication().getUserData(GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY);
    assertThat(commandLineOptions).asList().contains("--refresh-dependencies");
  }

  // See https://code.google.com/p/android/issues/detail?id=74259
  @Test
  public void withCentralBuildDirectoryInRootModule() throws IOException {
    // In issue 74259, project sync fails because the "app" build directory is set to "CentralBuildDirectory/central/build", which is
    // outside the content root of the "app" module.
    String projectDirName = "CentralBuildDirectory";
    File projectPath = new File(getProjectCreationDirPath(), projectDirName);

    // The bug appears only when the central build folder does not exist.
    File centralBuildDirPath = new File(projectPath, join("central", "build"));
    File centralBuildParentDirPath = centralBuildDirPath.getParentFile();
    delete(centralBuildParentDirPath);

    guiTest.importProjectAndWaitForProjectSyncToFinish(projectDirName);
    Module app = guiTest.ideFrame().getModule("app");

    // Now we have to make sure that if project import was successful, the build folder (with custom path) is excluded in the IDE (to
    // prevent unnecessary file indexing, which decreases performance.)
    File[] excludeFolderPaths = GuiQuery.getNonNull(
      () -> {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(app);
        ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        try {
          ContentEntry[] contentEntries = rootModel.getContentEntries();
          ContentEntry parent = findParentContentEntry(centralBuildDirPath, contentEntries);

          List<File> paths = Lists.newArrayList();

          for (ExcludeFolder excluded : parent.getExcludeFolders()) {
            String path = urlToPath(excluded.getUrl());
            if (isNotEmpty(path)) {
              paths.add(new File(toSystemDependentName(path)));
            }
          }
          return paths.toArray(new File[paths.size()]);
        }
        finally {
          rootModel.dispose();
        }
      });

    assertThat(excludeFolderPaths).isNotEmpty();

    boolean isExcluded = false;
    for (File path : notNullize(excludeFolderPaths)) {
      if (isAncestor(centralBuildParentDirPath, path, true)) {
        isExcluded = true;
        break;
      }
    }

    assertTrue(String.format("Folder '%1$s' should be excluded", centralBuildDirPath.getPath()), isExcluded);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/592")
  @Test
  public void withProjectWithoutWrapper() throws IOException {
    GradleExperimentalSettings settings = GradleExperimentalSettings.getInstance();
    settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;
    settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 5;

    guiTest.copyProjectBeforeOpening("AarDependency");

    File projectPath = guiTest.getProjectPath();
    IdeFrameFixture.deleteWrapper(projectPath);
    guiTest.cleanUpProjectForImport(projectPath);

    VirtualFile toSelect = findFileByIoFile(projectPath, true);

    // Import project
    WelcomeFrameFixture welcomeFrame = WelcomeFrameFixture.find(guiTest.robot());
    welcomeFrame.importProject();
    FileChooserDialogFixture importProjectDialog = findImportProjectDialog(guiTest.robot());
    importProjectDialog.select(toSelect).clickOk();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    welcomeFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  // See https://code.google.com/p/android/issues/detail?id=74341
  @Test
  public void editorShouldFindAppCompatStyle() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("AarDependency");
    EditorFixture editor = guiTest.ideFrame().getEditor();

    editor.open("app/src/main/res/values/strings.xml", Tab.EDITOR);
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }

  @Ignore("http://b.android.com/222226")
  @Test
  public void moduleSelectionOnImport() throws IOException {
    GradleExperimentalSettings.getInstance().SELECT_MODULES_ON_PROJECT_IMPORT = true;
    guiTest.importProject("Flavoredlib");

    ModulesToImportDialogFixture projectSubsetDialog = ModulesToImportDialogFixture.find(guiTest.robot());
    projectSubsetDialog.setSelected("lib", false).clickOk();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.waitForGradleProjectSyncToFinish();

    // Verify that "lib" (which was unchecked in the "Select Modules to Include" dialog) is not a module.
    assertThat(ideFrame.getModuleNames()).containsExactly("Flavoredlib", "app");

    // subsequent project syncs should respect module selection
    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
    assertThat(ideFrame.getModuleNames()).containsExactly("Flavoredlib", "app");
  }

  @Ignore("http://ag/1519100")
  @Test
  public void androidPluginAndGradleVersionCompatibility() throws IOException {
    guiTest.importMultiModule();

    // Set the plugin version to 1.0.0. This version is incompatible with Gradle 2.4.
    // We expect the IDE to warn the user about this incompatibility.
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    // @formatter:off
    ideFrame.updateGradleWrapperVersion("2.4")
            .updateAndroidGradlePluginVersion("1.0.0")
            .requestProjectSync()
            .waitForGradleProjectSyncToFinish();
    // @formatter:on

    ContentFixture syncMessages = ideFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("Gradle 2.4 requires Android Gradle plugin 1.2.0 (or newer)"));
  }

  // See https://code.google.com/p/android/issues/detail?id=165576
  @Test
  public void javaModelSerialization() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish().closeProject();

    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");

    LibraryTable libraryTable = ProjectLibraryTable.getInstance(guiTest.ideFrame().getProject());
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

  // See https://code.google.com/p/android/issues/detail?id=169778
  @Test
  public void javaToAndroidModuleDependencies() throws IOException {
    guiTest.importMultiModule();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Module library3 = ideFrame.getModule("library3");
    assertNull(AndroidFacet.getInstance(library3));

    File library3BuildFile = new File(ideFrame.getProjectPath(), join("library3", FN_BUILD_GRADLE));
    assertAbout(file()).that(library3BuildFile).isFile();
    appendToFile(library3BuildFile, "dependencies { compile project(':app') }");

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(library3);
    // Verify that the module "library3" doesn't depend on module "app"
    ModuleOrderEntry moduleDependency = null;
    for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        moduleDependency = (ModuleOrderEntry)orderEntry;
        break;
      }
    }

    assertNull(moduleDependency);

    ContentFixture syncMessages = ideFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message =
      syncMessages.findMessage(WARNING, firstLineStartingWith("Ignoring dependency of module 'app' on module 'library3'."));

    // Verify if the error message's link goes to the build file.
    VirtualFile buildFile = getGradleBuildFile(library3);
    message.requireLocation(new File(buildFile.getPath()), 0);
  }

  // See https://code.google.com/p/android/issues/detail?id=73087
  @Test
  public void withUserDefinedLibraryAttachments() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");

    File javadocJarPath = new File(guiTest.getProjectPath(), "fake-javadoc.jar");
    try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(javadocJarPath)))) {
      zos.putNextEntry(new ZipEntry("allclasses-frame.html"));
      zos.putNextEntry(new ZipEntry("allclasses-noframe.html"));
    }
    refreshFiles();

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    LibraryPropertiesDialogFixture propertiesDialog = ideFrame.showPropertiesForLibrary("guava-18.0");
    propertiesDialog.addAttachment(javadocJarPath).clickOk();

    guiTest.waitForBackgroundTasks();

    String javadocJarUrl = pathToUrl(javadocJarPath.getPath());

    // Verify that the library has the Javadoc attachment we just added.
    LibraryFixture library = propertiesDialog.getLibrary();
    library.requireJavadocUrls(javadocJarUrl);

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    // Verify that the library still has the Javadoc attachment after sync.
    library = propertiesDialog.getLibrary();
    library.requireJavadocUrls(javadocJarUrl);
  }

  // See https://code.google.com/p/android/issues/detail?id=169743
  // JVM settings for Gradle should be cleared before any invocation to Gradle.
  @Test
  public void shouldClearJvmArgsOnSyncAndBuild() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Project project = ideFrame.getProject();

    GradleProperties gradleProperties = new GradleProperties(project);
    gradleProperties.clear();
    gradleProperties.save();

    VirtualFile gradlePropertiesFile = findFileByIoFile(gradleProperties.getPath(), true);
    ideFrame.getEditor().open(gradlePropertiesFile, Tab.DEFAULT);

    String jvmArgs = "-Xmx2048m";
    ideFrame.setGradleJvmArgs(jvmArgs);

    ideFrame.requestProjectSync();

    // Copy JVM args to gradle.properties file.
    ideFrame.findMessageDialog(GRADLE_SETTINGS_DIALOG_TITLE).clickYes();

    // Verify JVM args were removed from IDE's Gradle settings.
    ideFrame.waitForGradleProjectSyncToFinish();
    assertNull(GradleSettings.getInstance(project).getGradleVmOptions());

    // Verify JVM args were copied to gradle.properties file
    refreshFiles();

    gradleProperties = new GradleProperties(project);
    assertEquals(jvmArgs, gradleProperties.getJvmArgs());
  }

  // Verifies that the IDE, during sync, asks the user to copy IDE proxy settings to gradle.properties, if applicable.
  // See https://code.google.com/p/android/issues/detail?id=65325
  @Test
  public void withIdeProxySettings() throws IOException {
    System.getProperties().setProperty("show.do.not.copy.http.proxy.settings.to.gradle", "true");

    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    File gradlePropertiesPath = new File(ideFrame.getProjectPath(), "gradle.properties");
    createIfNotExists(gradlePropertiesPath);

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

    ideFrame.waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish();

    // Verify gradle.properties has proxy settings.
    assertAbout(file()).that(gradlePropertiesPath).isFile();

    Properties gradleProperties = getProperties(gradlePropertiesPath);
    assertEquals(host, gradleProperties.getProperty("systemProp.http.proxyHost"));
    assertEquals(String.valueOf(port), gradleProperties.getProperty("systemProp.http.proxyPort"));

    // Verifies that the "Do not show this dialog in the future" does not show up. If it does show up the test will timeout and fail.
    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
  }

  // Verifies that the IDE switches SDKs if the IDE and project SDKs are not the same.
  @Test
  public void sdkSwitch() throws IOException {
    File secondSdkPath = getFilePathPropertyOrSkipTest("second.android.sdk.path", "the path of a secondary Android SDK", true);

    getGuiTestSuiteState().setSkipSdkMerge(true);

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

  // Verifies that if syncing using cached model, and if the cached model is missing data, we fall back to a full Gradle sync.
  // See: https://code.google.com/p/android/issues/detail?id=160899
  @Test
  @Ignore("We will remove the cache in 2.3")
  public void withCacheMissingModules() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    // Remove a module from the cache.
    Project project = ideFrame.getProject();
    DataNode<ProjectData> cache = getCachedProjectData(project);

    List<DataNode<?>> cachedChildren = field("myChildren").ofType(new TypeRef<List<DataNode<?>>>() {}).in(cache).get();
    assertThat(cachedChildren.size()).isGreaterThan(1);

    DataNode<?> toRemove = null;
    for (DataNode<?> child : cachedChildren) {
      if (child.getData() instanceof ModuleData) {
        toRemove = child;
        break;
      }
    }
    cachedChildren.remove(toRemove);

    // Force the IDE to use cache for sync.
    getGuiTestSuiteState().setUseCachedGradleModelOnly(true);

    // Sync again, and a full sync should occur, since the cache is missing modules.
    // 'waitForGradleProjectSyncToFinish' will never finish and test will time out and fail if the IDE never gets notified that the sync
    // finished.
    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
  }

  // Verify that the IDE warns users about rendering issue when using plugin 1.2.0 to 1.2.2.
  // See https://code.google.com/p/android/issues/detail?id=170841
  @Ignore("http://ag/1261774")
  @Test
  public void withLayoutRenderingIssue() throws IOException {
    guiTest.importMultiModule();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    // @formatter:off
    ideFrame.updateGradleWrapperVersion("2.4")
            .updateAndroidGradlePluginVersion("1.2.0")
            .requestProjectSync()
            .waitForGradleProjectSyncToFinish();
    // @formatter:on

    ContentFixture syncMessages = ideFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(WARNING, firstLineStartingWith("Using an obsolete version of the Gradle plugin (1.2.0)"));
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

  // Verifies that sync does not fail and user is warned when a project contains an Android module without variants.
  // See https://code.google.com/p/android/issues/detail?id=170722
  @Test
  public void withAndroidProjectWithoutVariants() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    assertNotNull(AndroidFacet.getInstance(ideFrame.getModule("app")));

    File appBuildFile = new File(ideFrame.getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertAbout(file()).that(appBuildFile).isFile();

    // Remove all variants.
    appendToFile(appBuildFile, "android.variantFilter { variant -> variant.ignore = true }");

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    // Verify user was warned.
    ContentFixture syncMessages = ideFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("The module 'app' is an Android project without build variants"));

    // Verify AndroidFacet was removed.
    assertNull(AndroidFacet.getInstance(ideFrame.getModule("app")));
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

  @Test
  public void withPreReleasePlugin() throws IOException {
    guiTest.importMultiModule();
    guiTest.ideFrame().updateAndroidGradlePluginVersion("1.2.0-beta1").requestProjectSync().waitForGradleProjectSyncToFail();

    ContentFixture syncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message =
      syncMessages.findMessage(ERROR, firstLineStartingWith("Plugin is too old, please update to a more recent version"));
    // Verify that the "quick fix" is added.
    message.findHyperlink("Fix plugin version and sync project");
  }

  @Test
  public void syncDuringOfflineMode() throws IOException {
    String hyperlinkText = "Disable offline mode and sync project";

    guiTest.importSimpleApplication();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    File buildFile = new File(ideFrame.getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertAbout(file()).that(buildFile).isFile();
    appendToFile(buildFile, "dependencies { compile 'something:not:exists' }");

    GradleSettings gradleSettings = GradleSettings.getInstance(ideFrame.getProject());
    gradleSettings.setOfflineWork(true);

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
    MessagesToolWindowFixture messagesToolWindow = ideFrame.getMessagesToolWindow();
    MessageFixture message = messagesToolWindow.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to resolve:"));

    HyperlinkFixture hyperlink = message.findHyperlink(hyperlinkText);
    hyperlink.click();

    assertFalse(gradleSettings.isOfflineWork());
    ideFrame.waitForGradleProjectSyncToFinish();
    messagesToolWindow = ideFrame.getMessagesToolWindow();
    message = messagesToolWindow.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to resolve:"));

    try {
      message.findHyperlink(hyperlinkText);
      fail(hyperlinkText + " link still present");
    }
    catch (AssertionError e) {
      // After offline mode is disable, the previous hyperlink will disappear after next sync
      assertThat(e.getMessage()).contains("Failed to find URL");
      assertThat(e.getMessage()).contains(hyperlinkText);
    }
  }

  @Nullable
  private static LanguageLevel getJavaLanguageLevel(@NotNull Module module) {
    return LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
  }

  @Test
  @Ignore("http://b.android.com/218578")
  public void suggestUpgradingAndroidPlugin() throws IOException {
    String hyperlinkText = "Fix plugin version and sync project";

    guiTest.importMultiModule();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.updateGradleWrapperVersion("2.4").updateAndroidGradlePluginVersion("1.2.0");
    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    EditorFixture editor = ideFrame.getEditor();
    editor.open("app/build.gradle");
    editor.moveBetween("android {", "\n");
    editor.enterText("\nlatestDsl()");

    ideFrame.requestProjectSyncAndExpectFailure();

    ContentFixture messages = ideFrame.getMessagesToolWindow().getGradleSyncContent();
    String expectedError = "Gradle DSL method not found: 'latestDsl()'";
    MessageFixture message = messages.findMessageContainingText(ERROR, expectedError);
    HyperlinkFixture quickFix = message.findHyperlink(hyperlinkText);
    quickFix.clickAndContinue();

    // Sync still fails, because latestDsl() is made up, but the plugin version should have changed.
    ideFrame.waitForGradleProjectSyncToFail();

    // Check the top-level build.gradle got updated.
    GradleVersion newVersion = null;
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(ideFrame.getProject());
    if (androidPluginInfo != null) {
      newVersion = androidPluginInfo.getPluginVersion();
    }
    assertThat(newVersion.toString()).isEqualTo(GRADLE_PLUGIN_RECOMMENDED_VERSION);

    messages = ideFrame.getMessagesToolWindow().getGradleSyncContent();
    expectedError = "Gradle DSL method not found: 'latestDsl()'";
    message = messages.findMessageContainingText(ERROR, expectedError);
    try {
      message.findHyperlink(hyperlinkText);
      fail("There should be no link, now that the plugin is up to date.");
    }
    catch (NullPointerException e) {
      assertThat(e.getMessage()).contains("Failed to find URL");
      assertThat(e.getMessage()).contains(hyperlinkText);
    }
  }

  @Ignore
  @Test
  public void withInvalidJdk() throws IOException {
    guiTest.importSimpleApplication();

    File tempJdkDirectory = createTempDirectory("GradleSyncTest", "testSyncWithInvalidJdk", true);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ApplicationManager.getApplication().runWriteAction(() -> IdeSdks.getInstance().setJdkPath(tempJdkDirectory));
      }
    });
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.requestProjectSync().waitForGradleProjectSyncToFail();

    delete(tempJdkDirectory);
    ideFrame.requestProjectSyncAndExpectFailure();
  }

  @Test
  public void shouldUseLibrary() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Project project = ideFrame.getProject();

    // Make sure the library was added.
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    String libraryName = "org.apache.http.legacy-" + TestUtils.getLatestAndroidPlatform();
    Library library = libraryTable.getLibraryByName(libraryName);

    // Verify that the library has the right j
    VirtualFile[] jarFiles = library.getFiles(CLASSES);
    assertThat(jarFiles).asList().hasSize(1);
    VirtualFile jarFile = jarFiles[0];
    assertEquals("org.apache.http.legacy.jar", jarFile.getName());

    // Verify that the module depends on the library
    Module appModule = ideFrame.getModule("app");
    AtomicBoolean dependencyFound = new AtomicBoolean();
    new ReadAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
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
      }
    }.execute();
    assertTrue("Module app should depend on library '" + library.getName() + "'", dependencyFound.get());
  }

  @Test
  public void aarSourceAttachments() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Project project = ideFrame.getProject();

    Module appModule = ideFrame.getModule("app");

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        runWriteCommandAction(
          project, () -> {
            GradleBuildModel buildModel = GradleBuildModel.get(appModule);

            String newDependency = "com.mapbox.mapboxsdk:mapbox-android-sdk:0.7.4@aar";
            buildModel.dependencies().addArtifact(COMPILE, newDependency);
            buildModel.applyChanges();
          });
      }
    });

    ideFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    // Verify that the library has sources.
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    String libraryName = "mapbox-android-sdk-0.7.4";
    Library library = libraryTable.getLibraryByName(libraryName);
    VirtualFile[] files = library.getFiles(SOURCES);
    assertThat(files).asList().hasSize(1);
  }

  // https://code.google.com/p/android/issues/detail?id=185313
  @Test
  public void sdkCreationForAddons() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrame = guiTest.ideFrame();

    Project project = ideFrame.getProject();

    Module appModule = ideFrame.getModule("app");
    GradleBuildFile buildFile = GradleBuildFile.get(appModule);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        runWriteCommandAction(
          project, () -> buildFile.setValue(BuildFileKey.COMPILE_SDK_VERSION, "Google Inc.:Google APIs:24"));
      }
    });

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

  @Ignore("http://ag/1574084")
  @Test
  public void gradleModelCache() throws IOException {
    guiTest.importSimpleApplication();
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    File projectPath = ideFrameFixture.getProjectPath();
    ideFrameFixture.closeProject();

    AtomicBoolean syncSkipped = new AtomicBoolean(false);

    // Reopen project and verify that sync was skipped (i.e. model loaded from cache)
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
        Project project = projectManager.convertAndLoadProject(projectPath.getPath());
        GradleSyncState.subscribe(project, new GradleSyncListener.Adapter() {
          @Override
          public void syncSkipped(@NotNull Project project) {
            syncSkipped.set(true);
          }
        });
        projectManager.openProject(project);
      }
    });

    Wait.seconds(1).expecting("sync to be skipped").until(syncSkipped::get);
  }

  /**
   * Verify that the project syncs and gradle file updates after changing the minSdkVersion in the build.gradle file.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * <pre>
   *   Steps:
   *   1. Import a project.
   *   2. Open build.gradle file for the project and update the min sdk value to 23.
   *   3. Sync the project.
   *   Verify:
   *   Project syncs and minSdk version is updated.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void modifyMinSdkAndSync() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();
    // @formatter:off
    ideFrame.getEditor()
            .open("app/build.gradle")
            .select("minSdkVersion (19)")
            .enterText("23")
            .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
            .performAction("Sync Now")
            .waitForGradleProjectSyncToFinish();
    // @formatter:on
  }
}
