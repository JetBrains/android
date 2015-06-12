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

import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.projectView.AndroidTreeStructureProvider;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.IdeGuiTestSetup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import com.intellij.util.net.HttpConfigurable;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.android.AndroidPlugin.GuiTestSuiteState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer.pathToUrl;
import static com.android.tools.idea.gradle.parser.BuildFileKey.PLUGIN_VERSION;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture.findImportProjectDialog;
import static com.android.tools.idea.tests.gui.framework.fixture.FileFixture.getDocument;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.*;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static com.intellij.pom.java.LanguageLevel.*;
import static com.intellij.util.SystemProperties.getLineSeparator;
import static junit.framework.Assert.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.fest.swing.timing.Pause.pause;
import static org.jetbrains.android.AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY;
import static org.jetbrains.android.AndroidPlugin.getGuiTestSuiteState;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@IdeGuiTestSetup(skipSourceGenerationOnSync = true)
public class GradleSyncTest extends GuiTestCase {
  private static final String ANDROID_SDK_MANAGER_DIALOG_TITLE = "Android SDK Manager";
  private static final String GRADLE_SETTINGS_DIALOG_TITLE = "Gradle Settings";
  private static final String GRADLE_SYNC_DIALOG_TITLE = "Gradle Sync";

  private File myAndroidRepoPath;
  private File myAndroidRepoTempPath;

  @Before
  public void restoreAndroidRepository() throws IOException {
    File androidExtrasPath = new File(IdeSdks.getAndroidSdkPath(), join("extras", "android"));
    myAndroidRepoPath = new File(androidExtrasPath ,"m2repository");
    myAndroidRepoTempPath = new File(androidExtrasPath, "m2repository.temp");

    if (!myAndroidRepoPath.isDirectory() && myAndroidRepoTempPath.isDirectory()) {
      rename(myAndroidRepoTempPath, myAndroidRepoPath);
    }
  }

  @Test @IdeGuiTest
  public void testMissingInterModuleDependencies() throws IOException {
    GradleExperimentalSettings.getInstance().SELECT_MODULES_ON_PROJECT_IMPORT = true;
    File projectPath = importProject("ModuleDependencies");

    ConfigureProjectSubsetDialogFixture projectSubsetDialog = ConfigureProjectSubsetDialogFixture.find(myRobot);
    projectSubsetDialog.selectModule("javalib1", false)
                       .clickOk();

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectSyncToFinish();

    ContentFixture messages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    String expectedError = "Unable to find module with Gradle path ':javalib1' (needed by modules: 'androidlib1', 'app'.)";
    messages.findMessageContainingText(ERROR, expectedError);

    // Click "quick fix" to find and include any missing modules.
    MessageFixture quickFixMsg = messages.findMessageContainingText(INFO, "The missing modules may have been excluded");
    HyperlinkFixture quickFix = quickFixMsg.findHyperlink("Find and include missing modules");
    quickFix.click();

    projectFrame.waitForBackgroundTasksToFinish();
    projectFrame.getModule("javalib1"); // Fails if the module is not found.
  }

  @Test @IdeGuiTest
  public void testNonExistingInterModuleDependencies() throws IOException {
    final IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");

    Module appModule = projectFrame.getModule("app");
    final GradleBuildFile buildFile = GradleBuildFile.get(appModule);
    assertNotNull(buildFile);

    // Set a dependency on a module that does not exist.
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        new WriteCommandAction<Void>(projectFrame.getProject(), "Adding dependencies", buildFile.getPsiFile()) {
          @Override
          protected void run(@NotNull Result<Void> result) throws Throwable {
            final Dependency nonExisting = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.MODULE, ":fakeLibrary");
            List<BuildFileStatement> dependencies = Lists.newArrayList();
            dependencies.add(nonExisting);
            buildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
          }
        }.execute();
      }
    });

    projectFrame.requestProjectSyncAndExpectFailure();

    ContentFixture messages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    String expectedError = "Project with path ':fakeLibrary' could not be found";
    MessageFixture msg = messages.findMessageContainingText(ERROR, expectedError);
    msg.findHyperlink("Open File"); // Now it is possible to open the build.gradle where the missing dependency is declared.
  }

  @Test @IdeGuiTest
  public void testUserDefinedLibrarySources() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    Project project = projectFrame.getProject();

    String libraryName = "guava-18.0";

    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    Library library = libraryTable.getLibraryByName(libraryName);
    assertNotNull(library);

    String url = "jar://$USER_HOME$/fake-dir/fake-sources.jar!/";

    // add an extra source path.
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    libraryModel.addRoot(url, OrderRootType.SOURCES);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            libraryModel.commit();
          }
        });
      }
    });

    projectFrame.requestProjectSync().waitForBackgroundTasksToFinish();

    libraryTable = ProjectLibraryTable.getInstance(project);
    library = libraryTable.getLibraryByName(libraryName);
    assertNotNull(library);

    String[] urls = library.getUrls(OrderRootType.SOURCES);
    assertThat(urls).contains(url);
  }

  @Test @IdeGuiTest
  public void testSyncMissingAppCompat() throws IOException {
    if (myAndroidRepoPath.isDirectory()) {
      // Instead of deleting the Android repo folder, we rename it and later on restore it in a @SetUp method, so if this fails, the SDK
      // will be in good state.
      delete(myAndroidRepoTempPath);
      rename(myAndroidRepoPath, myAndroidRepoTempPath);
    }
    assertThat(myAndroidRepoPath).doesNotExist();

    IdeFrameFixture projectFrame = importSimpleApplication();

    projectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    MessageFixture message =
      projectFrame.getMessagesToolWindow().getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to resolve:"));

    HyperlinkFixture hyperlink = message.findHyperlink("Install Repository and sync project");
    hyperlink.clickAndContinue();

    // TODO implement a proper "SDK Quick Fix wizard" fixture that wraps a SdkQuickfixWizard
    DialogFixture quickFixDialog = findDialog(new GenericTypeMatcher<Dialog>(Dialog.class) {
      @Override
      protected boolean isMatching(@NotNull Dialog dialog) {
        return "Install Missing Components".equals(dialog.getTitle());
      }
    }).withTimeout(SHORT_TIMEOUT.duration()).using(myRobot);

    final JButtonFixture finish = quickFixDialog.button(withText("Finish"));

    // Wait until installation is finished. By then the "Finish" button will be enabled.
    pause(new Condition("Android Support Repository is installed") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() {
            return finish.target().isEnabled();
          }
        });
      }
    }, LONG_TIMEOUT);

    // Installation finished. Click finish to resync project.
    finish.click();

    projectFrame.waitForGradleProjectSyncToFinish().waitForBackgroundTasksToFinish();

    assertThat(myAndroidRepoPath).as("Android Support Repository must have been reinstalled").isDirectory();
  }

  @Test @IdeGuiTest
  public void testSyncDoesNotChangeDependenciesInBuildFiles() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    File appBuildFilePath = new File(projectFrame.getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertThat(appBuildFilePath).isFile();
    long lastModified = appBuildFilePath.lastModified();

    projectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
    // See https://code.google.com/p/android/issues/detail?id=78628
    assertEquals(lastModified, appBuildFilePath.lastModified());
  }

  @Test @IdeGuiTest
  public void testJdkNodeModificationInProjectView() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

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

    pause(new Condition("Wait for 'Project View' to be customized") {
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

  @Test @IdeGuiTest @Ignore // Removed minimum plugin version check. It is failing in some projects.
  public void testUnsupportedPluginVersion() throws IOException {
    // Open the project without updating the version of the plug-in
    IdeFrameFixture projectFrame = importSimpleApplication();

    final Project project = projectFrame.getProject();

    // Use old, unsupported plugin version.
    File buildFilePath = new File(project.getBasePath(), FN_BUILD_GRADLE);
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

    GradleProjectSettings settings = getGradleProjectSettings(project);
    assertNotNull(settings);
    settings.setDistributionType(DEFAULT_WRAPPED);

    projectFrame.requestProjectSyncAndExpectFailure();

    ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    String errorPrefix = "The minimum supported version of the Android Gradle plugin";
    MessageFixture message = syncMessages.findMessage(ERROR, firstLineStartingWith(errorPrefix));

    MessagesToolWindowFixture.HyperlinkFixture hyperlink = message.findHyperlink("Fix plugin version");
    hyperlink.click();

    projectFrame.waitForGradleProjectSyncToFinish();
  }

  // See https://code.google.com/p/android/issues/detail?id=75060
  @Test @IdeGuiTest @Ignore // Works only when executed individually
  public void testHandlingOfOutOfMemoryErrors() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

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
    IdeFrameFixture projectFrame = importSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("Unable to load class 'com.android.utils.ILogger'");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Unable to load class"));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
    message.findHyperlink("Open Gradle Daemon documentation");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=72556
  public void testHandlingOfUnexpectedEndOfBlockData() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("unexpected end of block data");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("An unexpected I/O error occurred."));

    message.findHyperlink("Build Project");
    message.findHyperlink("Open Android SDK Manager");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=66880
  public void testAutomaticCreationOfMissingWrapper() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    projectFrame.deleteGradleWrapper()
                .requestProjectSync()
                .waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=72294
  public void testSyncWithEmptyGradleSettingsFileInMultiModuleProject() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    createEmptyGradleSettingsFile(projectFrame.getProjectPath());

    // Sync should be successful for multi-module projects with an empty settings.gradle file.
    projectFrame.requestProjectSync()
                .waitForGradleProjectSyncToFinish();
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=76444
  public void testSyncWithEmptyGradleSettingsFileInSingleModuleProject() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("Basic");

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
    final IdeFrameFixture projectFrame = importSimpleApplication();

    File topLevelBuildFile = new File(projectFrame.getProjectPath(), FN_BUILD_GRADLE);
    assertThat(topLevelBuildFile).isFile();
    String content = "asdf()" + getLineSeparator() + loadFile(topLevelBuildFile);
    writeToFile(topLevelBuildFile, content);

    projectFrame.requestProjectSyncAndExpectFailure();

    ContentFixture gradleSyncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = gradleSyncMessages.findMessage(ERROR, firstLineStartingWith("Gradle DSL method not found: 'asdf()'"));

    final EditorFixture editor = projectFrame.getEditor();
    editor.close();

    // Verify that at least we offer some sort of hint.
    message.findHyperlink("Open Gradle wrapper file");
  }

  @Test @IdeGuiTest
  public void testGradleDslMethodNotFoundInSettingsFile() throws IOException {
    final IdeFrameFixture projectFrame = importSimpleApplication();

    File settingsFile = new File(projectFrame.getProjectPath(), FN_SETTINGS_GRADLE);
    assertThat(settingsFile).isFile();
    writeToFile(settingsFile, "incude ':app'");

    projectFrame.requestProjectSyncAndExpectFailure();

    ContentFixture gradleSyncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = gradleSyncMessages.findMessage(ERROR, firstLineStartingWith("Gradle DSL method not found: 'incude()'"));

    // Ensure the error message contains the location of the error.
    message.requireLocation(settingsFile, 1);
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=76797
  public void testHandlingOfZipFileOpeningError() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("error in opening zip file");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to open zip file."));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=75520
  public void testConnectionPermissionDeniedError() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

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
    IdeFrameFixture projectFrame = importSimpleApplication();

    String failure = "The newly created daemon process has a different context than expected.\n" +
                     "It won't be possible to reconnect to this daemon. Context mismatch: \n" +
                     "Java home is different.\n" +
                     "javaHome=c:\\Program Files\\Java\\jdk,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=7868,idleTimeout=null]\n" +
                     "javaHome=C:\\Program Files\\Java\\jdk\\jre,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=4792,idleTimeout=10800000]";
    projectFrame.requestProjectSyncAndSimulateFailure(failure);

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("The newly created daemon"));

    message.findHyperlink("Open JDK Settings");
  }

  @Test @IdeGuiTest
  public void testUpdateGradleVersionWithLocalDistribution() throws IOException {
    File unsupportedGradleHome = getUnsupportedGradleHome();
    File gradleHomePath = getGradleHomePath();
    if (unsupportedGradleHome == null || gradleHomePath == null) {
      skip("testUpdateGradleVersionWithLocalDistribution");
      return;
    }

    IdeFrameFixture projectFrame = importSimpleApplication();

    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution(unsupportedGradleHome)
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "Cancel" to use local distribution.
    projectFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickCancel();

    ChooseGradleHomeDialogFixture chooseGradleHomeDialog = ChooseGradleHomeDialogFixture.find(myRobot);
    chooseGradleHomeDialog.chooseGradleHome(gradleHomePath).clickOk()
                          .requireNotShowing();

    projectFrame.waitForGradleProjectSyncToFinish();
  }

  @Test @IdeGuiTest
  public void testShowUserFriendlyErrorWhenUsingUnsupportedVersionOfGradle() throws IOException {
    File unsupportedGradleHome = getUnsupportedGradleHome();
    if (unsupportedGradleHome == null) {
      skip("testShowUserFriendlyErrorWhenUsingUnsupportedVersionOfGradle");
      return;
    }

    IdeFrameFixture projectFrame = importSimpleApplication();
    projectFrame.deleteGradleWrapper().useLocalGradleDistribution(unsupportedGradleHome).requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    projectFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    projectFrame.waitForGradleProjectSyncToStart()
                .waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  @Test @IdeGuiTest
  public void testCreateWrapperWhenLocalDistributionPathIsNotSet() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution("")
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    projectFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    projectFrame.waitForGradleProjectSyncToStart()
                .waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  @Test @IdeGuiTest
  public void testCreateWrapperWhenLocalDistributionPathDoesNotExist() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    File nonExistingDirPath = new File(SystemProperties.getUserHome(), UUID.randomUUID().toString());
    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution(nonExistingDirPath).requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    projectFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    projectFrame.waitForGradleProjectSyncToStart()
                .waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  // See https://code.google.com/p/android/issues/detail?id=74842
  @Test @IdeGuiTest
  public void testPrematureEndOfContentLength() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    // Simulate this Gradle error.
    final String failure = "Premature end of Content-Length delimited message body (expected: 171012; received: 50250.";
    projectFrame.requestProjectSyncAndSimulateFailure(failure);

    final String prefix = "Gradle's dependency cache seems to be corrupt or out of sync";
    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();

    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith(prefix));
    HyperlinkFixture quickFix = message.findHyperlink("Re-download dependencies and sync project (requires network)");
    quickFix.click();

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
    final File centralBuildDirPath = new File(projectPath, join("central", "build"));
    File centralBuildParentDirPath = centralBuildDirPath.getParentFile();
    delete(centralBuildParentDirPath);

    IdeFrameFixture ideFrame = importProjectAndWaitForProjectSyncToFinish(projectDirName);
    final Module app = ideFrame.getModule("app");

    // Now we have to make sure that if project import was successful, the build folder (with custom path) is excluded in the IDE (to
    // prevent unnecessary file indexing, which decreases performance.)
    final File[] excludeFolderPaths = execute(new GuiQuery<File[]>() {
      @Override
      protected File[] executeInEDT() throws Throwable {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(app);
        ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        try {
          ContentEntry[] contentEntries = rootModel.getContentEntries();
          ContentEntry parent = findParentContentEntry(centralBuildDirPath, contentEntries);
          assertNotNull(parent);

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

  @Test @IdeGuiTest
  public void testSyncWithUnresolvedDependencies() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    testSyncWithUnresolvedAppCompat(projectFrame);
  }

  @Test @IdeGuiTest
  public void testSyncWithUnresolvedDependenciesWithAndroidGradlePluginOneDotZero() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    VirtualFile projectBuildFile = projectFrame.findFileByRelativePath("build.gradle", true);
    Document document = getDocument(projectBuildFile);
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
    Document document = getDocument(appBuildFile);
    assertNotNull(document);

    updateGradleDependencyVersion(projectFrame.getProject(), document, "com.android.support:appcompat-v7:", new Computable<String>() {
      @Override
      public String compute() {
        return "100.0.0";
      }
    });

    projectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("Failed to resolve: com.android.support:appcompat-v7:"));
  }

  @Test @IdeGuiTest
  public void testImportProjectWithoutWrapper() throws IOException {
    GradleExperimentalSettings settings = GradleExperimentalSettings.getInstance();
    settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;
    settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 5;

    File projectDirPath = copyProjectBeforeOpening("AarDependency");

    IdeFrameFixture.deleteWrapper(projectDirPath);

    cleanUpProjectForImport(projectDirPath);

    // Import project
    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.clickImportProjectButton();
    FileChooserDialogFixture importProjectDialog = findImportProjectDialog(myRobot);

    VirtualFile toSelect = findFileByIoFile(projectDirPath, true);
    assertNotNull(toSelect);

    importProjectDialog.select(toSelect).clickOk();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    welcomeFrame.findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    IdeFrameFixture projectFrame = findIdeFrame(projectDirPath);
    projectFrame.waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  // See https://code.google.com/p/android/issues/detail?id=74341
  @Test @IdeGuiTest
  public void testEditorFindsAppCompatStyle() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("AarDependency");

    String stringsXmlPath = "app/src/main/res/values/strings.xml";
    projectFrame.getEditor().open(stringsXmlPath, EditorFixture.Tab.EDITOR);

    FileFixture file = projectFrame.findExistingFileByRelativePath(stringsXmlPath);
    file.requireCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }

  @Test @IdeGuiTest
  public void testModuleSelectionOnImport() throws IOException {
    GradleExperimentalSettings.getInstance().SELECT_MODULES_ON_PROJECT_IMPORT = true;
    File projectPath = importProject("Flavoredlib");

    ConfigureProjectSubsetDialogFixture projectSubsetDialog = ConfigureProjectSubsetDialogFixture.find(myRobot);
    projectSubsetDialog.selectModule("lib", false)
                       .clickOk();

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectSyncToFinish();

    // Verify that "lib" (which was unchecked in the "Select Modules to Include" dialog) is not a module.
    assertThat(projectFrame.getModuleNames()).containsOnly("Flavoredlib", "app");

    // subsequent project syncs should respect module selection
    projectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
    assertThat(projectFrame.getModuleNames()).containsOnly("Flavoredlib", "app");
  }

  @Test @IdeGuiTest
  public void testLocalJarsAsModules() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("LocalJarsAsModules");
    Module localJarModule = projectFrame.getModule("localJarAsModule");

    // Module should be a Java module, not buildable (since it doesn't have source code).
    JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(localJarModule);
    assertNotNull(javaFacet);
    assertFalse(javaFacet.getConfiguration().BUILDABLE);

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(localJarModule);
    OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();

    // Verify that the module depends on the jar that it contains.
    LibraryOrderEntry libraryDependency = null;
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        libraryDependency = (LibraryOrderEntry)orderEntry;
        break;
      }
    }
    assertNotNull(libraryDependency);
    assertThat(libraryDependency.getLibraryName()).isEqualTo("localJarAsModule.local");
    assertTrue(libraryDependency.isExported());
  }

  @Test @IdeGuiTest
  public void testLocalAarsAsModules() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("LocalAarsAsModules");
    Module localAarModule = projectFrame.getModule("library-debug");

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

    Module appModule = projectFrame.getModule("app");
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
    assertTrue(libraryDependency.isExported());
  }

  @Test @IdeGuiTest
  public void testInterModuleDependencies() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");

    Module appModule = projectFrame.getModule("app");
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(appModule);

    // Verify that the module "app" depends on module "library"
    ModuleOrderEntry moduleDependency = null;
    for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        moduleDependency = (ModuleOrderEntry)orderEntry;
        break;
      }
    }

    assertNotNull(moduleDependency);
    assertThat(moduleDependency.getModuleName()).isEqualTo("library");
  }

  @Test @IdeGuiTest
  public void testAndroidPluginAndGradleVersionCompatibility() throws IOException {
    File gradleTwoDotFourHome = getGradleHomeFromSystemProperty("gradle.2.4.home", "2.4");
    if (gradleTwoDotFourHome == null) {
      skip("testAndroidPluginAndGradleVersionCompatibility");
      return;
    }

    IdeFrameFixture projectFrame = importSimpleApplication();

    // Set the plugin version to 1.0.0. This version is incompatible with Gradle 2.4.
    // We expect the IDE to warn the user about this incompatibility.
    projectFrame.updateAndroidModelVersion("1.0.0");

    projectFrame.useLocalGradleDistribution(gradleTwoDotFourHome)
                .requestProjectSync()
                .waitForGradleProjectSyncToFinish();

    ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("Gradle 2.4 requires Android Gradle plugin 1.2.0 (or newer)"));
  }

  // See https://code.google.com/p/android/issues/detail?id=165576
  @Test @IdeGuiTest
  public void testJavaModelSerialization() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");
    final File projectPath = projectFrame.getProjectPath();

    projectFrame.requestProjectSync()
                .waitForGradleProjectSyncToFinish();
    projectFrame.closeProject();

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
        projectManager.loadAndOpenProject(projectPath.getPath());
      }
    });

    projectFrame = findIdeFrame(projectPath);
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(projectFrame.getProject());
    // When serialization of Java model fails, libraries are not set up.
    // Here we confirm that serialization works, because the Java module has the dependency declared in its build.gradle file.
    assertThat(libraryTable.getLibraries()).hasSize(1);
  }

  // See https://code.google.com/p/android/issues/detail?id=167378
  @Test @IdeGuiTest
  public void testInterJavaModuleDependencies() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");
    projectFrame.requestProjectSync()
                .waitForGradleProjectSyncToFinish();

    Module library = projectFrame.getModule("library");
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(library);

    // Verify that the module "library" depends on module "library2"
    ModuleOrderEntry moduleDependency = null;
    for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        moduleDependency = (ModuleOrderEntry)orderEntry;
        break;
      }
    }

    assertNotNull(moduleDependency);
    assertThat(moduleDependency.getModuleName()).isEqualTo("library2");
  }

  // See https://code.google.com/p/android/issues/detail?id=73087
  @Test @IdeGuiTest
  public void testUserDefinedLibraryAttachments() throws IOException {
    File javadocJarPath = getFilePathProperty("guava.javadoc.jar.path", "the path of the Javadoc jar file for Guava", false);
    if (javadocJarPath == null) {
      skip("testUserDefinedLibraryAttachments");
      return;
    }

    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");
    LibraryPropertiesDialogFixture propertiesDialog = projectFrame.showPropertiesForLibrary("guava");
    propertiesDialog.addAttachment(javadocJarPath)
                    .clickOk();

    String javadocJarUrl = pathToUrl(javadocJarPath.getPath());

    // Verify that the library has the Javadoc attachment we just added.
    LibraryFixture library = propertiesDialog.getLibrary();
    library.requireJavadocUrls(javadocJarUrl);

    projectFrame.requestProjectSync()
                .waitForGradleProjectSyncToFinish();

    // Verify that the library still has the Javadoc attachment after sync.
    library = propertiesDialog.getLibrary();
    library.requireJavadocUrls(javadocJarUrl);
  }

  // See https://code.google.com/p/android/issues/detail?id=169743
  // JVM settings for Gradle should be cleared before any invocation to Gradle.
  @Test @IdeGuiTest
  public void testClearJvmArgsOnSyncAndBuild() throws IOException {
    final IdeFrameFixture projectFrame = importSimpleApplication();
    Project project = projectFrame.getProject();

    GradleProperties gradleProperties = new GradleProperties(project);
    gradleProperties.setJvmArgs("");

    String jvmArgs = "-Xmx2048m";
    projectFrame.setGradleJvmArgs(jvmArgs)
                .requestProjectSync();

    // Copy JVM args to gradle.properties file.
    projectFrame.findMessageDialog(GRADLE_SETTINGS_DIALOG_TITLE).clickYes();

    // Verify JVM args were removed from IDE's Gradle settings.
    projectFrame.waitForGradleProjectSyncToFinish();
    assertEquals("", GradleSettings.getInstance(project).getGradleVmOptions());

    // Verify JVM args were copied to gradle.properties file
    gradleProperties = new GradleProperties(project);
    assertEquals(jvmArgs, gradleProperties.getJvmArgs());

    projectFrame.setGradleJvmArgs(jvmArgs).invokeProjectMake(new Runnable() {
      @Override
      public void run() {
        // Copy JVM args to gradle.properties file.
        projectFrame.findMessageDialog(GRADLE_SETTINGS_DIALOG_TITLE).clickYes();
      }
    });
    assertEquals("", GradleSettings.getInstance(project).getGradleVmOptions());
  }

  // Verifies that the IDE, during sync, asks the user to copy IDE proxy settings to gradle.properties, if applicable.
  // See https://code.google.com/p/android/issues/detail?id=65325
  @Test @IdeGuiTest
  public void testWithIdeProxySettings() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    File gradlePropertiesPath = new File(projectFrame.getProjectPath(), "gradle.properties");
    createIfNotExists(gradlePropertiesPath);

    String host = "myproxy.test.com";
    int port = 443;

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;

    projectFrame.requestProjectSync();

    // Expect IDE to ask user to copy proxy settings.
    MessagesFixture message = projectFrame.findMessageDialog("Proxy Settings");
    message.clickYes();

    projectFrame.waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish();

    // Verify gradle.properties has proxy settings.
    assertThat(gradlePropertiesPath).isFile();

    Properties gradleProperties = getProperties(gradlePropertiesPath);
    assertEquals(host, gradleProperties.getProperty("systemProp.http.proxyHost"));
    assertEquals(String.valueOf(port), gradleProperties.getProperty("systemProp.http.proxyPort"));
  }

  @Test @IdeGuiTest
  public void testMismatchingEncodings() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    final Project project = projectFrame.getProject();

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        EncodingProjectManager encodings = EncodingProjectManager.getInstance(project);
        encodings.setDefaultCharsetName("ISO-8859-1");
      }
    });

    projectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    String expectedMessage = "The project encoding (ISO-8859-1) has been reset to the encoding specified in the Gradle build files (UTF-8).";
    ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(INFO, firstLineStartingWith(expectedMessage));

    assertEquals("UTF-8", EncodingProjectManager.getInstance(project).getDefaultCharsetName());
  }

  // Verifies that the IDE switches SDKs if the IDE and project SDKs are not the same.
  @Test @IdeGuiTest
  public void testSdkSwitch() throws IOException {
    File secondSdkPath = getFilePathProperty("second.android.sdk.path", "the path of a secondary Android SDK", true);
    if (secondSdkPath == null) {
      skip("testSdkSwitch");
      return;
    }

    getGuiTestSuiteState().setSkipSdkMerge(true);

    File originalSdkPath = IdeSdks.getAndroidSdkPath();
    assertNotNull(originalSdkPath);

    IdeFrameFixture projectFrame = importSimpleApplication();

    // Change the SDK in the project. We expect the IDE to have the same SDK as the project.
    LocalProperties localProperties = new LocalProperties(projectFrame.getProject());
    localProperties.setAndroidSdkPath(secondSdkPath);
    localProperties.save();

    projectFrame.requestProjectSync();

    MessagesFixture messages = projectFrame.findMessageDialog(ANDROID_SDK_MANAGER_DIALOG_TITLE);
    messages.click("Use Project's SDK");

    projectFrame.waitForGradleProjectSyncToFinish();

    assertThat(IdeSdks.getAndroidSdkPath()).isEqualTo(secondSdkPath);

    // Set the project's SDK to be the original one. Now we will choose the IDE's SDK.
    localProperties = new LocalProperties(projectFrame.getProject());
    localProperties.setAndroidSdkPath(originalSdkPath);
    localProperties.save();

    projectFrame.requestProjectSync();

    messages = projectFrame.findMessageDialog(ANDROID_SDK_MANAGER_DIALOG_TITLE);
    messages.click("Use Android Studio's SDK");

    projectFrame.waitForGradleProjectSyncToFinish();

    localProperties = new LocalProperties(projectFrame.getProject());
    assertThat(localProperties.getAndroidSdkPath()).isEqualTo(secondSdkPath);
  }

  // Verifies that if syncing using cached model, and if the cached model is missing data, we fall back to a full Gradle sync.
  // See: https://code.google.com/p/android/issues/detail?id=160899
  @Test @IdeGuiTest
  public void testWithCacheMissingModules() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    // Remove a module from the cache.
    Project project = projectFrame.getProject();
    DataNode<ProjectData> cache = getCachedProjectData(project);
    assertNotNull(cache);

    List<DataNode<?>> cachedChildren = field("myChildren").ofType(new TypeRef<List<DataNode<?>>>(){}).in(cache).get();
    assertNotNull(cachedChildren);
    assertThat(cachedChildren.size()).isGreaterThan(1);
    DataNode<?> toRemove = null;
    for (DataNode<?> child : cachedChildren) {
      if (child.getData() instanceof ModuleData) {
        toRemove = child;
        break;
      }
    }
    assertNotNull(toRemove);
    cachedChildren.remove(toRemove);

    // Force the IDE to use cache for sync.
    GuiTestSuiteState state = getGuiTestSuiteState();
    assertNotNull(state);
    state.setUseCachedGradleModelOnly(true);

    // Sync again, and a full sync should occur, since the cache is missing modules.
    // 'waitForGradleProjectSyncToFinish' will never finish and test will time out and fail if the IDE never gets notified that the sync
    // finished.
    projectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();
  }

  // Verify that the IDE warns users about rendering issue when using plugin 1.2.0 to 1.2.2.
  // See https://code.google.com/p/android/issues/detail?id=170841
  @Test @IdeGuiTest
  public void testModelWithLayoutRenderingIssue() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    projectFrame.updateAndroidModelVersion("1.2.0")
                .requestProjectSync().waitForGradleProjectSyncToFinish();

    ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(WARNING, firstLineStartingWith("Using an obsolete version of the Gradle plugin (1.2.0)"));
  }

  // Verifies that after making a change in a build.gradle file, the editor notification saying that sync is needed shows up. This wasn't
  // the case after a project import.
  // See https://code.google.com/p/android/issues/detail?id=171370
  @Test @IdeGuiTest
  public void testEditorNotificationsWhenSyncNeededAfterProjectImport() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();

    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/build.gradle").waitUntilErrorAnalysisFinishes().enterText("Hello World");

    projectFrame.requireEditorNotification(
      "Gradle files have changed since last project sync. " + "A project sync may be necessary for the IDE to work properly.");
  }

  // Verifies that sync does not fail and user is warned when a project contains an Android module without variants.
  // See https://code.google.com/p/android/issues/detail?id=170722
  @Test @IdeGuiTest
  public void testWithAndroidProjectWithoutVariants() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    Module appModule = projectFrame.getModule("app");
    assertNotNull(AndroidFacet.getInstance(appModule));

    File appBuildFile = new File(projectFrame.getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertThat(appBuildFile).isFile();

    // Remove all variants.
    appendToFile(appBuildFile, "android.variantFilter { variant -> variant.ignore = true }");

    projectFrame.requestProjectSync().waitForGradleProjectSyncToFinish();

    // Verify user was warned.
    ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("The module 'app' is an Android project without build variants"));

    // Verify AndroidFacet was removed.
    appModule = projectFrame.getModule("app");
    assertNull(AndroidFacet.getInstance(appModule));
  }

  @Test @IdeGuiTest
  public void testModuleLanguageLevel() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiModule");

    Module library = projectFrame.getModule("library");
    Module library2 = projectFrame.getModule("library2");
    Module app = projectFrame.getModule("app");

    assertEquals(JDK_1_6, getJavaLanguageLevel(library));
    assertEquals(JDK_1_5, getJavaLanguageLevel(library2));
    assertEquals(JDK_1_7, getJavaLanguageLevel(app));
  }

  @Test @IdeGuiTest(runWithMinimumJdkVersion = JavaSdkVersion.JDK_1_8)
  public void testModuleLanguageLevelWithJdk8() throws IOException {
    IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");
    Module javaLib = projectFrame.getModule("javaLib");
    assertEquals(JDK_1_7, getJavaLanguageLevel(javaLib));
  }

  @Nullable
  private static LanguageLevel getJavaLanguageLevel(@NotNull Module module) {
    return LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
  }
}
