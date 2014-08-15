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
package com.android.tools.idea.tests.gui.wizard;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ChooseOptionsForNewFileStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.idea.wizard.FormFactorUtils.FormFactor.MOBILE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;

public class SampleTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testCreateNewMobileProject() {
    final String projectName = "Test Application";

    WelcomeFrameFixture welcomeFrame = findWelcomeFrame();
    welcomeFrame.clickNewProjectButton();

    NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

    ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.configureAndroidProjectStep();
    configureAndroidProjectStep.enterApplicationName(projectName)
                               .enterCompanyDomain("com.android")
                               .enterPackageName("com.android.test.app");
    File projectPath = configureAndroidProjectStep.getLocationInFileSystem();
    newProjectWizard.clickNext();

    String minSdkApi = "19";
    newProjectWizard.configureFormFactorStep().selectMinimumSdkApi(MOBILE, minSdkApi);
    newProjectWizard.clickNext();

    // Skip "Add Activity" step
    newProjectWizard.clickNext();

    ChooseOptionsForNewFileStepFixture chooseOptionsForNewFileStep = newProjectWizard.chooseOptionsForNewFileStep();
    chooseOptionsForNewFileStep.enterActivityName("MainActivity");
    String layoutName = chooseOptionsForNewFileStep.getLayoutName();
    newProjectWizard.clickFinish();

    IdeFrameFixture projectFrame = findIdeFrame(projectName, projectPath);
    projectFrame.waitForGradleProjectSyncToFinish();

    FileFixture layoutFile = projectFrame.findExistingFileByRelativePath("app/src/main/res/layout/" + layoutName + DOT_XML);
    layoutFile.requireOpenAndSelected();

    // Verify state of project
    projectFrame.requireModuleCount(2);
    IdeaAndroidProject appAndroidProject = projectFrame.getAndroidProjectForModule("app");
    assertThat(appAndroidProject.getVariantNames()).as("variants").containsOnly("debug", "release");
    assertThat(appAndroidProject.getSelectedVariant().getName()).as("selected variant").isEqualTo("debug");

    AndroidProject model = appAndroidProject.getDelegate();
    ApiVersion minSdkVersion = model.getDefaultConfig().getProductFlavor().getMinSdkVersion();
    assertNotNull("minSdkVersion", minSdkVersion);
    assertThat(minSdkVersion.getApiString()).as("minSdkVersion API").isEqualTo(minSdkApi);
  }

  @Test @IdeGuiTest
  public void testEditor() throws IOException {
    IdeFrameFixture projectFrame = openProject("SimpleApplication");
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/values/strings.xml", EditorFixture.Tab.EDITOR);

    assertEquals("strings.xml", editor.getCurrentFileName());
    assertEquals(0, editor.getCurrentLineNumber());

    editor.moveTo(editor.findOffset(null, "app_name", true));

    assertEquals("<string name=\"^app_name\">Simple Application</string>", editor.getCurrentLineContents(true, true, 0));
    int offset = editor.findOffset(null, "Simple Application", true);
    editor.moveTo(offset);
    assertEquals("<string name=\"app_name\">^Simple Application</string>", editor.getCurrentLineContents(true, true, 0));
    editor.select(offset, offset + "Simple".length());
    assertEquals("<string name=\"app_name\">|>^Simple<| Application</string>", editor.getCurrentLineContents(true, true, 0));
    editor.enterText("Tester");
    editor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    editor.enterText("d");
    assertEquals("<string name=\"app_name\">Tested^ Application</string>", editor.getCurrentLineContents(true, true, 0));
    editor.invokeAction(EditorFixture.EditorAction.UNDO);
    editor.invokeAction(EditorFixture.EditorAction.UNDO);
    assertEquals("<string name=\"app_name\">Tester^ Application</string>", editor.getCurrentLineContents(true, true, 0));

    editor.invokeAction(EditorFixture.EditorAction.TOGGLE_COMMENT);
    assertEquals("    <!--<string name=\"app_name\">Tester Application</string>-->\n" +
                 "    <string name=\"hello_world\">Hello w^orld!</string>\n" +
                 "    <string name=\"action_settings\">Settings</string>", editor.getCurrentLineContents(false, true, 1));
    editor.moveTo(editor.findOffset(" ", "<string name=\"action", true));
    editor.enterText("    ");
    editor.invokeAction(EditorFixture.EditorAction.FORMAT);
  }
}
