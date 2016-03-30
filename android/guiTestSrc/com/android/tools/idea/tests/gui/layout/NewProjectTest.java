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

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectionsFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.LayoutEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.RenderErrorPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class NewProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testCreateNewMobileProject() {
    newProject("Test Application").create();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.EDITOR);
    FileFixture layoutFile = guiTest.ideFrame().findExistingFileByRelativePath("app/src/main/res/layout/activity_main.xml");
    layoutFile.requireOpenAndSelected();

    // Verify state of project
    guiTest.ideFrame().requireModuleCount(2);
    AndroidGradleModel appAndroidModel = guiTest.ideFrame().getAndroidProjectForModule("app");
    assertThat(appAndroidModel.getVariantNames()).named("variants").containsExactly("debug", "release");
    assertThat(appAndroidModel.getSelectedVariant().getName()).named("selected variant").isEqualTo("debug");

    AndroidProject model = appAndroidModel.getAndroidProject();
    ApiVersion minSdkVersion = model.getDefaultConfig().getProductFlavor().getMinSdkVersion();
    assertThat(minSdkVersion.getApiString()).named("minSdkVersion API").isEqualTo("19");

    // Make sure that the activity registration uses the relative syntax
    // (regression test for https://code.google.com/p/android/issues/detail?id=76716)
    editor.open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR);
    int offset = editor.findOffset("\".^MainActivity\"");
    assertThat(offset).isNotEqualTo(-1);

    // The language level should be JDK_1_7 since the compile SDK version is assumed to be 21 or higher
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
  public void testNoWarningsInNewProjects() throws IOException {
    // Creates a new default project, and checks that if we run Analyze > Inspect Code, there are no warnings.
    // This checks that our (default) project templates are warnings-clean.
    // The test then proceeds to make a couple of edits and checks that these do not generate additional
    // warnings either.
    newProject("Test Application").create();

    // Insert resValue statements which should not add warnings (since they are generated files; see
    // https://code.google.com/p/android/issues/detail?id=76715
    EditorFixture editor = guiTest.ideFrame().getEditor();
    String buildGradlePath = "app/build.gradle";
    editor.open(buildGradlePath, EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset(null, "applicationId", true));
    editor.enterText("resValue \"string\", \"foo\", \"Typpo Here\"\n");
    guiTest.ideFrame().requireEditorNotification("Gradle files have changed since last project sync").performAction("Sync Now");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();

    InspectionsFixture inspections = guiTest.ideFrame().inspectCode();

    assertThat(inspections.getResults()).isEqualTo(lines(
      "Test Application",
      // This warning is from the "foo" string we created in the Gradle resValue declaration above
      "    Android > Lint > Performance",
      "        Unused resources",
      "            app",
      "                The resource 'R.string.foo' appears to be unused",

      // This warning is unfortunate. We may want to get rid of it.
      "    Android > Lint > Security",
      "        AllowBackup/FullBackupContent Problems",
      "            app",
      "                On SDK version 23 and up, your app data will be automatically backed up and restored on app install. Consider adding the attribute 'android:fullBackupContent' to specify an '@xml' resource which configures which files to backup. More info: https://developer.android.com/preview/backup/index.html",

      // This warning is wrong: http://b.android.com/192605
      "    Android > Lint > Usability",
      "        Missing support for Google App Indexing",
      "            app",
      "                App is not indexable by Google Search; consider adding at least one Activity with an ACTION-VIEW intent filter. See issue explanation for more details."));
  }

  private static String lines(String... strings) {
    StringBuilder sb = new StringBuilder();
    for (String s : strings) {
      sb.append(s).append('\n');
    }
    return sb.toString();
  }

  @Test
  public void testRenderResourceInitialization() throws IOException {
    // Regression test for https://code.google.com/p/android/issues/detail?id=76966
    newProject("Test Application").withBriefNames().withMinSdk("9").create();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.requireName("A.java");
    editor.close();
    editor.requireName("activity_a.xml");
    LayoutEditorFixture layoutEditor = editor.getLayoutEditor(true);
    assertThat(layoutEditor).isNotNull();
    layoutEditor.waitForNextRenderToFinish();
    guiTest.ideFrame().invokeProjectMake();
    layoutEditor.waitForNextRenderToFinish();
    layoutEditor.requireRenderSuccessful();
    guiTest.waitForBackgroundTasks();
  }

  @Test
  public void testLanguageLevelForApi21() {
    newProject("Test Application").withBriefNames().withMinSdk("21").create();

    AndroidGradleModel appAndroidModel = guiTest.ideFrame().getAndroidProjectForModule("app");

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

    Wait.seconds(30).expecting("file to open").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return "A.java".equals(editor.getCurrentFileName());
      }
    });

    editor.open("app/src/main/res/layout/activity_a.xml", EditorFixture.Tab.EDITOR);
    LayoutEditorFixture layoutEditor = editor.getLayoutEditor(true);
    assertThat(layoutEditor).isNotNull();
    layoutEditor.waitForNextRenderToFinish();

    RenderErrorPanelFixture renderErrors = layoutEditor.getRenderErrors();
    String html = renderErrors.getErrorHtml();
    // We could be showing an error message, but if we do, it should *not* say missing styles
    // (should only be showing project render errors)
    assertThat(html).doesNotContain("Missing styles");
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
    private String myMinSdk = "19";
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
    void create() {
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

      guiTest.ideFrame().requestFocusIfLost();

      if (myWaitForSync) {
        guiTest.ideFrame().waitForGradleProjectSyncToFinish();
      }
    }
  }
}