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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;

@RunWith(GuiTestRunner.class)
public class NewProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verify able to create a new project with name containing a space.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project with min sdk 23.
   *   2. Enter a project name with at least one space.
   *   3. Accept all other defaults.
   *   4. Wait for build to finish.
   *   5. Project is created successfully.
   *   Verify:
   *   Successfully created new project with name containing a space.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void createNewProjectNameWithSpace() {
    EditorFixture editor = newProject("Test Application").withMinSdk("23").create()
      .getEditor()
      .open("app/src/main/res/values/strings.xml", EditorFixture.Tab.EDITOR);
    String text = editor.getCurrentFileContents();
    assertThat(text).contains("Test Application");
  }

  /**
   * Verify creating a new project from default template.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   *   <pre>
   *   Steps:
   *   1. From the welcome screen, click on "Start a new Android Studio project".
   *   2. Enter a unique project name.
   *   3. Accept all other defaults.
   *   Verify:
   *   1. Check that the project contains 2 module.
   *   2. Check that MainActivity is in AndroidManifest.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testCreateNewMobileProject() {
    newProject("Test Application").create();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.EDITOR);

    guiTest.ideFrame().requireModuleCount(2);

    // Make sure that the activity registration uses the relative syntax
    // (regression test for https://code.google.com/p/android/issues/detail?id=76716)
    editor.open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR);
    assertThat(editor.getCurrentFileContents()).contains("\".MainActivity\"");
  }

  @Test
  public void testNoWarningsInNewProjects() throws IOException {
    // Creates a new default project, and checks that if we run Analyze > Inspect Code, there are no warnings.
    // This checks that our (default) project templates are warnings-clean.
    // The test then proceeds to make a couple of edits and checks that these do not generate additional
    // warnings either.
    newProject("Test Application").create();

    // Insert resValue statements which should not add warnings (since they are generated files; see
    // https://code.google.com/p/android/issues/detail?id=76715
    String inspectionResults = guiTest.ideFrame()
      .getEditor()
      .open("app/build.gradle", EditorFixture.Tab.EDITOR)
      .moveBetween("", "applicationId")
      .enterText("resValue \"string\", \"foo\", \"Typpo Here\"\n")
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now")
      .waitForGradleProjectSyncToFinish()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    assertThat(inspectionResults).isEqualTo(lines(
      "Project '" + guiTest.getProjectPath() + "' TestApplication",
      // This warning is from the "foo" string we created in the Gradle resValue declaration above
      "    Android > Lint > Performance",
      "        Unused resources",
      "            build.gradle",
      "                The resource 'R.string.foo' appears to be unused",

      // This warning is unfortunate. We may want to get rid of it.
      "    Android > Lint > Security",
      "        AllowBackup/FullBackupContent Problems",
      "            AndroidManifest.xml",
      "                On SDK version 23 and up, your app data will be automatically backed up and restored on app install. Consider adding the attribute 'android:fullBackupContent' to specify an '@xml' resource which configures which files to backup. More info: https://developer.android.com/training/backup/autosyncapi.html",

      // This warning is wrong: http://b.android.com/192605
      "    Android > Lint > Usability",
      "        Missing support for Firebase App Indexing",
      "            AndroidManifest.xml",
      "                App is not indexable by Google Search; consider adding at least one Activity with an ACTION-VIEW intent filter. See issue explanation for more details."));
  }

  @Test
  public void testInferNullity() throws IOException {
    // Creates a new default project, adds a nullable API and then invokes Infer Nullity and
    // confirms that it adds nullability annotations.
    newProject("Test Infer Nullity Application").withPackageName("my.pkg").create();

    // Insert resValue statements which should not add warnings (since they are generated files; see
    // https://code.google.com/p/android/issues/detail?id=76715
    IdeFrameFixture frame = guiTest.ideFrame();
    EditorFixture editor = frame.getEditor();

    editor
      .open("app/src/main/java/my/pkg/MainActivity.java", EditorFixture.Tab.EDITOR)
      .moveBetween(" ", "}")
      .enterText("if (savedInstanceState != null) ;");

    frame
      .openFromMenu(InferNullityDialogFixture::find, "Analyze", "Infer Nullity...")
      .clickOk();

    // Text will be updated when analysis is done
    Wait.seconds(30).expecting("matching nullness")
      .until(() -> {
        String file = editor.getCurrentFileContents();
        return file.contains("@Nullable Bundle savedInstanceState");
      });
  }

  private static String lines(String... strings) {
    StringBuilder sb = new StringBuilder();
    for (String s : strings) {
      sb.append(s).append('\n');
    }
    return sb.toString();
  }

  @RunIn(TestGroup.UNRELIABLE)
  @Test
  public void testRenderResourceInitialization() throws IOException {
    // Regression test for https://code.google.com/p/android/issues/detail?id=76966
    newProject("Test Application").withBriefNames().withMinSdk("9").create();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    assertThat(editor.getCurrentFileName()).isEqualTo("A.java");
    editor.close();
    assertThat(editor.getCurrentFileName()).isEqualTo("activity_a.xml");

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);
    layoutEditor.waitForRenderToFinish();
    guiTest.ideFrame().invokeProjectMake();
    layoutEditor.waitForRenderToFinish();
    assertFalse(layoutEditor.hasRenderErrors());
    guiTest.waitForBackgroundTasks();
  }

  @Test
  public void testLanguageLevelForApi21() {
    newProject("Test Application").withBriefNames().withMinSdk("21").create();

    AndroidModuleModel appAndroidModel = guiTest.ideFrame().getAndroidProjectForModule("app");

    assertThat(appAndroidModel.getAndroidProject().getDefaultConfig().getProductFlavor().getMinSdkVersion().getApiString())
      .named("minSdkVersion API").isEqualTo("21");
    assertThat(appAndroidModel.getJavaLanguageLevel()).named("Gradle Java language level").isSameAs(LanguageLevel.JDK_1_7);
    LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(guiTest.ideFrame().getProject());
    assertThat(projectExt.getLanguageLevel()).named("Project Java language level").isSameAs(LanguageLevel.JDK_1_7);
    for (Module module : ModuleManager.getInstance(guiTest.ideFrame().getProject()).getModules()) {
      LanguageLevelModuleExtension moduleExt = LanguageLevelModuleExtensionImpl.getInstance(module);
      assertThat(moduleExt.getLanguageLevel()).named("Gradle Java language level in module " + module.getName())
        .isSameAs(LanguageLevel.JDK_1_7);
    }
  }

  @Test
  public void testStillBuildingMessage() throws Exception {
    // Creates a new project with minSdk 15, which should use appcompat.
    // Check that if there are render-error messages on first render,
    // they don't include "Missing Styles" (should now talk about project building instead)
    newProject("Test Application").withBriefNames().withMinSdk("15").withoutSync().create();
    final EditorFixture editor = guiTest.ideFrame().getEditor();

    Wait.seconds(5).expecting("file to open").until(() -> "A.java".equals(editor.getCurrentFileName()));

    editor.open("app/src/main/res/layout/activity_a.xml", EditorFixture.Tab.EDITOR);

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);
    layoutEditor.waitForRenderToFinish();

    // We could be showing an error message, but if we do, it should *not* say missing styles
    // (should only be showing project render errors)
    layoutEditor.waitForErrorPanelToContain("Missing styles");
  }

  @NotNull
  private NewProjectDescriptor newProject(@NotNull String name) {
    return new NewProjectDescriptor(name);
  }

  /**
   * Describes a new test project to be created.
   */
  private class NewProjectDescriptor {
    private String myActivity = "MainActivity";
    private String myPkg = "com.android.test.app";
    private String myMinSdk = "15";
    private String myName = "TestProject";
    private String myDomain = "com.android";
    private boolean myWaitForSync = true;

    private NewProjectDescriptor(@NotNull String name) {
      withName(name);
    }

    /**
     * Set a custom package to use in the new project
     */
    NewProjectDescriptor withPackageName(@NotNull String pkg) {
      myPkg = pkg;
      return this;
    }

    /**
     * Set a new project name to use for the new project
     */
    NewProjectDescriptor withName(@NotNull String name) {
      myName = name;
      return this;
    }

    /**
     * Set a custom activity name to use in the new project
     */
    NewProjectDescriptor withActivity(@NotNull String activity) {
      myActivity = activity;
      return this;
    }

    /**
     * Set a custom minimum SDK version to use in the new project
     */
    NewProjectDescriptor withMinSdk(@NotNull String minSdk) {
      myMinSdk = minSdk;
      return this;
    }

    /**
     * Set a custom company domain to enter in the new project wizard
     */
    NewProjectDescriptor withCompanyDomain(@NotNull String domain) {
      myDomain = domain;
      return this;
    }

    /**
     * Picks brief names in order to make the test execute faster (less slow typing in name text fields)
     */
    NewProjectDescriptor withBriefNames() {
      withActivity("A").withCompanyDomain("C").withName("P").withPackageName("a.b");
      return this;
    }

    /** Turns off the automatic wait-for-sync that normally happens on {@link #create} */
    NewProjectDescriptor withoutSync() {
      myWaitForSync = false;
      return this;
    }

    /**
     * Creates a project fixture for this description
     */
    IdeFrameFixture create() {
      WelcomeFrameFixture.find(guiTest.robot()).createNewProject();

      NewProjectWizardFixture newProjectWizard = NewProjectWizardFixture.find(guiTest.robot());

      ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep();
      configureAndroidProjectStep.enterApplicationName(myName).enterCompanyDomain(myDomain).enterPackageName(myPkg);
      guiTest.setProjectPath(configureAndroidProjectStep.getLocationInFileSystem());
      newProjectWizard.clickNext();

      newProjectWizard.getConfigureFormFactorStep().selectMinimumSdkApi(MOBILE, myMinSdk);
      newProjectWizard.clickNext();

      // Skip "Add Activity" step
      newProjectWizard.clickNext();

      newProjectWizard.getChooseOptionsForNewFileStep().enterActivityName(myActivity);
      newProjectWizard.clickFinish();

      if (myWaitForSync) {
        guiTest.ideFrame().waitForGradleProjectSyncToFinish();
      }
      return guiTest.ideFrame();
    }
  }
}
