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

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseGradleHomeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleWrapperPropertiesFilePath;
import static com.android.tools.idea.gradle.util.GradleUtil.updateGradleDistributionUrl;
import static com.android.tools.idea.tests.gui.framework.GuiTests.GRADLE_1_12_HOME_PROPERTY;
import static com.android.tools.idea.tests.gui.framework.GuiTests.GRADLE_2_1_HOME_PROPERTY;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static junit.framework.Assert.fail;
import static org.fest.util.Strings.quote;

/**
 * Tests upgrade of Android Gradle plug-in and Gradle itself.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PluginAndGradleUpgradeTest extends GuiTestCase {
  private static final String MAVEN_URL = "MAVEN_URL";

  private static final String PROJECT_DIR_NAME = "PluginAndGradleUpgrade";

  @Test @IdeGuiTest(closeProjectBeforeExecution = true)
  public void test1UpdateGradleVersionInWrapper() throws IOException {
    if (skipTest()) {
      return;
    }

    File projectPath = setUpProject(PROJECT_DIR_NAME, false, false);

    // Ensure we have a pre-2.1 Gradle in the wrapper.
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(projectPath);
    updateGradleDistributionUrl("1.12", wrapperPropertiesFile);

    // Import the project
    findWelcomeFrame().clickImportProjectButton();
    FileChooserDialogFixture importProjectDialog = FileChooserDialogFixture.findImportProjectDialog(myRobot);
    importProjectDialog.select(projectPath).clickOk();

    // Expect message suggesting to update Gradle wrapper.
    GradleSyncMessageDialogFixture.find(myRobot).clickOk();

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectSyncToFinish();
  }

  @Test @IdeGuiTest(closeProjectBeforeExecution = false)
  public void test2UpdateGradleVersionWithLocalDistribution() {
    if (skipTest()) {
      return;
    }

    File projectPath = getTestProjectDirPath(PROJECT_DIR_NAME);

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.useLocalGradleDistribution(getUnsupportedGradleHome())
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "Cancel" to use local distribution.
    GradleSyncMessageDialogFixture.find(myRobot).clickCancel();

    String gradleHome = System.getProperty(GRADLE_2_1_HOME_PROPERTY);
    if (isEmpty(gradleHome)) {
      fail("Please specify the path of a local, Gradle 2.1 distribution using the system property " + quote(GRADLE_2_1_HOME_PROPERTY));
    }

    ChooseGradleHomeDialogFixture chooseGradleHomeDialog = ChooseGradleHomeDialogFixture.find(myRobot);
    chooseGradleHomeDialog.chooseGradleHome(new File(gradleHome))
                          .clickOk()
                          .requireNotShowing();

    projectFrame.waitForGradleProjectSyncToFinish();
  }

  @Test @IdeGuiTest(closeProjectBeforeExecution = false)
  public void test3ShowUserFriendlyErrorWhenUsingUnsupportedVersionOfGradle() {
    if (skipTest()) {
      return;
    }

    File projectPath = getTestProjectDirPath(PROJECT_DIR_NAME);
    IdeFrameFixture projectFrame = findIdeFrame(projectPath);

    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution(getUnsupportedGradleHome())
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "Cancel" to use local distribution.
    GradleSyncMessageDialogFixture.find(myRobot).clickCancel();

    ChooseGradleHomeDialogFixture chooseGradleHomeDialog = ChooseGradleHomeDialogFixture.find(myRobot);
    chooseGradleHomeDialog.clickCancel();

    projectFrame.waitForGradleProjectSyncToFail();

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessagesToolWindowFixture.MessageFixture msg =
      messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Gradle 2.1 is required."));
    msg.findHyperlink("Migrate to Gradle wrapper and sync project").click();

    projectFrame.waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  @Test @IdeGuiTest(closeProjectBeforeExecution = false)
  public void test4CreateWrapperWhenLocalDistributionPathIsNotSet() {
    if (skipTest()) {
      return;
    }

    File projectPath = getTestProjectDirPath(PROJECT_DIR_NAME);
    IdeFrameFixture projectFrame = findIdeFrame(projectPath);

    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution("")
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    GradleSyncMessageDialogFixture.find(myRobot).clickOk();

    projectFrame.waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  @Test @IdeGuiTest(closeProjectBeforeExecution = false)
  public void test5CreateWrapperWhenLocalDistributionPathDoesNotExist() {
    if (skipTest()) {
      return;
    }

    File projectPath = getTestProjectDirPath(PROJECT_DIR_NAME);
    IdeFrameFixture projectFrame = findIdeFrame(projectPath);

    File nonExistingDirPath = new File(SystemProperties.getUserHome(), UUID.randomUUID().toString());
    projectFrame.deleteGradleWrapper()
                .useLocalGradleDistribution(nonExistingDirPath.getPath())
                .requestProjectSync();

    // Expect message suggesting to use Gradle wrapper. Click "OK" to use wrapper.
    GradleSyncMessageDialogFixture.find(myRobot).clickOk();

    projectFrame.waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  private boolean skipTest() {
    boolean skip = false;
    String customRepositoryUrl = System.getenv(MAVEN_URL);
    if (isEmpty(customRepositoryUrl)) {
      String msg = String.format("Test '%1$s' skipped. It requires the system property '%2$s'.", getTestName(), MAVEN_URL);
      System.out.println(msg);
      skip = true;
    }
    return skip;
  }

  @NotNull
  private static String getUnsupportedGradleHome() {
    String unsupportedGradleHome = System.getProperty(GRADLE_1_12_HOME_PROPERTY);
    if (isEmpty(unsupportedGradleHome)) {
      fail("Please specify the path of a local, Gradle 1.12 distribution using the system property " + quote(GRADLE_1_12_HOME_PROPERTY));
    }
    return unsupportedGradleHome;
  }
}
