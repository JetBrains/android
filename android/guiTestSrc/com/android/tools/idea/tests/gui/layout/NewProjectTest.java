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
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FileFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectionsFixture;
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
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.npw.FormFactorUtils.FormFactor.MOBILE;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NewProjectTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testCreateNewMobileProject() {
    newProject("Test Application").create();
    FileFixture layoutFile = myProjectFrame.findExistingFileByRelativePath("app/src/main/res/layout/activity_main.xml");
    layoutFile.requireOpenAndSelected();

    // Verify state of project
    myProjectFrame.requireModuleCount(2);
    AndroidGradleModel appAndroidModel = myProjectFrame.getAndroidProjectForModule("app");
    assertThat(appAndroidModel.getVariantNames()).as("variants").containsOnly("debug", "release");
    assertThat(appAndroidModel.getSelectedVariant().getName()).as("selected variant").isEqualTo("debug");

    AndroidProject model = appAndroidModel.getAndroidProject();
    ApiVersion minSdkVersion = model.getDefaultConfig().getProductFlavor().getMinSdkVersion();
    assertNotNull("minSdkVersion", minSdkVersion);
    assertThat(minSdkVersion.getApiString()).as("minSdkVersion API").isEqualTo("19");

    // Make sure that the activity registration uses the relative syntax
    // (regression test for https://code.google.com/p/android/issues/detail?id=76716)
    EditorFixture editor = myProjectFrame.getEditor();
    editor.open("app/src/main/AndroidManifest.xml");
    int offset = editor.findOffset("\".^MainActivity\"");
    assertTrue(offset != -1);

    // Creating a project with minSdkVersion 19 should leave the Java language level as Java 6
    // For L and higher we use Java 7 language level; that is tested separately in testLanguageLevelForApi21
    assertThat(appAndroidModel.getJavaLanguageLevel()).as("Gradle Java language level").isSameAs(LanguageLevel.JDK_1_6);
    LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(myProjectFrame.getProject());
    assertThat(projectExt.getLanguageLevel()).as("Project Java language level").isSameAs(LanguageLevel.JDK_1_6);
    for (Module module : ModuleManager.getInstance(myProjectFrame.getProject()).getModules()) {
      LanguageLevelModuleExtension moduleExt = LanguageLevelModuleExtensionImpl.getInstance(module);
      assertThat(moduleExt.getLanguageLevel()).as("Gradle Java language level in module " + module.getName()).isNull();
    }
  }

  @Test @IdeGuiTest
  public void testNoWarningsInNewProjects() throws IOException {
    // Creates a new default project, and checks that if we run Analyze > Inspect Code, there are no warnings.
    // This checks that our (default) project templates are warnings-clean.
    // The test then proceeds to make a couple of edits and checks that these do not generate additional
    // warnings either.
    newProject("Test Application").create();

    // Insert resValue statements which should not add warnings (since they are generated files; see
    // https://code.google.com/p/android/issues/detail?id=76715
    EditorFixture editor = myProjectFrame.getEditor();
    String buildGradlePath = "app/build.gradle";
    editor.open(buildGradlePath, EditorFixture.Tab.EDITOR);
    editor.moveTo(editor.findOffset("defaultConfig {", null, true));
    editor.enterText("\nresValue \"string\", \"foo\", \"Typpo Here\"");
    myProjectFrame.requireEditorNotification("Gradle files have changed since last project sync").performAction("Sync Now");
    myProjectFrame.waitForGradleProjectSyncToFinish();

    InspectionsFixture inspections = myProjectFrame.inspectCode();

    assertEquals("Test Application\n" +
                 // This warning is from the "foo" string we created in the Gradle resValue declaration above
                 "    Android Lint\n" +
                 "        Unused resources\n" +
                 "            app\n" +
                 "                The resource 'R.string.foo' appears to be unused\n" +
                 // This warning is wrong: https://code.google.com/p/android/issues/detail?id=76719
                 "    Assignment issues\n" +
                 "        Incompatible type assignments\n" +
                 "            app\n" +
                 "                'dependencies' cannot be applied to '(groovy.lang.Closure)'\n" +
                 "            build.gradle\n" +
                 "                'dependencies' cannot be applied to '(groovy.lang.Closure)'\n",
                 inspections.getResults());
  }

  @Test @IdeGuiTest
  public void testRenderResourceInitialization() throws IOException {
    // Regression test for https://code.google.com/p/android/issues/detail?id=76966
    newProject("Test Application").withBriefNames().withMinSdk("9").create();

    EditorFixture editor = myProjectFrame.getEditor();
    editor.requireName("activity_a.xml");
    LayoutEditorFixture layoutEditor = editor.getLayoutEditor(false);
    assertNotNull("Layout editor was not showing", layoutEditor);
    layoutEditor.waitForNextRenderToFinish();
    myProjectFrame.invokeProjectMake();
    layoutEditor.waitForNextRenderToFinish();
    layoutEditor.requireRenderSuccessful();
    myProjectFrame.waitForBackgroundTasksToFinish();
  }

  @Test @IdeGuiTest
  public void testLanguageLevelForApi21() {
    // Verifies that creating a project with L will set the language level correctly
    // both in the generated Gradle model as well as in the synced project and modules

    // "20+" here should change to 21 as soon as L goes out of preview state
    newProject("Test Application").withBriefNames().withMinSdk("21").create();

    AndroidGradleModel appAndroidModel = myProjectFrame.getAndroidProjectForModule("app");
    AndroidProject model = appAndroidModel.getAndroidProject();
    ApiVersion minSdkVersion = model.getDefaultConfig().getProductFlavor().getMinSdkVersion();
    assertNotNull("minSdkVersion", minSdkVersion);

    // If this test fails, verify that
    //   (1) you have the L preview installed in the SDK on the test machine
    //   (2) the associated JDK is JDK 7 or higher
    assertThat(minSdkVersion.getApiString()).as("minSdkVersion API").isEqualTo("L");
    assertThat(appAndroidModel.getJavaLanguageLevel()).as("Gradle Java language level").isSameAs(LanguageLevel.JDK_1_7);
    LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(myProjectFrame.getProject());
    assertThat(projectExt.getLanguageLevel()).as("Project Java language level").isSameAs(LanguageLevel.JDK_1_7);
    for (Module module : ModuleManager.getInstance(myProjectFrame.getProject()).getModules()) {
      LanguageLevelModuleExtension moduleExt = LanguageLevelModuleExtensionImpl.getInstance(module);
      assertThat(moduleExt.getLanguageLevel()).as("Gradle Java language level in module " + module.getName()).isNull();
    }
  }

  @Test @IdeGuiTest
  public void testStillBuildingMessage() throws Exception {
    // Creates a new project with minSdk 15, which should use appcompat.
    // Check that if there are render-error messages on first render,
    // they don't include "Missing Styles" (should now talk about project building instead)
    newProject("Test Application").withBriefNames().withMinSdk("15").withoutSync().create();
    EditorFixture editor = myProjectFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_a.xml", EditorFixture.Tab.DESIGN);
    LayoutEditorFixture layoutEditor = editor.getLayoutEditor(true);
    assertNotNull(layoutEditor);
    layoutEditor.waitForNextRenderToFinish();

    RenderErrorPanelFixture renderErrors = layoutEditor.getRenderErrors();
    String html = renderErrors.getErrorHtml();
    // We could be showing an error message, but if we do, it should *not* say missing styles
    // (should only be showing project render errors)
    assertFalse(html, html.contains("Missing styles"));
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
      findWelcomeFrame().clickNewProjectButton();

      NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

      ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep();
      configureAndroidProjectStep.enterApplicationName(myName).enterCompanyDomain(myDomain).enterPackageName(myPkg);
      File projectPath = configureAndroidProjectStep.getLocationInFileSystem();
      newProjectWizard.clickNext();

      newProjectWizard.getConfigureFormFactorStep().selectMinimumSdkApi(MOBILE, myMinSdk);
      newProjectWizard.clickNext();

      // Skip "Add Activity" step
      newProjectWizard.clickNext();

      newProjectWizard.getChooseOptionsForNewFileStep().enterActivityName(myActivity);
      newProjectWizard.clickFinish();

      myProjectFrame = findIdeFrame(myName, projectPath);
      if (myWaitForSync) {
        myProjectFrame.waitForGradleProjectSyncToFinish();
      }
    }
  }
}