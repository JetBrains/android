/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin;

import static com.android.tools.idea.wizard.template.Language.Kotlin;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.ConfigureKotlinDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewKotlinClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureNewAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import java.io.IOException;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;

public class ProjectWithKotlinTestUtil {

  public static final String APP = "app";
  public static final String SRC = "src";
  public static final String MAIN = "main";
  public static final String JAVA = "java";
  public static final String MENU_FILE = "File";
  public static final String MENU_NEW = "New";
  public static final String KOTLIN_FILE_CLASS = "Kotlin Class/File";
  public static final String KOTLIN_EXTENSION = ".kt";
  public static final String CLASS_NAME = "KotlinClass";
  public static final String FILE_NAME = "KotlinFile";
  public static final String INTERFACE_NAME = "KotlinInterface";
  public static final String ENUM_NAME = "KotlinEnum";
  public static final String OBJECT_NAME = "KotlinObject";
  // Types for Kotlin File/Class
  public static final String TYPE_CLASS = "Class";
  public static final String TYPE_FILE = "File";
  public static final String TYPE_INTERFACE = "Interface";
  public static final String TYPE_ENUMCLASS = "Enum class";
  public static final String TYPE_OBJECT = "Object";
  public static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);

  protected static void createKotlinFileAndClassAndVerify(@NotNull String projectDirName,
                                                 @NotNull String packageName,
                                                 boolean withKotlinSupport,
                                                 GuiTestRule guiTest,
                                                 EmulatorTestRule emulator) throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish(projectDirName);

    ProjectViewFixture.PaneFixture projectPane = ideFrameFixture.getProjectView().selectProjectPane();

    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, CLASS_NAME, TYPE_CLASS);
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, FILE_NAME, TYPE_FILE);
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, INTERFACE_NAME, TYPE_INTERFACE);
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, ENUM_NAME, TYPE_ENUMCLASS);
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, OBJECT_NAME, TYPE_OBJECT);

    if (!withKotlinSupport) {
      EditorNotificationPanelFixture editorNotificationPanelFixture =
        ideFrameFixture.getEditor().awaitNotification("Kotlin not configured");
      editorNotificationPanelFixture.performActionWithoutWaitingForDisappearance("Configure");

      ideFrameFixture.actAndWaitForGradleProjectSyncToFinish(Wait.seconds(60), it -> {
        // As default, "All modules containing Kotlin files" option is selected for now.
        ConfigureKotlinDialogFixture.find(ideFrameFixture.robot())
          .clickOkAndWaitDialogDisappear();
        it.requestProjectSync();
      });
    }

    ideFrameFixture.invokeMenuPath("Build", "Rebuild Project");

    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());

    ideFrameFixture.runApp(APP, emulator.getDefaultAvdName());

    // Check app successfully builds and deploys on emulator.
    ideFrameFixture.getRunToolWindow().findContent(APP)
                   .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 60);
  }

  protected static void newKotlinFileAndClass(@NotNull ProjectViewFixture.PaneFixture projectPane,
                                     @NotNull IdeFrameFixture ideFrameFixture,
                                     @NotNull String projectName,
                                     @NotNull String packageName,
                                     @NotNull String name,
                                     @NotNull String type) {
    Wait.seconds(30).expecting("Path should be found.").until(() -> {
      try {
        projectPane.clickPath(projectName, APP, SRC, MAIN, JAVA, packageName)
                   .invokeMenuPath(MENU_FILE, MENU_NEW, KOTLIN_FILE_CLASS);
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });

    NewKotlinClassDialogFixture.find(ideFrameFixture)
                               .enterName(name)
                               .selectType(type)
                               .clickOk();

    String fileName = name + KOTLIN_EXTENSION;

    // The flakiness here is 1/1000. Increase the timeout from 5s to 10s to stabilize it.
    Wait.seconds(10).expecting(fileName + " file should be opened")
        .until(() -> fileName.equals(ideFrameFixture.getEditor().getCurrentFileName()));
  }

  protected static void createKotlinProj(boolean hasCppSupport, GuiTestRule guiTest) throws IOException {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
                                                      .createNewProject();

    ConfigureNewAndroidProjectStepFixture<NewProjectWizardFixture> configAndroid = newProjectWizard
      .getChooseAndroidProjectStep()
      .chooseActivity(hasCppSupport ? "Native C++" : "Empty Views Activity")
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .enterPackageName("android.com");

    waitForPackageNameToShow("android.com", configAndroid);

    configAndroid.setSourceLanguage(Kotlin);

    if (hasCppSupport) {
      newProjectWizard.clickNext();
    }

    newProjectWizard.clickFinishAndWaitForSyncToFinish(Wait.seconds(300));
  }

  /**
   *
   * @throws WaitTimedOutError if the package name field does not contain {@code expectedName}
   */
  private static <S, W extends AbstractWizardFixture> void waitForPackageNameToShow(
    @NotNull String expectedName,
    @NotNull AbstractWizardStepFixture<S, W> configAndroidFixture
  ) {

    Robot robot = configAndroidFixture.robot();
    JComponent comp = robot.finder().findByLabel(configAndroidFixture.target(), "Package name", JComponent.class, true);
    JTextComponent textField = robot.finder().find(comp, JTextComponentMatcher.any());

    Wait.seconds(10)
      .expecting("Package name field to show " + expectedName)
      .until(
        () -> GuiQuery.getNonNull(
          () -> expectedName.equals(textField.getText())));
  }
}
