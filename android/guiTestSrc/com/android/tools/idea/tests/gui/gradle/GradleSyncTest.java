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
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.projectView.AndroidTreeStructureProvider;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.ChooseGradleHomeDialogFixture;
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
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import com.intellij.util.net.HttpConfigurable;
import junit.framework.AssertionFailedError;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.android.AndroidPlugin.GuiTestSuiteState;
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

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.AndroidTestCaseHelper.getSystemPropertyOrEnvironmentVariable;
import static com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer.pathToUrl;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture.findImportProjectDialog;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
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
import static com.intellij.pom.java.LanguageLevel.*;
import static com.intellij.util.SystemProperties.getLineSeparator;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.jetbrains.android.AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY;
import static org.jetbrains.android.AndroidPlugin.getGuiTestSuiteState;
import static org.junit.Assert.*;

@BelongsToTestGroups({PROJECT_SUPPORT})
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
    File androidExtrasPath = new File(IdeSdks.getAndroidSdkPath(), join("extras", "android"));
    myAndroidRepoPath = new File(androidExtrasPath, "m2repository");
    myAndroidRepoTempPath = new File(androidExtrasPath, "m2repository.temp");

    if (!myAndroidRepoPath.isDirectory() && myAndroidRepoTempPath.isDirectory()) {
      rename(myAndroidRepoTempPath, myAndroidRepoPath);
    }
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testMissingInterModuleDependencies() throws IOException {
    GradleExperimentalSettings.getInstance().SELECT_MODULES_ON_PROJECT_IMPORT = true;
    guiTest.importProject("ModuleDependencies");

    ConfigureProjectSubsetDialogFixture projectSubsetDialog = ConfigureProjectSubsetDialogFixture.find(guiTest.robot());
    projectSubsetDialog.selectModule("javalib1", false).clickOk();

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
  public void testTestOnlyInterModuleDependencies() throws IOException {
    guiTest.importMultiModule();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/build.gradle").moveTo(editor.findOffset("^compile fileTree")).enterText("androidTestCompile project(':library3')\n");

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();
    Module appModule = guiTest.ideFrame().getModule("app");

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
  public void testNonExistingInterModuleDependencies() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ModuleDependencies");

    final Module appModule = guiTest.ideFrame().getModule("app");

    // Set a dependency on a module that does not exist.
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        runWriteCommandAction(guiTest.ideFrame().getProject(), new Runnable() {
          @Override
          public void run() {
            GradleBuildModel buildModel = GradleBuildModel.get(appModule);
            assertNotNull(buildModel);
            buildModel.dependencies().addModule(COMPILE, ":fakeLibrary");
            buildModel.applyChanges();
          }
        });
      }
    });

    guiTest.ideFrame().requestProjectSyncAndExpectFailure();

    ContentFixture messages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    String expectedError = "Project with path ':fakeLibrary' could not be found";
    MessageFixture msg = messages.findMessageContainingText(ERROR, expectedError);
    msg.findHyperlink("Open File"); // Now it is possible to open the build.gradle where the missing dependency is declared.
  }

  @Test
  public void testUserDefinedLibrarySources() throws IOException {
    guiTest.importSimpleApplication();
    Project project = guiTest.ideFrame().getProject();

    String libraryName = "guava-18.0";

    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    Library library = libraryTable.getLibraryByName(libraryName);
    assertNotNull(library);

    String url = "jar://$USER_HOME$/fake-dir/fake-sources.jar!/";

    // add an extra source path.
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    libraryModel.addRoot(url, SOURCES);

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

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    libraryTable = ProjectLibraryTable.getInstance(project);
    library = libraryTable.getLibraryByName(libraryName);
    assertNotNull(library);

    String[] urls = library.getUrls(SOURCES);
    assertThat(urls).contains(url);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testSyncMissingAppCompat() throws IOException {
    if (myAndroidRepoPath.isDirectory()) {
      // Instead of deleting the Android repo folder, we rename it and later on restore it in a @SetUp method, so if this fails, the SDK
      // will be in good state.
      delete(myAndroidRepoTempPath);
      rename(myAndroidRepoPath, myAndroidRepoTempPath);
    }
    assertThat(myAndroidRepoPath).doesNotExist();

    guiTest.importSimpleApplication();

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    MessageFixture message =
      guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to resolve:"));

    HyperlinkFixture hyperlink = message.findHyperlink("Install Repository and sync project");
    hyperlink.clickAndContinue();

    // TODO implement a proper "SDK Quick Fix wizard" fixture that wraps a SdkQuickfixWizard
    DialogFixture quickFixDialog = findDialog(new GenericTypeMatcher<Dialog>(Dialog.class) {
      @Override
      protected boolean isMatching(@NotNull Dialog dialog) {
        return "Install Missing Components".equals(dialog.getTitle());
      }
    }).withTimeout(TimeUnit.MINUTES.toMillis(2)).using(guiTest.robot());

    final JButtonFixture finish = quickFixDialog.button(withText("Finish"));

    // Wait until installation is finished. By then the "Finish" button will be enabled.
    Wait.minutes(2).expecting("Android Support Repository to be installed").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() {
            return finish.target().isEnabled();
          }
        });
      }
    });

    // Installation finished. Click finish to resync project.
    finish.click();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    assertThat(myAndroidRepoPath).as("Android Support Repository must have been reinstalled").isDirectory();
  }

  @Test
  public void testSyncDoesNotChangeDependenciesInBuildFiles() throws IOException {
    guiTest.importMultiModule();
    File appBuildFilePath = new File(guiTest.ideFrame().getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertThat(appBuildFilePath).isFile();
    long lastModified = appBuildFilePath.lastModified();

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();
    // See https://code.google.com/p/android/issues/detail?id=78628
    assertEquals(lastModified, appBuildFilePath.lastModified());
  }

  @Test
  public void testJdkNodeModificationInProjectView() throws IOException {
    guiTest.importSimpleApplication();

    AndroidTreeStructureProvider treeStructureProvider = null;
    TreeStructureProvider[] treeStructureProviders = Extensions.getExtensions(TreeStructureProvider.EP_NAME, guiTest.ideFrame().getProject());
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

    ProjectViewFixture projectView = guiTest.ideFrame().getProjectView();
    ProjectViewFixture.PaneFixture projectPane = projectView.selectProjectPane();
    ProjectViewFixture.NodeFixture externalLibrariesNode = projectPane.findExternalLibrariesNode();
    projectPane.expand();

    Wait.minutes(2).expecting("'Project View' to be customized").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        // 2 nodes should be changed: JDK (remove all children except rt.jar) and rt.jar (remove all children except packages 'java' and
        // 'javax'.
        return changedNodes.size() == 2;
      }
    });

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

    final ProjectViewFixture.NodeFixture finalJdkNode = jdkNode;
    Wait.seconds(30).expecting("JDK node to be customized").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        List<ProjectViewFixture.NodeFixture> jdkChildren = finalJdkNode.getChildren();
        return jdkChildren.size() == 1;
      }
    });

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
  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and when run individually from IDEA, Windows OK")
  public void testHandlingOfOutOfMemoryErrors() throws IOException {
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
  public void testHandlingOfClassLoadingErrors() throws IOException {
    guiTest.importSimpleApplication();

    guiTest.ideFrame().requestProjectSyncAndSimulateFailure("Unable to load class 'com.android.utils.ILogger'");

    MessagesToolWindowFixture messages = guiTest.ideFrame().getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Unable to load class"));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
    message.findHyperlink("Open Gradle Daemon documentation");
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=72556
  public void testHandlingOfUnexpectedEndOfBlockData() throws IOException {
    guiTest.importSimpleApplication();

    guiTest.ideFrame().requestProjectSyncAndSimulateFailure("unexpected end of block data");

    MessagesToolWindowFixture messages = guiTest.ideFrame().getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("An unexpected I/O error occurred."));

    message.findHyperlink("Build Project");
    message.findHyperlink("Open Android SDK Manager");
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=66880
  public void testAutomaticCreationOfMissingWrapper() throws IOException {
    guiTest.importSimpleApplication();
    guiTest.ideFrame().deleteGradleWrapper().requestProjectSync().waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=72294
  public void testSyncWithEmptyGradleSettingsFileInMultiModuleProject() throws IOException {
    guiTest.importSimpleApplication();

    createEmptyGradleSettingsFile(guiTest.ideFrame().getProjectPath());

    // Sync should be successful for multi-module projects with an empty settings.gradle file.
    guiTest.ideFrame().requestProjectSync();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=76444
  public void testSyncWithEmptyGradleSettingsFileInSingleModuleProject() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("Basic");

    createEmptyGradleSettingsFile(guiTest.ideFrame().getProjectPath());

    // Sync should be successful for single-module projects with an empty settings.gradle file.
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();
  }

  private static void createEmptyGradleSettingsFile(@NotNull File projectPath) throws IOException {
    File settingsFilePath = new File(projectPath, FN_SETTINGS_GRADLE);
    delete(settingsFilePath);
    writeToFile(settingsFilePath, " ");
    assertThat(settingsFilePath).isFile();

    // Refresh file content
    findFileByIoFile(settingsFilePath, true);
  }

  @Test
  public void testGradleDslMethodNotFoundInBuildFile() throws IOException {
    guiTest.importSimpleApplication();

    File topLevelBuildFile = new File(guiTest.ideFrame().getProjectPath(), FN_BUILD_GRADLE);
    assertThat(topLevelBuildFile).isFile();
    String content = "asdf()" + getLineSeparator() + loadFile(topLevelBuildFile);
    writeToFile(topLevelBuildFile, content);

    guiTest.ideFrame().requestProjectSyncAndExpectFailure();

    ContentFixture gradleSyncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = gradleSyncMessages.findMessage(ERROR, firstLineStartingWith("Gradle DSL method not found: 'asdf()'"));

    final EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.close();

    // Verify that at least we offer some sort of hint.
    message.findHyperlink("Open Gradle wrapper file");
  }

  @Test
  public void testGradleDslMethodNotFoundInSettingsFile() throws IOException {
    guiTest.importSimpleApplication();

    File settingsFile = new File(guiTest.ideFrame().getProjectPath(), FN_SETTINGS_GRADLE);
    assertThat(settingsFile).isFile();
    writeToFile(settingsFile, "incude ':app'");

    guiTest.ideFrame().requestProjectSyncAndExpectFailure();

    ContentFixture gradleSyncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = gradleSyncMessages.findMessage(ERROR, firstLineStartingWith("Gradle DSL method not found: 'incude()'"));

    // Ensure the error message contains the location of the error.
    message.requireLocation(settingsFile, 1);
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=76797
  public void testHandlingOfZipFileOpeningError() throws IOException {
    guiTest.importSimpleApplication();

    guiTest.ideFrame().requestProjectSyncAndSimulateFailure("error in opening zip file");

    MessagesToolWindowFixture messages = guiTest.ideFrame().getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to open zip file."));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=75520
  public void testConnectionPermissionDeniedError() throws IOException {
    guiTest.importSimpleApplication();

    String failure = "Connection to the Internet denied.";
    guiTest.ideFrame().requestProjectSyncAndSimulateFailure(failure);

    MessagesToolWindowFixture messages = guiTest.ideFrame().getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith(failure));

    HyperlinkFixture hyperlink = message.findHyperlink("More details (and potential fix)");
    hyperlink.requireUrl("http://tools.android.com/tech-docs/project-sync-issues-android-studio");
  }

  @Test
  // See https://code.google.com/p/android/issues/detail?id=76984
  public void testDaemonContextMismatchError() throws IOException {
    guiTest.importSimpleApplication();

    String failure = "The newly created daemon process has a different context than expected.\n" +
                     "It won't be possible to reconnect to this daemon. Context mismatch: \n" +
                     "Java home is different.\n" +
                     "javaHome=c:\\Program Files\\Java\\jdk,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=7868,idleTimeout=null]\n" +
                     "javaHome=C:\\Program Files\\Java\\jdk\\jre,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=4792,idleTimeout=10800000]";
    guiTest.ideFrame().requestProjectSyncAndSimulateFailure(failure);
    MessagesToolWindowFixture messages = guiTest.ideFrame().getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("The newly created daemon"));

    message.findHyperlink("Open JDK Settings");
  }

  @Test
  public void testUpdateGradleVersionWithLocalDistribution() throws IOException {
    File unsupportedGradleHome = getUnsupportedGradleHome();
    File gradleHomePath = getGradleHomePath();
    assume().that(unsupportedGradleHome).isNotNull();
    assume().that(gradleHomePath).isNotNull();

    guiTest.importSimpleApplication();

    guiTest.ideFrame().deleteGradleWrapper().useLocalGradleDistribution(unsupportedGradleHome).requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "Cancel" to use local distribution.
    guiTest.ideFrame().findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickCancel();

    ChooseGradleHomeDialogFixture chooseGradleHomeDialog = ChooseGradleHomeDialogFixture.find(guiTest.robot());
    chooseGradleHomeDialog.chooseGradleHome(gradleHomePath).clickOk().requireNotShowing();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  @Test
  public void testShowUserFriendlyErrorWhenUsingUnsupportedVersionOfGradle() throws IOException {
    File unsupportedGradleHome = getUnsupportedGradleHome();
    assume().that(unsupportedGradleHome).isNotNull();

    guiTest.importMultiModule();
    guiTest.ideFrame().deleteGradleWrapper().useLocalGradleDistribution(unsupportedGradleHome).requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    guiTest.ideFrame().findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    guiTest.ideFrame().waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  @Test
  public void testCreateWrapperWhenLocalDistributionPathIsNotSet() throws IOException {
    guiTest.importSimpleApplication();
    guiTest.ideFrame().deleteGradleWrapper().useLocalGradleDistribution("").requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    guiTest.ideFrame().findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();
    guiTest.ideFrame().waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  @Test
  public void testCreateWrapperWhenLocalDistributionPathDoesNotExist() throws IOException {
    guiTest.importSimpleApplication();

    File nonExistingDirPath = new File(SystemProperties.getUserHome(), UUID.randomUUID().toString());
    guiTest.ideFrame().deleteGradleWrapper().useLocalGradleDistribution(nonExistingDirPath).requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    guiTest.ideFrame().findMessageDialog(GRADLE_SYNC_DIALOG_TITLE).clickOk();

    guiTest.ideFrame().waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish().requireGradleWrapperSet();
  }

  // See https://code.google.com/p/android/issues/detail?id=74842
  @Test
  public void testPrematureEndOfContentLength() throws IOException {
    guiTest.importSimpleApplication();

    // Simulate this Gradle error.
    final String failure = "Premature end of Content-Length delimited message body (expected: 171012; received: 50250.";
    guiTest.ideFrame().requestProjectSyncAndSimulateFailure(failure);

    final String prefix = "Gradle's dependency cache seems to be corrupt or out of sync";
    MessagesToolWindowFixture messages = guiTest.ideFrame().getMessagesToolWindow();

    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith(prefix));
    HyperlinkFixture quickFix = message.findHyperlink("Re-download dependencies and sync project (requires network)");
    quickFix.click();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    // This is the only way we can at least know that we pass the right command-line option.
    String[] commandLineOptions = ApplicationManager.getApplication().getUserData(GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY);
    assertThat(commandLineOptions).contains("--refresh-dependencies");
  }

  // See https://code.google.com/p/android/issues/detail?id=74259
  @Test
  public void testImportProjectWithCentralBuildDirectoryInRootModule() throws IOException {
    // In issue 74259, project sync fails because the "app" build directory is set to "CentralBuildDirectory/central/build", which is
    // outside the content root of the "app" module.
    String projectDirName = "CentralBuildDirectory";
    File projectPath = new File(getProjectCreationDirPath(), projectDirName);

    // The bug appears only when the central build folder does not exist.
    final File centralBuildDirPath = new File(projectPath, join("central", "build"));
    File centralBuildParentDirPath = centralBuildDirPath.getParentFile();
    delete(centralBuildParentDirPath);

    guiTest.importProjectAndWaitForProjectSyncToFinish(projectDirName);
    final Module app = guiTest.ideFrame().getModule("app");

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

  @Test
  public void testSyncWithUnresolvedDependencies() throws IOException {
    guiTest.importSimpleApplication();
    final VirtualFile appBuildFile = guiTest.ideFrame().findFileByRelativePath("app/build.gradle", true);

    boolean versionChanged = false;

    final Project project = guiTest.ideFrame().getProject();
    final GradleBuildModel buildModel = execute(new GuiQuery<GradleBuildModel>() {
      @Override
      @Nullable
      protected GradleBuildModel executeInEDT() throws Throwable {
        return GradleBuildModel.parseBuildFile(appBuildFile, project);
      }
    });

    assertNotNull(buildModel);

    for (ArtifactDependencyModel artifact : buildModel.dependencies().artifacts()) {
      if ("com.android.support".equals(artifact.group().value()) && "appcompat-v7".equals(artifact.name().value())) {
        artifact.setVersion("100.0.0");
        versionChanged = true;
        break;
      }
    }

    assertTrue(versionChanged);

    runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    ContentFixture syncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("Failed to resolve: com.android.support:appcompat-v7:"));
  }

  @Test
  public void testImportProjectWithoutWrapper() throws IOException {
    GradleExperimentalSettings settings = GradleExperimentalSettings.getInstance();
    settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;
    settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 5;

    guiTest.copyProjectBeforeOpening("AarDependency");

    IdeFrameFixture.deleteWrapper(guiTest.getProjectPath());

    guiTest.cleanUpProjectForImport(guiTest.getProjectPath());

    VirtualFile toSelect = findFileByIoFile(guiTest.getProjectPath(), true);
    assertNotNull(toSelect);

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
  public void testEditorFindsAppCompatStyle() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("AarDependency");

    String stringsXmlPath = "app/src/main/res/values/strings.xml";
    guiTest.ideFrame().getEditor().open(stringsXmlPath, Tab.EDITOR);

    FileFixture file = guiTest.ideFrame().findExistingFileByRelativePath(stringsXmlPath);
    file.requireCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }

  @Test
  public void testModuleSelectionOnImport() throws IOException {
    GradleExperimentalSettings.getInstance().SELECT_MODULES_ON_PROJECT_IMPORT = true;
    guiTest.importProject("Flavoredlib");

    ConfigureProjectSubsetDialogFixture projectSubsetDialog = ConfigureProjectSubsetDialogFixture.find(guiTest.robot());
    projectSubsetDialog.selectModule("lib", false).clickOk();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    // Verify that "lib" (which was unchecked in the "Select Modules to Include" dialog) is not a module.
    assertThat(guiTest.ideFrame().getModuleNames()).containsOnly("Flavoredlib", "app");

    // subsequent project syncs should respect module selection
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();
    assertThat(guiTest.ideFrame().getModuleNames()).containsOnly("Flavoredlib", "app");
  }

  @Test
  public void testLocalJarsAsModules() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LocalJarsAsModules");
    Module localJarModule = guiTest.ideFrame().getModule("localJarAsModule");

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

  @Test
  public void testLocalAarsAsModules() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LocalAarsAsModules");
    Module localAarModule = guiTest.ideFrame().getModule("library-debug");

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

    Module appModule = guiTest.ideFrame().getModule("app");
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

  @Test
  public void testInterModuleDependencies() throws IOException {
    guiTest.importMultiModule();

    Module appModule = guiTest.ideFrame().getModule("app");
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(appModule);

    // Verify that the module "app" depends on module "library"
    ModuleOrderEntry found = null;
    for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry dependency = (ModuleOrderEntry)orderEntry;
        if (dependency.getModuleName().equals("library")) {
          found = dependency;
          break;
        }
      }
    }

    assertNotNull(found);
    assertThat(found.getModuleName()).isEqualTo("library");
  }

  @Test
  public void testAndroidPluginAndGradleVersionCompatibility() throws IOException {
    guiTest.importMultiModule();

    // Set the plugin version to 1.0.0. This version is incompatible with Gradle 2.4.
    // We expect the IDE to warn the user about this incompatibility.
    guiTest.ideFrame().updateGradleWrapperVersion("2.4").updateAndroidGradlePluginVersion("1.0.0").requestProjectSync()
      .waitForGradleProjectSyncToFinish();

    ContentFixture syncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("Gradle 2.4 requires Android Gradle plugin 1.2.0 (or newer)"));
  }

  // See https://code.google.com/p/android/issues/detail?id=165576
  @Test
  public void testJavaModelSerialization() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish().closeProject();

    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");

    LibraryTable libraryTable = ProjectLibraryTable.getInstance(guiTest.ideFrame().getProject());
    // When serialization of Java model fails, libraries are not set up.
    // Here we confirm that serialization works, because the Java module has the dependency declared in its build.gradle file.
    assertThat(libraryTable.getLibraries()).hasSize(1);
  }

  // See https://code.google.com/p/android/issues/detail?id=167378
  @Test
  public void testInterJavaModuleDependencies() throws IOException {
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

    assertNotNull(moduleDependency);
    assertThat(moduleDependency.getModuleName()).isEqualTo("library2");
  }

  // See https://code.google.com/p/android/issues/detail?id=169778
  @Test
  public void testJavaToAndroidModuleDependencies() throws IOException {
    guiTest.importMultiModule();
    Module library3 = guiTest.ideFrame().getModule("library3");
    assertNull(AndroidFacet.getInstance(library3));

    File library3BuildFile = new File(guiTest.ideFrame().getProjectPath(), join("library3", FN_BUILD_GRADLE));
    assertThat(library3BuildFile).isFile();
    appendToFile(library3BuildFile, "dependencies { compile project(':app') }");

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

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

    ContentFixture syncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message =
      syncMessages.findMessage(WARNING, firstLineStartingWith("Ignoring dependency of module 'app' on module 'library3'."));

    // Verify if the error message's link goes to the build file.
    VirtualFile buildFile = getGradleBuildFile(library3);
    assertNotNull(buildFile);
    message.requireLocation(new File(buildFile.getPath()), 0);
  }

  // See https://code.google.com/p/android/issues/detail?id=73087
  @Test
  public void testUserDefinedLibraryAttachments() throws IOException {
    File javadocJarPath = getFilePathProperty("guava.javadoc.jar.path", "the path of the Javadoc jar file for Guava", false);
    assume().that(javadocJarPath).isNotNull();

    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");
    LibraryPropertiesDialogFixture propertiesDialog = guiTest.ideFrame().showPropertiesForLibrary("guava");
    propertiesDialog.addAttachment(javadocJarPath).clickOk();

    guiTest.waitForBackgroundTasks();

    String javadocJarUrl = pathToUrl(javadocJarPath.getPath());

    // Verify that the library has the Javadoc attachment we just added.
    LibraryFixture library = propertiesDialog.getLibrary();
    library.requireJavadocUrls(javadocJarUrl);

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    // Verify that the library still has the Javadoc attachment after sync.
    library = propertiesDialog.getLibrary();
    library.requireJavadocUrls(javadocJarUrl);
  }

  // See https://code.google.com/p/android/issues/detail?id=169743
  // JVM settings for Gradle should be cleared before any invocation to Gradle.
  @Test
  public void testClearJvmArgsOnSyncAndBuild() throws IOException {
    guiTest.importSimpleApplication();
    Project project = guiTest.ideFrame().getProject();

    GradleProperties gradleProperties = new GradleProperties(project);
    gradleProperties.clear();
    gradleProperties.save();

    VirtualFile gradlePropertiesFile = findFileByIoFile(gradleProperties.getPath(), true);
    assertNotNull(gradlePropertiesFile);
    guiTest.ideFrame().getEditor().open(gradlePropertiesFile, Tab.DEFAULT);

    String jvmArgs = "-Xmx2048m";
    guiTest.ideFrame().setGradleJvmArgs(jvmArgs);

    guiTest.ideFrame().requestProjectSync();

    // Copy JVM args to gradle.properties file.
    guiTest.ideFrame().findMessageDialog(GRADLE_SETTINGS_DIALOG_TITLE).clickYes();

    // Verify JVM args were removed from IDE's Gradle settings.
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    assertNull(GradleSettings.getInstance(project).getGradleVmOptions());

    // Verify JVM args were copied to gradle.properties file
    refreshFiles();

    gradleProperties = new GradleProperties(project);
    assertEquals(jvmArgs, gradleProperties.getJvmArgs());
  }

  // Verifies that the IDE, during sync, asks the user to copy IDE proxy settings to gradle.properties, if applicable.
  // See https://code.google.com/p/android/issues/detail?id=65325
  @Test
  public void testWithIdeProxySettings() throws IOException {
    System.getProperties().setProperty("show.do.not.copy.http.proxy.settings.to.gradle", "true");

    guiTest.importSimpleApplication();
    File gradlePropertiesPath = new File(guiTest.ideFrame().getProjectPath(), "gradle.properties");
    createIfNotExists(gradlePropertiesPath);

    String host = "myproxy.test.com";
    int port = 443;

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;

    guiTest.ideFrame().requestProjectSync();

    // Expect IDE to ask user to copy proxy settings.
    ProxySettingsDialogFixture proxyDialog = ProxySettingsDialogFixture.find(guiTest.robot());
    proxyDialog.setDoNotShowThisDialog(true);
    proxyDialog.clickOk();

    guiTest.ideFrame().waitForGradleProjectSyncToStart().waitForGradleProjectSyncToFinish();

    // Verify gradle.properties has proxy settings.
    assertThat(gradlePropertiesPath).isFile();

    Properties gradleProperties = getProperties(gradlePropertiesPath);
    assertEquals(host, gradleProperties.getProperty("systemProp.http.proxyHost"));
    assertEquals(String.valueOf(port), gradleProperties.getProperty("systemProp.http.proxyPort"));

    // Verifies that the "Do not show this dialog in the future" does not show up. If it does show up the test will timeout and fail.
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();
  }

  @Test
  public void testMismatchingEncodings() throws IOException {
    guiTest.importSimpleApplication();
    final Project project = guiTest.ideFrame().getProject();

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        EncodingProjectManager encodings = EncodingProjectManager.getInstance(project);
        encodings.setDefaultCharsetName("ISO-8859-1");
      }
    });

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    String expectedMessage =
      "The project encoding (ISO-8859-1) has been reset to the encoding specified in the Gradle build files (UTF-8).";
    ContentFixture syncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(INFO, firstLineStartingWith(expectedMessage));

    assertEquals("UTF-8", EncodingProjectManager.getInstance(project).getDefaultCharsetName());
  }

  // Verifies that the IDE switches SDKs if the IDE and project SDKs are not the same.
  @Test
  public void testSdkSwitch() throws IOException {
    File secondSdkPath = getFilePathProperty("second.android.sdk.path", "the path of a secondary Android SDK", true);
    assume().that(secondSdkPath).isNotNull();

    getGuiTestSuiteState().setSkipSdkMerge(true);

    File originalSdkPath = IdeSdks.getAndroidSdkPath();
    assertNotNull(originalSdkPath);

    guiTest.importSimpleApplication();

    // Change the SDK in the project. We expect the IDE to have the same SDK as the project.
    LocalProperties localProperties = new LocalProperties(guiTest.ideFrame().getProject());
    localProperties.setAndroidSdkPath(secondSdkPath);
    localProperties.save();

    guiTest.ideFrame().requestProjectSync();

    MessagesFixture messages = guiTest.ideFrame().findMessageDialog(ANDROID_SDK_MANAGER_DIALOG_TITLE);
    messages.click("Use Project's SDK");

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    assertThat(IdeSdks.getAndroidSdkPath()).isEqualTo(secondSdkPath);

    // Set the project's SDK to be the original one. Now we will choose the IDE's SDK.
    localProperties = new LocalProperties(guiTest.ideFrame().getProject());
    localProperties.setAndroidSdkPath(originalSdkPath);
    localProperties.save();

    guiTest.ideFrame().requestProjectSync();

    messages = guiTest.ideFrame().findMessageDialog(ANDROID_SDK_MANAGER_DIALOG_TITLE);
    messages.click("Use Android Studio's SDK");

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    localProperties = new LocalProperties(guiTest.ideFrame().getProject());
    assertThat(localProperties.getAndroidSdkPath()).isEqualTo(secondSdkPath);
  }

  // Verifies that if syncing using cached model, and if the cached model is missing data, we fall back to a full Gradle sync.
  // See: https://code.google.com/p/android/issues/detail?id=160899
  @Test
  public void testWithCacheMissingModules() throws IOException {
    guiTest.importSimpleApplication();

    // Remove a module from the cache.
    Project project = guiTest.ideFrame().getProject();
    DataNode<ProjectData> cache = getCachedProjectData(project);
    assertNotNull(cache);

    List<DataNode<?>> cachedChildren = field("myChildren").ofType(new TypeRef<List<DataNode<?>>>() {
    }).in(cache).get();
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
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();
  }

  // Verify that the IDE warns users about rendering issue when using plugin 1.2.0 to 1.2.2.
  // See https://code.google.com/p/android/issues/detail?id=170841
  @Test
  public void testModelWithLayoutRenderingIssue() throws IOException {
    guiTest.importMultiModule();
    guiTest.ideFrame().updateGradleWrapperVersion("2.4").updateAndroidGradlePluginVersion("1.2.0").requestProjectSync()
      .waitForGradleProjectSyncToFinish();

    ContentFixture syncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(WARNING, firstLineStartingWith("Using an obsolete version of the Gradle plugin (1.2.0)"));
  }

  // Verifies that after making a change in a build.gradle file, the editor notification saying that sync is needed shows up. This wasn't
  // the case after a project import.
  // See https://code.google.com/p/android/issues/detail?id=171370
  @Test
  public void testEditorNotificationsWhenSyncNeededAfterProjectImport() throws IOException {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/build.gradle").waitUntilErrorAnalysisFinishes().enterText("Hello World");

    guiTest.ideFrame().requireEditorNotification(
      "Gradle files have changed since last project sync. " + "A project sync may be necessary for the IDE to work properly.");
  }

  // Verifies that sync does not fail and user is warned when a project contains an Android module without variants.
  // See https://code.google.com/p/android/issues/detail?id=170722
  @Test
  public void testWithAndroidProjectWithoutVariants() throws IOException {
    guiTest.importSimpleApplication();
    Module appModule = guiTest.ideFrame().getModule("app");
    assertNotNull(AndroidFacet.getInstance(appModule));

    File appBuildFile = new File(guiTest.ideFrame().getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertThat(appBuildFile).isFile();

    // Remove all variants.
    appendToFile(appBuildFile, "android.variantFilter { variant -> variant.ignore = true }");

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    // Verify user was warned.
    ContentFixture syncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    syncMessages.findMessage(ERROR, firstLineStartingWith("The module 'app' is an Android project without build variants"));

    // Verify AndroidFacet was removed.
    appModule = guiTest.ideFrame().getModule("app");
    assertNull(AndroidFacet.getInstance(appModule));
  }

  @Test
  public void testModuleLanguageLevel() throws IOException {
    guiTest.importMultiModule();

    Module library = guiTest.ideFrame().getModule("library");
    Module library2 = guiTest.ideFrame().getModule("library2");
    Module app = guiTest.ideFrame().getModule("app");

    assertEquals(JDK_1_6, getJavaLanguageLevel(library));
    assertEquals(JDK_1_5, getJavaLanguageLevel(library2));
    assertEquals(JDK_1_7, getJavaLanguageLevel(app));
  }

  @Test
  public void testModuleLanguageLevelWithJdk8() throws IOException {
    assume().that(JavaSdk.getInstance().getVersion(IdeSdks.getJdk())).isAtLeast(JavaSdkVersion.JDK_1_8);
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultipleModuleTypes");
    Module javaLib = guiTest.ideFrame().getModule("javaLib");
    assertEquals(JDK_1_8, getJavaLanguageLevel(javaLib));
  }

  @Test
  public void testWithPreReleasePlugin() throws IOException {
    guiTest.importMultiModule();
    guiTest.ideFrame().updateAndroidGradlePluginVersion("1.2.0-beta1").requestProjectSync().waitForGradleProjectSyncToFail();

    ContentFixture syncMessages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message =
      syncMessages.findMessage(ERROR, firstLineStartingWith("Plugin is too old, please update to a more recent version"));
    // Verify that the "quick fix" is added.
    message.findHyperlink("Fix plugin version and sync project");
  }

  @Test
  public void testSyncDuringOfflineMode() throws IOException {
    guiTest.importSimpleApplication();

    File buildFile = new File(guiTest.ideFrame().getProjectPath(), join("app", FN_BUILD_GRADLE));
    assertThat(buildFile).isFile();
    appendToFile(buildFile, "dependencies { compile 'something:not:exists' }");

    GradleSettings gradleSettings = GradleSettings.getInstance(guiTest.ideFrame().getProject());
    gradleSettings.setOfflineWork(true);

    guiTest.ideFrame().requestProjectSync();

    MessageFixture message =
      guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to resolve:"));

    HyperlinkFixture hyperlink = message.findHyperlink("Disable offline mode and Sync");
    hyperlink.click();

    assertFalse(gradleSettings.isOfflineWork());

    message = guiTest
      .ideFrame().getMessagesToolWindow().getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to resolve:"));

    try {
      message.findHyperlink("Disable offline mode and Sync");
      fail("Expecting AssertionFailedError");
    }
    catch (AssertionFailedError expected) {
      // After offline mode is disable, the previous hyperlink will disappear after next sync
    }
  }

  @Nullable
  private static LanguageLevel getJavaLanguageLevel(@NotNull Module module) {
    return LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
  }

  @Test
  public void suggestUpgradingAndroidPlugin() throws IOException {
    final String hyperlinkText = "Fix plugin version and sync project";

    guiTest.importMultiModule();
    guiTest.ideFrame().updateGradleWrapperVersion("2.4").updateAndroidGradlePluginVersion("1.2.0");
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/build.gradle");
    editor.moveTo(editor.findOffset("android {", "\n", true));
    editor.enterText("\nlatestDsl()");

    guiTest.ideFrame().requestProjectSyncAndExpectFailure();

    ContentFixture messages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    String expectedError = "Gradle DSL method not found: 'latestDsl()'";
    MessageFixture message = messages.findMessageContainingText(ERROR, expectedError);
    HyperlinkFixture quickFix = message.findHyperlink(hyperlinkText);
    quickFix.clickAndContinue();

    // Sync still fails, because latestDsl() is made up, but the plugin version should have changed.
    guiTest.ideFrame().waitForGradleProjectSyncToFail();

    // Check the top-level build.gradle got updated.
    GradleVersion newVersion = getAndroidGradleModelVersionFromBuildFile(guiTest.ideFrame().getProject());
    assertNotNull(newVersion);
    assertThat(newVersion.toString()).isEqualTo(GRADLE_PLUGIN_RECOMMENDED_VERSION);

    messages = guiTest.ideFrame().getMessagesToolWindow().getGradleSyncContent();
    expectedError = "Gradle DSL method not found: 'latestDsl()'";
    message = messages.findMessageContainingText(ERROR, expectedError);
    try {
      message.findHyperlink(hyperlinkText);
      fail("There should be no link, now that the plugin is up to date.");
    }
    catch (AssertionFailedError e) {
      assertThat(e.getMessage()).contains("Failed to find URL");
      assertThat(e.getMessage()).contains(hyperlinkText);
    }
  }

  @Test
  public void testSyncWithInvalidJdk() throws IOException {
    guiTest.importSimpleApplication();

    final File tempJdkDirectory = createTempDirectory("GradleSyncTest", "testSyncWithInvalidJdk", true);
    String jdkHome = getSystemPropertyOrEnvironmentVariable(JDK_HOME_FOR_TESTS);
    assert jdkHome != null;
    copyDir(new File(jdkHome), tempJdkDirectory);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            IdeSdks.setJdkPath(tempJdkDirectory);
          }
        });
      }
    });
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFail();

    delete(tempJdkDirectory);
    guiTest.ideFrame().requestProjectSyncAndExpectFailure();
  }

  @Test
  public void testUseLibrary() throws IOException {
    guiTest.importSimpleApplication();
    Project project = guiTest.ideFrame().getProject();

    // Make sure the library was added.
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    final String libraryName = "org.apache.http.legacy-android-23";
    final Library library = libraryTable.getLibraryByName(libraryName);
    assertNotNull(library);

    // Verify that the library has the right j
    VirtualFile[] jarFiles = library.getFiles(CLASSES);
    assertThat(jarFiles).hasSize(1);
    VirtualFile jarFile = jarFiles[0];
    assertEquals("org.apache.http.legacy.jar", jarFile.getName());

    // Verify that the module depends on the library
    final Module appModule = guiTest.ideFrame().getModule("app");
    final AtomicBoolean dependencyFound = new AtomicBoolean();
    new ReadAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(appModule).getModifiableModel();
        for (OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
          if (orderEntry instanceof LibraryOrderEntry) {
            LibraryOrderEntry libraryDependency = (LibraryOrderEntry)orderEntry;
            if (libraryDependency.getLibrary() == library) {
              dependencyFound.set(true);
            }
          }
        }
      }
    }.execute();
    assertTrue("Module app should depend on library '" + library.getName() + "'", dependencyFound.get());
  }

  @Test
  public void testAarSourceAttachments() throws IOException {
    guiTest.importSimpleApplication();
    final Project project = guiTest.ideFrame().getProject();

    final Module appModule = guiTest.ideFrame().getModule("app");

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        runWriteCommandAction(project, new Runnable() {
          @Override
          public void run() {
            GradleBuildModel buildModel = GradleBuildModel.get(appModule);
            assertNotNull(buildModel);

            String newDependency = "com.mapbox.mapboxsdk:mapbox-android-sdk:0.7.4@aar";
            buildModel.dependencies().addArtifact(COMPILE, newDependency);
            buildModel.applyChanges();
          }
        });
      }
    });

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    // Verify that the library has sources.
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    String libraryName = "mapbox-android-sdk-0.7.4";
    Library library = libraryTable.getLibraryByName(libraryName);
    assertNotNull(library);
    VirtualFile[] files = library.getFiles(SOURCES);
    assertThat(files).hasSize(1);
  }

  // https://code.google.com/p/android/issues/detail?id=185313
  @Test
  public void testSdkCreationForAddons() throws IOException {
    guiTest.importSimpleApplication();
    final Project project = guiTest.ideFrame().getProject();

    final Module appModule = guiTest.ideFrame().getModule("app");
    final GradleBuildFile buildFile = GradleBuildFile.get(appModule);
    assertNotNull(buildFile);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        runWriteCommandAction(project, new Runnable() {
          @Override
          public void run() {
            buildFile.setValue(BuildFileKey.COMPILE_SDK_VERSION, "Google Inc.:Google APIs:23");
          }
        });
      }
    });

    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    Sdk sdk = ModuleRootManager.getInstance(appModule).getSdk();
    assertNotNull(sdk);

    AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
    assertNotNull(sdkData);

    SdkAdditionalData data = sdk.getSdkAdditionalData();
    assertThat(data).isInstanceOf(AndroidSdkAdditionalData.class);

    AndroidSdkAdditionalData androidSdkData = (AndroidSdkAdditionalData)data;
    assertNotNull(androidSdkData);
    IAndroidTarget buildTarget = androidSdkData.getBuildTarget(sdkData);
    assertNotNull(buildTarget);

    // By checking that there are no additional libraries in the SDK, we are verifying that an additional SDK was not created for add-ons.
    assertThat(buildTarget.getAdditionalLibraries()).hasSize(0);
  }

  @Test
  public void testGradleModelCache() throws IOException {
    guiTest.importSimpleApplication();
    final File projectPath = guiTest.ideFrame().getProjectPath();
    guiTest.ideFrame().closeProject();

    final AtomicBoolean syncSkipped = new AtomicBoolean(false);

    // Reopen project and verify that sync was skipped (i.e. model loaded from cache)
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
        Project project = projectManager.convertAndLoadProject(projectPath.getPath());
        assertNotNull(project);
        GradleSyncState.subscribe(project, new GradleSyncListener.Adapter() {
          @Override
          public void syncSkipped(@NotNull Project project) {
            syncSkipped.set(true);
          }
        });
        projectManager.openProject(project);
      }
    });

    Wait.minutes(2).expecting("sync to be skipped").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return syncSkipped.get();
      }
    });
  }
}
