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
package com.android.tools.idea.tests.gui.npw;

import static com.android.tools.idea.tests.gui.npw.NewCppProjectTestUtil.assertAndroidPanePath;
import static com.android.tools.idea.tests.gui.npw.NewCppProjectTestUtil.createCppProject;
import static com.android.tools.idea.tests.gui.npw.NewCppProjectTestUtil.getExternalNativeBuildRegExp;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.AddCppToModuleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.CppStandardType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddRemoveCppDependencyTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(8, TimeUnit.MINUTES);

  /**
   * To verify project deploys successfully after adding and removing dependency
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 45d4c00b-a6a8-4e48-b9af-d55094ae17a3
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project, checking the box for "Include C++ Support"
   *   2. Remove the externalNativeBuild section of the project level build.gradle.kts
   *   3. Sync gradle; verify that the project's app/cpp files are gone but app/java remains
   *   4. Go to File -> Add C++ Project with Gradle
   *   5. Leave the build system dropdown on cmake and select ${projectDir}/app/CMakeLists.txt for project path (Verify 1, 2)
   *   6. Build project
   *
   *   Verification:
   *   1) Verify that the externalNativeBuild section of build.gradle.kts reappears with cmake.path CMakeLists.txt
   *   2) Verify that app/cpp reappears and contains native-lib.cpp
   *   3) Project is built successfully
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void addRemoveCppDependency() {
    createCppProject(CppStandardType.DEFAULT, guiTest);

    IdeFrameFixture ideFixture = guiTest.ideFrame();

    assertAndroidPanePath(true, guiTest, "app", "cpp", "native-lib.cpp");

    // Remove externalNativeBuild block programmatically.
    // This test does not care about removing the block with UI actions only
    ApplicationManager.getApplication().invokeAndWait(() -> {
      WriteCommandAction.runWriteCommandAction(ideFixture.getProject(), () -> {
        ProjectBuildModel pbm = ProjectBuildModel.get(ideFixture.getProject());
        GradleBuildModel buildModel = pbm.getModuleBuildModel(ideFixture.getModule("app"));
        buildModel
          .android()
          .externalNativeBuild()
          .removeNdkBuild()
          .removeCMake();

        buildModel.applyChanges();
      });
    });
    // end programmatic removal of externalNativeBuild block

    // Open the build.gradle.kts file so screenshot mechanism can take a snapshot of the
    // project in case of failure
    ideFixture
      .getEditor()
      .open("app/build.gradle.kts")
      .getIdeFrame()
      .requestProjectSyncAndWaitForSyncToFinish();

    // verify that the project's app/cpp files are gone but app/java remains
    assertAndroidPanePath(false, guiTest, "app", "cpp", "native-lib.cpp");
    assertAndroidPanePath(true, guiTest, "app", "java");

    ideFixture
      .actAndWaitForGradleProjectSyncToFinish(
        it ->
        {
          AddCppToModuleDialogFixture fixture = it.openFromMenu(AddCppToModuleDialogFixture.Companion::find, "File", "Add C++ to Module")
            .selectLinkCppProject();
          fixture.getEnabledTextField().enterText(guiTest.getProjectPath("app/src/main/cpp/CMakeLists.txt").getAbsolutePath());
          fixture.getOkButton().click();
        }
      )
      .getEditor()
      .open("app/build.gradle.kts")
      .select(getExternalNativeBuildRegExp()); // externalNativeBuild section of build.gradle.kts reappears with cmake.path CMakeLists.txt

    assertAndroidPanePath(true, guiTest, "app", "cpp", "native-lib.cpp"); // app/cpp reappears and contains native-lib.cpp

    BuildStatus result = ideFixture.invokeProjectMake();
    assertThat(result.isBuildSuccessful()).isTrue();
  }
}
