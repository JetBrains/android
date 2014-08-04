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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.tests.gui.framework.fixture.FileChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.SystemProperties;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.templates.AndroidGradleTestCase.createGradleWrapper;
import static com.android.tools.idea.templates.AndroidGradleTestCase.updateGradleVersions;
import static com.android.tools.idea.tests.gui.framework.GuiTestRunner.canRunGuiTests;
import static com.android.tools.idea.tests.gui.framework.GuiTests.GUI_TESTS_RUNNING_IN_SUITE_PROPERTY;
import static com.android.tools.idea.tests.gui.framework.GuiTests.getTestProjectsRootDirPath;
import static com.intellij.openapi.util.io.FileUtil.*;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;

@RunWith(GuiTestRunner.class)
public abstract class GuiTestCase {
  @NonNls private static final String FN_DOT_IDEA = ".idea";

  protected Robot myRobot;

  @Before
  public void setUp() throws Exception {
    if (!canRunGuiTests()) {
      // We currently do not support running UI tests in headless environments.
      return;
    }

    Application application = ApplicationManager.getApplication();
    assertNotNull(application); // verify that we are using the IDE's ClassLoader.

    setUpRobot();
  }

  @After
  public void tearDown() {
    if (myRobot != null) {
      myRobot.cleanUpWithoutDisposingWindows();
    }
  }

  @AfterClass
  public static void tearDownPerClass() {
    boolean inSuite = SystemProperties.getBooleanProperty(GUI_TESTS_RUNNING_IN_SUITE_PROPERTY, false);
    if (!inSuite) {
      IdeTestApplication.disposeInstance();
    }
  }

  @NotNull
  protected WelcomeFrameFixture findWelcomeFrame() {
    return WelcomeFrameFixture.find(myRobot);
  }

  @NotNull
  protected NewProjectWizardFixture findNewProjectWizard() {
    return NewProjectWizardFixture.find(myRobot);
  }

  @NotNull
  protected IdeFrameFixture findIdeFrame(@NotNull String projectName, @NotNull File projectPath) {
    return IdeFrameFixture.find(myRobot, projectPath, projectName);
  }

  @NotNull
  protected IdeFrameFixture findIdeFrame(@NotNull File projectPath) {
    return IdeFrameFixture.find(myRobot, projectPath, null);
  }

  @SuppressWarnings("UnusedDeclaration")
  // Called by GuiTestRunner via reflection.
  protected void closeAllProjects() {
    setUpRobot();

    for (Frame frame : Frame.getFrames()) {
      if (frame instanceof IdeFrameImpl) {
        IdeFrameFixture ideFrame = new IdeFrameFixture(myRobot, (IdeFrameImpl)frame);
        ideFrame.close();
      }
    }
  }

  private void setUpRobot() {
    if (myRobot == null) {
      myRobot = BasicRobot.robotWithCurrentAwtHierarchy();
      myRobot.settings().delayBetweenEvents(30);
    }
  }

  @NotNull
  protected IdeFrameFixture openProject(@NotNull String projectDirName) throws IOException {
    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.openProjectButton().click();

    final File projectPath = new File(getTestProjectsRootDirPath(), projectDirName);
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        setUpProject(projectPath);
      }
    });

    FileChooserDialogFixture openProjectDialog = FileChooserDialogFixture.findOpenProjectDialog(myRobot);
    openProjectDialog.select(projectPath).clickOK();

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectToBeOpened();

    return projectFrame;
  }

  private static void setUpProject(@NotNull File projectPath) throws IOException {
    createGradleWrapper(projectPath);
    updateGradleVersions(projectPath);

    File androidHomePath = DefaultSdks.getDefaultAndroidHome();
    assertNotNull(androidHomePath);

    LocalProperties localProperties = new LocalProperties(projectPath);
    localProperties.setAndroidSdkPath(androidHomePath);
    localProperties.save();

    File toDotIdea = new File(projectPath, FN_DOT_IDEA);
    if (toDotIdea.isDirectory()) {
      File fromDotIdea = new File(getTestProjectsRootDirPath(), join("commonFiles", FN_DOT_IDEA));
      assertThat(fromDotIdea).isDirectory();

      for (File from : notNullize(fromDotIdea.listFiles())) {
        if (from.isDirectory()) {
          File destination = new File(toDotIdea, from.getName());
          if (!destination.isDirectory()) {
            copyDirContent(from, destination);
          }
          continue;
        }
        File to = new File(toDotIdea, from.getName());
        if (!to.isFile()) {
          copy(from, to);
        }
      }
    }
  }
}
