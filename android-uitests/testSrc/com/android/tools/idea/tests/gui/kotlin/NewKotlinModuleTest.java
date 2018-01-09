/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewModuleWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static com.google.common.truth.Truth.assertThat;

@RunWith (GuiTestRunner.class)
public class NewKotlinModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String APP_NAME = "app";
  private static final String NEW_KOTLIN_MDULE_NAME = "KotlinModule";

  @Test
  public void addNewKotlinModuleToNonKotlinProject() throws Exception {
    createNewBasicProject(false);
    addNewKotlinModule();
  }

  @Test
  public void addNewKotlinModuleToKotlinProject() throws Exception {
    createNewBasicProject(true);
    addNewKotlinModule();
  }

  private void createNewBasicProject(boolean hasKotlinSupport) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    newProjectWizard.getConfigureAndroidProjectStep()
      .enterPackageName("android.com")
      .enterApplicationName(APP_NAME)
      .setCppSupport(false)
      .setKotlinSupport(hasKotlinSupport); // Default "App name", "company domain" and "package name"

    newProjectWizard.clickNext()
      .clickNext() // Skip "Select minimum SDK Api" step
      .clickNext() // Skip "Add Activity" step
      .clickFinish();

    guiTest.ideFrame().waitForGradleImportProjectSync();

    if (hasKotlinSupport) {
      assertModuleSupportsKotlin(APP_NAME);
    }
    else {
      assertModuleDoesNotSupportKotlin(APP_NAME);
    }
  }

  private void addNewKotlinModule() {
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    NewModuleWizardFixture newModuleWizardFixture = ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...");

    newModuleWizardFixture
      .chooseModuleType("Phone & Tablet Module")
      .clickNext() // Selected App
      .getConfigureAndroidModuleStep()
      .enterModuleName(NEW_KOTLIN_MDULE_NAME);

    newModuleWizardFixture
      .clickNext() // Default options
      .clickNext() // Default Activity
      .getConfigureActivityStep()
      .setSourceLanguage("Kotlin");

    newModuleWizardFixture
      .clickFinish();

    ideFrame
      .waitForGradleProjectSyncToFinish();

    assertModuleSupportsKotlin(NEW_KOTLIN_MDULE_NAME);
  }

  private void assertModuleSupportsKotlin(String moduleName) {
    String gradleAppFileContent = guiTest.ideFrame().getEditor()
      .open(moduleName.toLowerCase(Locale.US) + "/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleAppFileContent).contains("apply plugin: 'kotlin-android");
  }

  private void assertModuleDoesNotSupportKotlin(String moduleName) {
    String gradleAppFileContent = guiTest.ideFrame().getEditor()
      .open(moduleName.toLowerCase(Locale.US) + "/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleAppFileContent).doesNotContain("kotlin");
  }
}
