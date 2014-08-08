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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.SystemProperties;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Condition;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.templates.AndroidGradleTestCase.createGradleWrapper;
import static com.android.tools.idea.templates.AndroidGradleTestCase.updateGradleVersions;
import static com.android.tools.idea.tests.gui.framework.GuiTestRunner.canRunGuiTests;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.intellij.ide.impl.ProjectUtil.closeAndDispose;
import static com.intellij.openapi.util.io.FileUtil.*;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertTrue;

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

    setUpDefaultProjectCreationLocationPath();

    myRobot = BasicRobot.robotWithCurrentAwtHierarchy();
    myRobot.settings().delayBetweenEvents(30);
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
    pause(new Condition("Close all projects") {
      @Override
      public boolean test() {
        final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        GuiActionRunner.execute(new GuiTask() {
          @Override
          protected void executeInEDT() throws Throwable {
            for (Project project : openProjects) {
              assertTrue("Failed to close project " + quote(project.getName()), closeAndDispose(project));
            }
          }
        });
        return ProjectManager.getInstance().getOpenProjects().length == 0;
      }
    }, SHORT_TIMEOUT);

    boolean welcomeFrameShown = GuiActionRunner.execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
          WelcomeFrame.showNow();

          WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
          windowManager.disposeRootFrame();
          return true;
        }
        return false;
      }
    });

    if (welcomeFrameShown) {
      pause(new Condition("'Welcome' frame to show up") {
        @Override
        public boolean test() {
          for (Frame frame : Frame.getFrames()) {
            if (frame instanceof WelcomeFrame && frame.isShowing()) {
              return true;
            }
          }
          return false;
        }
      }, SHORT_TIMEOUT);
    }
  }

  @NotNull
  protected IdeFrameFixture importProject(@NotNull String projectDirName) throws IOException {
    final File projectPath = setUpProject(projectDirName, false);

    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.importProjectButton().click();

    FileChooserDialogFixture importProjectDialog = FileChooserDialogFixture.findImportProjectDialog(myRobot);
    return openProjectAndWaitUntilOpened(projectPath, importProjectDialog);
  }

  @NotNull
  protected IdeFrameFixture openProject(@NotNull String projectDirName) throws IOException {
    final File projectPath = setUpProject(projectDirName, true);

    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.openProjectButton().click();

    FileChooserDialogFixture openProjectDialog = FileChooserDialogFixture.findOpenProjectDialog(myRobot);
    return openProjectAndWaitUntilOpened(projectPath, openProjectDialog);
  }

  @NotNull
  private static File setUpProject(@NotNull String projectDirName, boolean forOpen) throws IOException {
    final File projectPath = new File(getTestProjectsRootDirPath(), projectDirName);
    createGradleWrapper(projectPath);
    updateGradleVersions(projectPath);

    File androidHomePath = DefaultSdks.getDefaultAndroidHome();
    assertNotNull(androidHomePath);

    LocalProperties localProperties = new LocalProperties(projectPath);
    localProperties.setAndroidSdkPath(androidHomePath);
    localProperties.save();

    if (forOpen) {
      File toDotIdea = new File(projectPath, FN_DOT_IDEA);
      ensureExists(toDotIdea);

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
    else {
      removeIdeaFiles(projectPath);
    }

    return projectPath;
  }

  private static void removeIdeaFiles(@NotNull File projectPath) {
    File dotIdeaFolderPath = new File(projectPath, FN_DOT_IDEA);
    if (dotIdeaFolderPath.isDirectory()) {
      File modulesXmlFilePath = new File(dotIdeaFolderPath, "modules.xml");
      if (modulesXmlFilePath.isFile()) {
        SAXBuilder saxBuilder = new SAXBuilder();
        try {
          Document document = saxBuilder.build(modulesXmlFilePath);
          XPath xpath = XPath.newInstance("//*[@fileurl]");
          //noinspection unchecked
          List<Element> modules = xpath.selectNodes(document);
          int urlPrefixSize = "file://$PROJECT_DIR$/".length();
          for (Element module : modules) {
            String fileUrl = module.getAttributeValue("fileurl");
            if (!StringUtil.isEmpty(fileUrl)) {
              String relativePath = toSystemDependentName(fileUrl.substring(urlPrefixSize));
              File imlFilePath = new File(projectPath, relativePath);
              if (imlFilePath.isFile()) {
                delete(imlFilePath);
              }
            }
          }
        }
        catch (Throwable ignored) {
          // if something goes wrong, just ignore. Most likely it won't affect project import in any way.
        }
      }
      delete(dotIdeaFolderPath);
    }
  }

  @NotNull
  private IdeFrameFixture openProjectAndWaitUntilOpened(@NotNull File projectPath, @NotNull FileChooserDialogFixture fileChooserDialog) {
    fileChooserDialog.select(projectPath).clickOK();

    IdeFrameFixture projectFrame = findIdeFrame(projectPath);
    projectFrame.waitForGradleProjectToBeOpened();

    return projectFrame;
  }
}
