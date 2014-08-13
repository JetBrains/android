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
package com.android.tools.idea.tests.gui;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.DialogFixture;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleWrapperPropertiesFilePath;
import static com.android.tools.idea.gradle.util.GradleUtil.updateGradleDistributionUrl;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.fest.util.Strings.quote;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;

/**
 * Tests upgrade of Android Gradle plug-in and Gradle itself.
 */
public class PluginAndGradleUpgradeTest extends GuiTestCase {
  private static final String GRADLE_HOME_PROPERTY = "gradle.home.path";

  @Test @IdeGuiTest
  public void testUpdateGradleVersion() throws IOException {
    // For now we need a custom repository, since 0.13.0 is not released yet.
    String customRepositoryUrl = System.getenv("MAVEN_URL");
    if (isEmpty(customRepositoryUrl)) {
      fail("Please specify, in the environment variable 'MAVEN_URL', the path of the custom Maven repo to use");
    }

    String projectDirName = "PluginAndGradleUpgrade";
    File projectPath = setUpProject(projectDirName, false, false);

    // Ensure we have a pre-2.0 Gradle in the wrapper.
    File wrapperPropertiesFile = getGradleWrapperPropertiesFilePath(projectPath);
    updateGradleDistributionUrl("1.12", wrapperPropertiesFile);

    // Import the project
    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.importProjectButton().click();
    FileChooserDialogFixture importProjectDialog = FileChooserDialogFixture.findImportProjectDialog(myRobot);
    importProjectDialog.select(projectPath).clickOK();

    // Expect a dialog explaining that the version of Gradle in the project's wrapper needs to be updated to version 2.0, and click the
    // "OK" button.
    DialogFixture gradleVersionUpdateDialog = findDialog(withTitle("Gradle Sync")).using(myRobot);
    gradleVersionUpdateDialog.button(withText("OK")).click();

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectToBeOpened();

    // Now we are going to force the project to use a local Gradle distribution.
    // Ensure that the project is using the wrapper.
    //String gradleHome = System.getProperty(GRADLE_LOCAL_HOME_PATH_PROPERTY);
    //if (isEmpty(gradleHome)) {
    //  fail("Please specify the path of a local Gradle distribution (v1.12) using the system property " +
    //       quote(GRADLE_LOCAL_HOME_PATH_PROPERTY));
    //}
    //
    //GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(projectFrame.getProject());
    //assertNotNull(gradleSettings);
    //gradleSettings.setDistributionType(LOCAL);
    //gradleSettings.setGradleHome(gradleHome);
  }
}
