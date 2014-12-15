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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.projectView.AndroidTreeStructureProvider;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.AbstractContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.android.SdkConstants.*;
import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static com.android.tools.idea.gradle.parser.BuildFileKey.PLUGIN_VERSION;
import static com.android.tools.idea.gradle.service.notification.errors.OutdatedAppEngineGradlePluginErrorHandler.MARKER_TEXT;
import static com.android.tools.idea.gradle.service.notification.hyperlink.UpgradeAppenginePluginVersionHyperlink.*;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.android.tools.idea.tests.gui.gradle.GradleSyncUtil.findGradleSyncMessageDialog;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.util.SystemProperties.getLineSeparator;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.util.Strings.quote;
import static org.jetbrains.android.AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GradleSyncTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testSyncMissingAppCompat() throws IOException {
    File androidRepoPath = new File(DefaultSdks.getDefaultAndroidHome(), FileUtil.join("extras", "android", "m2repository"));
    assertThat(androidRepoPath).as("Android Support Repository must be installed before running this test").isDirectory();

    IdeFrameFixture projectFrame = openSimpleApplication();

    assertTrue("Android Support Repository deleted", FileUtil.delete(androidRepoPath));

    projectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    MessageFixture message =
      projectFrame.getMessagesToolWindow().getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to resolve:"));

    HyperlinkFixture hyperlink = message.findHyperlink("Install Repository and sync project");
    hyperlink.click(false);

    // TODO implement a proper "SDK Quick Fix wizard" fixture that wraps a SdkQuickfixWizard
    DialogFixture quickFixDialog = WindowFinder.findDialog(new GenericTypeMatcher<Dialog>(Dialog.class) {
      @Override
      protected boolean isMatching(Dialog dialog) {
        return "Install Missing Components".equals(dialog.getTitle());
      }
    }).withTimeout(SHORT_TIMEOUT.duration()).using(myRobot);

    // Accept license
    quickFixDialog.radioButton(new GenericTypeMatcher<JRadioButton>(JRadioButton.class) {
      @Override
      protected boolean isMatching(JRadioButton button) {
        return "Accept".equals(button.getText());
      }
    }).click();

    quickFixDialog.button(withText("Next")).click();
    final JButtonFixture finish = quickFixDialog.button(withText("Finish"));

    // Wait until installation is finished. By then the "Finish" button will be enabled.
    Pause.pause(new Condition("Android Support Repository is installed") {
      @Override
      public boolean test() {
        return GuiActionRunner.execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() {
            return finish.target.isEnabled();
          }
        });
      }
    }, LONG_TIMEOUT);

    // Installation finished. Click finish to resync project.
    finish.click();

    projectFrame.waitForGradleProjectSyncToFinish().waitForBackgroundTasksToFinish();

    assertThat(androidRepoPath).as("Android Support Repository must have been reinstalled").isDirectory();
  }

  @Test @IdeGuiTest
  public void testSyncDoesNotChangeDependenciesInBuildFiles() throws IOException {
    File projectPath = setUpProject("MultiModule", true, true, null);
    File appBuildFilePath = new File(projectPath, FileUtil.join("app", SdkConstants.FN_BUILD_GRADLE));
    assertThat(appBuildFilePath).isFile();
    long lastModified = appBuildFilePath.lastModified();
    openProject(projectPath);
    // See https://code.google.com/p/android/issues/detail?id=78628
    assertEquals(lastModified, appBuildFilePath.lastModified());
  }

  @Test @IdeGuiTest
  public void testJdkNodeModificationInProjectView() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    AndroidTreeStructureProvider treeStructureProvider = null;
    TreeStructureProvider[] treeStructureProviders = Extensions.getExtensions(TreeStructureProvider.EP_NAME, projectFrame.getProject());
    for (TreeStructureProvider current : treeStructureProviders) {
      if (current instanceof AndroidTreeStructureProvider) {
        treeStructureProvider = (AndroidTreeStructureProvider)current;
      }
    }

    assertNotNull(treeStructureProvider);
    final List<AbstractTreeNode> changedNodes = Lists.newArrayList();
    treeStructureProvider.addChangeListener(new AndroidTreeStructureProvider.ChangeListener() {
      @Override
      public void nodeChanged(@NotNull AbstractTreeNode parent, @NotNull Collection<AbstractTreeNode> newChildren) {
        changedNodes.add(parent);
      }
    });

    ProjectViewFixture projectView = projectFrame.getProjectView();
    ProjectViewFixture.PaneFixture projectPane = projectView.selectProjectPane();
    ProjectViewFixture.NodeFixture externalLibrariesNode = projectPane.findExternalLibrariesNode();
    projectPane.expand();

    Pause.pause(new Condition("Wait for 'Project View' to be customized") {
      @Override
      public boolean test() {
        // 2 nodes should be changed: JDK (remove all children except rt.jar) and rt.jar (remove all children except packages 'java' and
        // 'javax'.
        return changedNodes.size() == 2;
      }
    }, LONG_TIMEOUT);

    List<ProjectViewFixture.NodeFixture> libraryNodes = externalLibrariesNode.getChildren();

    ProjectViewFixture.NodeFixture jdkNode = null;
    // Find JDK node.
    for (ProjectViewFixture.NodeFixture node : libraryNodes) {
      if (node.isJdk()) {
        jdkNode = node;
        break;
      }
    }
    assertNotNull(jdkNode);

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

  @Test @IdeGuiTest
  public void testUnsupportedPluginVersion() throws IOException {
    // Open the project without updating the version of the plug-in
    IdeFrameFixture projectFrame = openSimpleApplication();

    final Project project = projectFrame.getProject();

    // Use old, unsupported plugin version.
    File buildFilePath = new File(project.getBasePath(), SdkConstants.FN_BUILD_GRADLE);
    final VirtualFile buildFile = findFileByIoFile(buildFilePath, true);
    assertNotNull(buildFile);
    WriteCommandAction.runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        new GradleBuildFile(buildFile, project).setValue(PLUGIN_VERSION, "0.12.+");
      }
    });

    // Use old, unsupported Gradle in the wrapper.
    File wrapperPropertiesFile = findWrapperPropertiesFile(project);
    assertNotNull(wrapperPropertiesFile);
    updateGradleDistributionUrl("1.12", wrapperPropertiesFile);

    GradleProjectSettings settings = GradleUtil.getGradleProjectSettings(project);
    assertNotNull(settings);
    settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);

    projectFrame.requestProjectSyncAndExpectFailure();

    AbstractContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    String errorPrefix = "The minimum supported version of the Android Gradle plugin";
    MessageFixture message = syncMessages.findMessage(ERROR, firstLineStartingWith(errorPrefix));

    MessagesToolWindowFixture.HyperlinkFixture hyperlink = message.findHyperlink("Fix plugin version");
    hyperlink.click(true);

    projectFrame.waitForGradleProjectSyncToFinish();
  }

  // See https://code.google.com/p/android/issues/detail?id=75060
  @Test @IdeGuiTest
  public void testHandlingOfOutOfMemoryErrors() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    // Force a sync failure by allocating not enough memory for the Gradle daemon.
    Properties gradleProperties = new Properties();
    gradleProperties.setProperty("org.gradle.jvmargs", "-XX:MaxHeapSize=8m");
    File gradlePropertiesFilePath = new File(projectFrame.getProjectPath(), FN_GRADLE_PROPERTIES);
    savePropertiesToFile(gradleProperties, gradlePropertiesFilePath, null);

    projectFrame.requestProjectSyncAndExpectFailure();

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Out of memory"));

    // Verify that at least we offer some sort of hint.
    MessagesToolWindowFixture.HyperlinkFixture hyperlink = message.findHyperlink("Read Gradle's configuration guide");
    hyperlink.requireUrl("http://www.gradle.org/docs/current/userguide/build_environment.html");
  }

  // See https://code.google.com/p/android/issues/detail?id=73872
  @Test @IdeGuiTest
  public void testHandlingOfClassLoadingErrors() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("Unable to load class 'com.android.utils.ILogger'");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Unable to load class"));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
    message.findHyperlink("Stop Gradle build processes (requires restart)");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=72556
  public void testHandlingOfUnexpectedEndOfBlockData() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("unexpected end of block data");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("An unexpected I/O error occurred."));

    message.findHyperlink("Build Project");
    message.findHyperlink("Open Android SDK Manager");
  }

  @Test @IdeGuiTest @Ignore
  // Reason for @Ignore: now that we use embedded Gradle it is difficult to tell if we should create the wrapper or not.
  // See https://code.google.com/p/android/issues/detail?id=66880
  public void testAutomaticCreationOfMissingWrapper() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.deleteGradleWrapper()
                .requestProjectSync()
                .waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=72294
  public void testSyncWithEmptyGradleSettingsFileInMultiModuleProject() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    createEmptyGradleSettingsFile(projectFrame.getProjectPath());

    // Sync should be successful for multi-module projects with an empty settings.gradle file.
    projectFrame.requestProjectSync()
                .waitForGradleProjectSyncToFinish();
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=76444
  public void testSyncWithEmptyGradleSettingsFileInSingleModuleProject() throws IOException {
    IdeFrameFixture projectFrame = importProject("Basic");

    createEmptyGradleSettingsFile(projectFrame.getProjectPath());

    // Sync should be successful for single-module projects with an empty settings.gradle file.
    projectFrame.requestProjectSync()
                .waitForGradleProjectSyncToFinish();
  }

  private static void createEmptyGradleSettingsFile(@NotNull File projectPath) throws IOException {
    File settingsFilePath = new File(projectPath, FN_SETTINGS_GRADLE);
    delete(settingsFilePath);
    writeToFile(settingsFilePath, " ");
    assertThat(settingsFilePath).isFile();
  }

  @Test @IdeGuiTest
  public void testGradleDslMethodNotFoundInBuildFile() throws IOException {
    final IdeFrameFixture projectFrame = openSimpleApplication();

    File topLevelBuildFile = new File(projectFrame.getProjectPath(), FN_BUILD_GRADLE);
    assertThat(topLevelBuildFile).isFile();
    String content = "asdf()" + getLineSeparator() + loadFile(topLevelBuildFile);
    writeToFile(topLevelBuildFile, content);

    projectFrame.requestProjectSyncAndExpectFailure();

    AbstractContentFixture gradleSyncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = gradleSyncMessages.findMessage(ERROR, firstLineStartingWith("Gradle DSL method not found: 'asdf()'"));

    final EditorFixture editor = projectFrame.getEditor();
    editor.close();

    Pause.pause(10, TimeUnit.SECONDS);

    // Verify that at least we offer some sort of hint.
    message.findHyperlink("Open Gradle wrapper file");
  }

  @Test @IdeGuiTest
  public void testGradleDslMethodNotFoundInSettingsFile() throws IOException {
    final IdeFrameFixture projectFrame = openSimpleApplication();

    File settingsFile = new File(projectFrame.getProjectPath(), FN_SETTINGS_GRADLE);
    assertThat(settingsFile).isFile();
    writeToFile(settingsFile, "incude ':app'");

    projectFrame.requestProjectSyncAndExpectFailure();

    AbstractContentFixture gradleSyncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = gradleSyncMessages.findMessage(ERROR, firstLineStartingWith("Gradle DSL method not found: 'incude()'"));

    // Ensure the error message contains the location of the error.
    message.requireLocation(settingsFile, 1);
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=76797
  public void testHandlingOfZipFileOpeningError() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("error in opening zip file");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to open zip file."));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=75520
  public void testConnectionPermissionDeniedError() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    String failure = "Connection to the Internet denied.";
    projectFrame.requestProjectSyncAndSimulateFailure(failure);

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith(failure));

    HyperlinkFixture hyperlink = message.findHyperlink("More details (and potential fix)");
    hyperlink.requireUrl("http://tools.android.com/tech-docs/project-sync-issues-android-studio");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=76984
  public void testDaemonContextMismatchError() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    String failure = "The newly created daemon process has a different context than expected.\n" +
                     "It won't be possible to reconnect to this daemon. Context mismatch: \n" +
                     "Java home is different.\n" +
                     "javaHome=c:\\Program Files\\Java\\jdk,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=7868,idleTimeout=null]\n" +
                     "javaHome=C:\\Program Files\\Java\\jdk\\jre,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=4792,idleTimeout=10800000]";
    projectFrame.requestProjectSyncAndSimulateFailure(failure);

    Pause.pause(10, TimeUnit.SECONDS);

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("The newly created daemon"));

    message.findHyperlink("Open JDK Settings");
  }

  @Test @IdeGuiTest
  public void testUpdateGradleVersionWithLocalDistribution() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.useLocalGradleDistribution(getUnsupportedGradleHome())
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "Cancel" to use local distribution.
    findGradleSyncMessageDialog(myRobot).clickCancel();

    String gradleHome = System.getProperty(SUPPORTED_GRADLE_HOME_PROPERTY);
    if (isEmpty(gradleHome)) {
      fail("Please specify the path of a local, Gradle 2.2.1 distribution using the system property "
           + quote(SUPPORTED_GRADLE_HOME_PROPERTY));
    }

    ChooseGradleHomeDialogFixture chooseGradleHomeDialog = ChooseGradleHomeDialogFixture.find(myRobot);
    chooseGradleHomeDialog.chooseGradleHome(new File(gradleHome))
                          .clickOk()
                          .requireNotShowing();

    projectFrame.waitForGradleProjectSyncToFinish();
  }

  @Test @IdeGuiTest
  public void testShowUserFriendlyErrorWhenUsingUnsupportedVersionOfGradle() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution(getUnsupportedGradleHome())
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "Cancel" to use local distribution.
    findGradleSyncMessageDialog(myRobot).clickCancel();

    ChooseGradleHomeDialogFixture chooseGradleHomeDialog = ChooseGradleHomeDialogFixture.find(myRobot);
    chooseGradleHomeDialog.clickCancel();

    projectFrame.waitForGradleProjectSyncToFail();

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessagesToolWindowFixture.MessageFixture msg =
      messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Gradle version 2.2 is required."));
    msg.findHyperlink("Migrate to Gradle wrapper and sync project").click(true);

    projectFrame.waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  @Test @IdeGuiTest
  public void testCreateWrapperWhenLocalDistributionPathIsNotSet() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution("")
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    findGradleSyncMessageDialog(myRobot).clickOk();

    projectFrame.waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  @Test @IdeGuiTest
  public void testCreateWrapperWhenLocalDistributionPathDoesNotExist() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    File nonExistingDirPath = new File(SystemProperties.getUserHome(), UUID.randomUUID().toString());
    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution(nonExistingDirPath.getPath())
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    findGradleSyncMessageDialog(myRobot).clickOk();

    projectFrame.waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  @Test @IdeGuiTest
  public void testOutdatedAppEnginePlugin() throws IOException {
    String[] appenginePluginNames = {APPENGINE_PLUGIN_NAME, APPENGINE_PLUGIN_GROUP_ID + ":appengine-java-sdk:",
      APPENGINE_PLUGIN_GROUP_ID + ":appengine-endpoints:", APPENGINE_PLUGIN_GROUP_ID +":appengine-endpoints-deps:"};

    IdeFrameFixture projectFrame = openProject("OutdatedAppEnginePlugin");

    VirtualFile backendBuildFile = projectFrame.findFileByRelativePath("backend/build.gradle", true);
    Document document = FileDocumentManager.getInstance().getDocument(backendBuildFile);
    assertNotNull(document);

    Project project = projectFrame.getProject();
    Computable<String> appengineOutdatedPluginVersionTask = new Computable<String>() {
      @Override
      public String compute() {
        return "1.9.4";
      }
    };
    for (String pluginName : appenginePluginNames) {
      updateGradleDependencyVersion(project, document, pluginName, appengineOutdatedPluginVersionTask);
    }

    projectFrame.requestProjectSyncAndExpectFailure();

    // Check that sync output has an 'update AppEngine plugin version' link.
    AbstractContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture msg = syncMessages.findMessage(ERROR, firstLineStartingWith(MARKER_TEXT));
    HyperlinkFixture hyperlink = msg.findHyperlink(AndroidBundle.message("android.gradle.link.appengine.outdated"));

    // Ensure that clicking 'AppEngine plugin version' link really updates *.gradle config.
    GradleCoordinate definition = getPluginDefinition(document.getText(), APPENGINE_PLUGIN_NAME);
    assertNotNull(definition);

    int compare = COMPARE_PLUS_HIGHER.compare(definition, REFERENCE_APPENGINE_COORDINATE);
    assertThat(compare).isLessThan(0);

    hyperlink.click(true);

    definition = getPluginDefinition(document.getText(), APPENGINE_PLUGIN_NAME);
    assertNotNull(definition);

    compare = COMPARE_PLUS_HIGHER.compare(definition, REFERENCE_APPENGINE_COORDINATE);
    assertThat(compare).isGreaterThanOrEqualTo(0);
  }

  // See https://code.google.com/p/android/issues/detail?id=74842
  @Test @IdeGuiTest
  public void testPrematureEndOfContentLength() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    // Simulate this Gradle error.
    final String failure = "Premature end of Content-Length delimited message body (expected: 171012; received: 50250.";
    projectFrame.requestProjectSyncAndSimulateFailure(failure);

    final String prefix = "Gradle's dependency cache seems to be corrupt or out of sync";
    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();

    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith(prefix));
    HyperlinkFixture hyperlink = message.findHyperlink("Re-download dependencies and sync project (requires network)");
    hyperlink.click(true);

    projectFrame.waitForGradleProjectSyncToFinish();

    // This is the only way we can at least know that we pass the right command-line option.
    String[] commandLineOptions = ApplicationManager.getApplication().getUserData(GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY);
    assertThat(commandLineOptions).contains("--refresh-dependencies");
  }

  // See https://code.google.com/p/android/issues/detail?id=74259
  @Test @IdeGuiTest
  public void testImportProjectWithCentralBuildDirectoryInRootModule() throws IOException {
    // In issue 74259, project sync fails because the "app" build directory is set to "CentralBuildDirectory/central/build", which is
    // outside the content root of the "app" module.
    String projectDirName = "CentralBuildDirectory";
    File projectPath = new File(getProjectCreationDirPath(), projectDirName);

    // The bug appears only when the central build folder does not exist.
    final File centralBuildDirPath = new File(projectPath, FileUtil.join("central", "build"));
    File centralBuildParentDirPath = centralBuildDirPath.getParentFile();
    delete(centralBuildParentDirPath);

    IdeFrameFixture ideFrame = importProject(projectDirName);
    final Module app = ideFrame.getModule("app");

    // Now we have to make sure that if project import was successful, the build folder (with custom path) is excluded in the IDE (to
    // prevent unnecessary file indexing, which decreases performance.)
    VirtualFile[] excludeFolders = GuiActionRunner.execute(new GuiQuery<VirtualFile[]>() {
      @Override
      protected VirtualFile[] executeInEDT() throws Throwable {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(app);
        ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        try {
          ContentEntry[] contentEntries = rootModel.getContentEntries();
          ContentEntry parent = findParentContentEntry(centralBuildDirPath, contentEntries);
          assertNotNull(parent);

          return parent.getExcludeFolderFiles();
        }
        finally {
          rootModel.dispose();
        }
      }
    });

    assertThat(excludeFolders).isNotEmpty();

    VirtualFile centralBuildDir = findFileByIoFile(centralBuildParentDirPath, true);
    assertNotNull(centralBuildDir);

    boolean isExcluded = false;
    for (VirtualFile folder : excludeFolders) {
      if (isAncestor(centralBuildDir, folder, true)) {
        isExcluded = true;
        break;
      }
    }

    assertTrue(isExcluded);
  }

  @Test @IdeGuiTest
  public void testSyncWithUnresolvedDependencies() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();
    testSyncWithUnresolvedAppCompat(projectFrame);
  }

  @Test @IdeGuiTest
  public void testSyncWithUnresolvedDependenciesWithAndroidGradlePluginOneDotZero() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    VirtualFile projectBuildFile = projectFrame.findFileByRelativePath("build.gradle", true);
    Document document = FileDocumentManager.getInstance().getDocument(projectBuildFile);
    assertNotNull(document);

    updateGradleDependencyVersion(projectFrame.getProject(), document, GRADLE_PLUGIN_NAME, new Computable<String>() {
      @Override
      public String compute() {
        return "1.0.0";
      }
    });

    testSyncWithUnresolvedAppCompat(projectFrame);
  }

  private static void testSyncWithUnresolvedAppCompat(@NotNull IdeFrameFixture projectFrame) {
    VirtualFile appBuildFile = projectFrame.findFileByRelativePath("app/build.gradle", true);
    Document document = FileDocumentManager.getInstance().getDocument(appBuildFile);
    assertNotNull(document);

    updateGradleDependencyVersion(projectFrame.getProject(), document, "com.android.support:appcompat-v7:", new Computable<String>() {
      @Override
      public String compute() {
        return "100.0.0";
      }
    });

    projectFrame.requestProjectSync();

    AbstractContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("Failed to resolve: com.android.support:appcompat-v7:"));
  }

  @NotNull
  private static String getUnsupportedGradleHome() {
    String unsupportedGradleHome = System.getProperty(UNSUPPORTED_GRADLE_HOME_PROPERTY);
    if (isEmpty(unsupportedGradleHome)) {
      fail("Please specify the path of a local, Gradle 2.1 distribution using the system property "
           + quote(UNSUPPORTED_GRADLE_HOME_PROPERTY));
    }
    return unsupportedGradleHome;
  }
}
