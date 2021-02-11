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
package com.android.tools.idea.tests.gui.instantapp;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.wizard.template.Language.Java;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EnableInstantAppSupportDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that newly created Instant App projects do not have errors in them
 */
@RunWith(GuiTestRemoteRunner.class)
public class NewInstantAppTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  //Not putting this in before() as I plan to add some tests that work on non-default projects.
  private void createAndOpenDefaultAIAProject(@NotNull String projectName, @Nullable String activityName) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    newProjectWizard
      .getChooseAndroidProjectStep()
      .chooseActivity(activityName == null ? "Empty Activity" : activityName)
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .setSourceLanguage(Java)
      .enterName(projectName)
      .selectMinimumSdkApi(23)
      .wizard()
      .clickFinishAndWaitForSyncToFinish();

    guiTest.ideFrame()
      .findRunApplicationButton().waitUntilEnabledAndShowing(); // Wait for the toolbar to be ready

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(EnableInstantAppSupportDialogFixture::find, "Refactor", "Enable Instant Apps Support...")
      .clickOk();

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane();
  }

  @Test
  public void testNoWarningsInDefaultNewInstantAppProjects() {
    String projectName = "Warning";
    createAndOpenDefaultAIAProject(projectName, null);

    String inspectionResults = guiTest.ideFrame()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    verifyOnlyExpectedWarnings(inspectionResults,
                               "InspectionViewTree",
                               "    Android",
                               "        Lint",
                               "            Correctness",
                               "                Obsolete Gradle Dependency",
                               "                    build.gradle",
                               "                        A newer version of .*",
                               "            Performance",
                               "                Unused resources",
                               "                    mobile_navigation.xml",
                               "                        The resource 'R.navigation.mobile_navigation' appears to be unused",
                               "            Security",
                               "                AllowBackup/FullBackupContent Problems",
                               "                    AndroidManifest.xml",
                               "                        On SDK version 23 and up, your app data will be automatically backed up and .*",
                               "            Usability",
                               "                Missing support for Firebase App Indexing",
                               "                    AndroidManifest.xml",
                               "                        App is not indexable by Google Search; consider adding at least one Activity .*",
                               "    Java",
                               "        Declaration redundancy",
                               "            Redundant throws clause",
                               "                ExampleInstrumentedTest",
                               "                ExampleUnitTest",
                               "                    The declared exception 'Exception' is never thrown",
                               "            Unnecessary module dependency",
                               "                app",
                               "                    Module 'app' sources do not depend on module 'base' sources",
                               "                    Module 'app' sources do not depend on module 'feature' sources",
                               "                feature",
                               "                    Module 'feature' sources do not depend on module 'base' sources",
                               "    XML",
                               "        Unused XML schema declaration",
                               "            AndroidManifest.xml",
                               "                Namespace declaration is never used",
                               "        XML tag empty body",
                               "            strings.xml",
                               "                XML tag has empty body"
    );
  }

  @Test
  public void testCanBuildDefaultNewInstantAppProjects() {
    createAndOpenDefaultAIAProject("BuildApp", null);

    assertAbout(file()).that(guiTest.getProjectPath("app/src/main/res/layout/activity_main.xml")).isFile();

    String manifestContent = guiTest.getProjectFileText("app/src/main/AndroidManifest.xml");
    assertThat(manifestContent).contains("xmlns:dist=\"http://schemas.android.com/apk/distribution\"");
    assertThat(manifestContent).contains("<dist:module dist:instant=\"true\" />");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildNewInstantAppProjectsWithEmptyActivityWithoutUrls() {
    createAndOpenDefaultAIAProject("BuildApp", null);

    assertAbout(file()).that(guiTest.getProjectPath("app/src/main/res/layout/activity_main.xml")).isFile();

    String manifestContent = guiTest.getProjectFileText("app/src/main/AndroidManifest.xml");
    assertThat(manifestContent).contains("android.intent.action.MAIN");
    assertThat(manifestContent).contains("android.intent.category.LAUNCHER");
    assertThat(manifestContent).doesNotContain("android:host=");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildNewInstantAppProjectsWithLoginActivity() {
    createAndOpenDefaultAIAProject("BuildApp", null);
    guiTest.ideFrame()
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Login Activity")
      .clickFinishAndWaitForSyncToFinish();

    assertThat(guiTest.getProjectFileText("app/src/main/res/values/strings.xml"))
      .contains("title_activity_login");
    assertAbout(file()).that(guiTest.getProjectPath("app/src/main/res/layout/activity_login.xml")).isFile();

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void newInstantAppProjectWithFullScreenActivity() {
    createAndOpenDefaultAIAProject("BuildApp", "Fullscreen Activity");
    guiTest.ideFrame().getEditor()
           .open("app/src/main/res/layout/activity_fullscreen.xml")
           .open("app/src/main/res/values/attrs.xml") // Make sure "Full Screen" themes, colors and styles are on the base module
           .moveBetween("FullscreenAttrs", "")
           .open("app/src/main/res/values/colors.xml")
           .moveBetween("black_overlay", "")
           .open("app/src/main/res/values/themes.xml")
           .moveBetween("Theme.BuildApp.Fullscreen", "")
           .moveBetween("FullscreenContainer", "")
           .open("app/src/main/res/values-night/themes.xml")
           .moveBetween("fullscreenBackgroundColor", "")
           .moveBetween("fullscreenTextColor", "");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  // b/68122671
  @Test
   public void addMapActivityToExistingIappModule() {
    String debugPath = "app/src/debug/res/values/google_maps_api.xml";
    String releasePath = "app/src/release/res/values/google_maps_api.xml";
    createAndOpenDefaultAIAProject("BuildApp", null);
    guiTest.ideFrame()
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Google", "Google Maps Activity")
      .clickFinishAndWaitForSyncToFinish();

    assertAbout(file()).that(guiTest.getProjectPath(debugPath)).isFile();
    assertAbout(file()).that(guiTest.getProjectPath(releasePath)).isFile();
  }


  // b/68478730
  @Test
  public void addPrimaryDetailActivityToExistingIappModule() {
    createAndOpenDefaultAIAProject("BuildApp", null);
    guiTest.ideFrame()
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Primary/Detail Flow")
      .clickFinishAndWaitForSyncToFinish();

    String baseStrings = guiTest.getProjectFileText("app/src/main/res/values/strings.xml");
    assertThat(baseStrings).contains("title_item_detail");
    assertThat(baseStrings).contains("title_item_list");
  }

  // b/68684401
  @Test
  public void addFullscreenActivityToExistingIappModule() {
    createAndOpenDefaultAIAProject("BuildApp", null);
    guiTest.ideFrame()
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Fullscreen Activity")
      .clickFinishAndWaitForSyncToFinish();

    assertThat(guiTest.getProjectFileText("app/src/main/res/values/strings.xml"))
      .contains("title_activity_fullscreen");
  }

  // With warnings coming from multiple projects the order of warnings is not deterministic, also there are some warnings that show up only
  // on local machines. This method allows us to check that the warnings in the actual result are a sub-set of the expected warnings.
  // This is not a perfect solution, but this state where we have multiple warnings on a new project should only be temporary
  public static void verifyOnlyExpectedWarnings(@NotNull String actualResults, @NotNull String... acceptedWarnings) {
    ArrayList<String> actualResultLines = new ArrayList<>();

    outLoop:
    for (String resultLine : actualResults.split("\n")) {
      for (String acceptedWarning : acceptedWarnings) {
        if (resultLine.matches(acceptedWarning)) {
          continue outLoop;
        }
      }
      actualResultLines.add(resultLine);
    }

    assertThat(actualResultLines).isEmpty();
  }
}
