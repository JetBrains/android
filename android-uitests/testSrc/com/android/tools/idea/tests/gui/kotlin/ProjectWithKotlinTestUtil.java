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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.CMakeOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.sdk.IdeSdks;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.ProjectManager;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
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

  public static final String CLASS_NAME = "KotlinClass";
  public static final String KOTLIN_EXTENSION = ".kt";
  public static final String FILE_NAME = "KotlinFile";
  public static final String APP = "app";
  public static final String SRC = "src";
  public static final String MAIN = "main";
  public static final String JAVA = "java";
  public static final String MENU_FILE = "File";
  public static final String MENU_NEW = "New";
  public static final String KOTLIN_FILE_CLASS = "Kotlin File/Class";
  public static final String INTERFACE_NAME = "KotlinInterface";
  public static final String ENUM_NAME = "KotlinEnum";
  public static final String OBJECT_NAME = "KotlinObject";
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

    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, CLASS_NAME, "Class");
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, FILE_NAME, "File");
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, INTERFACE_NAME, "Interface");
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, ENUM_NAME, "Enum class");
    newKotlinFileAndClass(projectPane, ideFrameFixture, projectDirName, packageName, OBJECT_NAME, "Object");

    if (!withKotlinSupport) {
      EditorNotificationPanelFixture editorNotificationPanelFixture =
        ideFrameFixture.getEditor().awaitNotification("Kotlin not configured");
      editorNotificationPanelFixture.performActionWithoutWaitingForDisappearance("Configure");

      // As default, "All modules containing Kotlin files" option is selected for now.
      ConfigureKotlinDialogFixture.find(ideFrameFixture)
                                  .clickOk();
      ideFrameFixture.requestProjectSync();
    }

    ideFrameFixture.invokeMenuPath("Build", "Rebuild Project").waitForGradleProjectSyncToFinish(Wait.seconds(60));

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
      .chooseActivity(hasCppSupport ? "Native C++" : "Empty Activity")
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .enterPackageName("android.com");

    waitForPackageNameToShow("android.com", configAndroid);

    configAndroid.setSourceLanguage("Kotlin");

    if (hasCppSupport) {
      newProjectWizard.clickNext();
    }

    try {
      newProjectWizard.clickFinish(Wait.seconds(30), Wait.seconds(120));
    } catch (WaitTimedOutError setupTimeout) {
      // We do not care about timeouts if the IDE is indexing and syncing the project,
      // so we don't actually want to throw an error in case  we get a timeout from
      // indexing or syncing

      // Unfortunately, there are 3 different waits used in the clickFinish() method,
      // so we have to repeat the checks to throw a more detailed error message

      // Check if the dialog is still open
      if (GuiQuery.getNonNull(() -> newProjectWizard.target().isShowing())) {
        // dialog still showing
        throw setupTimeout;
      }

      // Check if the project is opened
      if (ProjectManager.getInstance().getOpenProjects().length != 1) {
        throw setupTimeout;
      }

      // The only other possibility here is that the project is still indexing
      // or syncing. Ignore the timeout in this case!
    }

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.waitForGradleProjectSyncToFinish(Wait.seconds(240));

    // TODO remove the following hack: b/110174414
    File androidSdk = IdeSdks.getInstance().getAndroidSdkPath();
    File ninja = new File(androidSdk, "cmake/3.10.4819442/bin/ninja");

    AtomicReference<IOException> buildGradleFailure = new AtomicReference<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      WriteCommandAction.runWriteCommandAction(ideFrame.getProject(), () -> {
        ProjectBuildModel pbm = ProjectBuildModel.get(ideFrame.getProject());
        GradleBuildModel buildModel = pbm.getModuleBuildModel(ideFrame.getModule("app"));
        CMakeOptionsModel cmakeModel = buildModel
          .android()
          .defaultConfig()
          .externalNativeBuild()
          .cmake();

        ResolvedPropertyModel cmakeArgsModel = cmakeModel.arguments();
        try {
          cmakeArgsModel.setValue("-DCMAKE_MAKE_PROGRAM=" + ninja.getCanonicalPath());
          buildModel.applyChanges();
        }
        catch (IOException failureToWrite) {
          buildGradleFailure.set(failureToWrite);
        }
      });
    });
    IOException errorsWhileModifyingBuild = buildGradleFailure.get();
    if(errorsWhileModifyingBuild != null) {
      throw errorsWhileModifyingBuild;
    }
    // TODO end hack for b/110174414

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
