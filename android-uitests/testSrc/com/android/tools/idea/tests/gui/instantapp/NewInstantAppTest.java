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

import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.instantapp.InstantAppUrlFinder;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static com.android.SdkConstants.GRADLE_PLUGIN_AIA_VERSION;
import static com.android.tools.idea.instantapp.InstantApps.setInstantAppPluginVersion;
import static com.android.tools.idea.instantapp.InstantApps.setInstantAppSdkLocation;
import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.System.getenv;

/**
 * Test that newly created Instant App projects do not have errors in them
 */
@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRunner.class)
public class NewInstantAppTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void before() {
    setInstantAppPluginVersion(AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion());
    setInstantAppSdkLocation("TestValue");
  }

  @After
  public void after() {
    setInstantAppPluginVersion(GRADLE_PLUGIN_AIA_VERSION);
    setInstantAppSdkLocation(getenv("WH_SDK"));
  }

  //Not putting this in before() as I plan to add some tests that work on non-default projects.
  private void createAndOpenDefaultAIAProject(@NotNull String projectName, @Nullable String featureModuleName) {
    //TODO: There is some commonality between this code, the code in NewProjectTest and further tests I am planning, but there are also
    //      differences. Once AIA tests are completed this should be factored out into the NewProjectWizardFixture
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep();
    configureAndroidProjectStep
      .enterCompanyDomain("test.android.com")
      .enterApplicationName(projectName);
    guiTest.setProjectPath(configureAndroidProjectStep.getLocationInFileSystem());

    newProjectWizard
      .clickNext() // Complete project configuration
      .getConfigureFormFactorStep()
      .selectMinimumSdkApi(MOBILE, "16")
      .selectInstantAppSupport(MOBILE);

    newProjectWizard
      .clickNext(); // Complete form factor configuration

    if (featureModuleName != null) {
      newProjectWizard
        .getConfigureInstantModuleStep()
        .enterFeatureModuleName(featureModuleName);
    }

    newProjectWizard
      .clickNext() // Complete configuration of Instant App Module
      .clickNext() // Complete "Add Activity" step
      .clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  @Test
  public void testNoWarningsInDefaultNewInstantAppProjects() throws IOException {
    String projectName = "Warning";
    createAndOpenDefaultAIAProject(projectName, null);

    String inspectionResults = guiTest.ideFrame()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    verifyOnlyExpectedWarnings(inspectionResults,
                               "    Android\n" +
                               "        Android Resources Validation\n" +
                               "            AndroidManifest.xml\n" +
                               "                Unresolved class 'MainActivity'\n" +
                               "        Unknown Android XML attribute\n" +
                               "            AndroidManifest.xml\n" +
                               "                Unknown attribute split\n" +
                               "    Android Lint: Correctness\n" +
                               "        Gradle Dynamic Version\n" +
                               "            build.gradle\n" +
                               "                Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:25.+)\n" +
                               "        Missing Android XML namespace\n" +
                               "            AndroidManifest.xml\n" +
                               "                Attribute is missing the Android namespace prefix\n" +
                               "        Obsolete Gradle Dependency\n" +
                               "            build.gradle\n" +
                               "                Old buildToolsVersion 25.0.1; recommended version is 25.0.2 or later\n" +
                               "    Declaration redundancy\n" +
                               "        Unnecessary module dependency\n" +
                               "            app\n" +
                               "                Module 'app' sources do not depend on module 'base' sources\n" +
                               "                Module 'app' sources do not depend on module 'feature' sources\n" +
                               "            basesplit\n" +
                               "                Module 'basesplit' sources do not depend on module 'base' sources\n" +
                               "            feature\n" +
                               "                Module 'feature' sources do not depend on module 'base' sources\n" +
                               "            featuresplit\n" +
                               "                Module 'featuresplit' sources do not depend on module 'base' sources\n" +
                               "                Module 'featuresplit' sources do not depend on module 'basesplit' sources\n" +
                               "                Module 'featuresplit' sources do not depend on module 'feature' sources\n" +
                               "            instantapp\n" +
                               "                Module 'instantapp' sources do not depend on module 'basesplit' sources\n" +
                               "                Module 'instantapp' sources do not depend on module 'featuresplit' sources\n" +
                               "    Spelling\n" +
                               "        Typo\n" +
                               "            AndroidManifest.xml\n" +
                               "                Typo: In word 'baselib'\n" +
                               "                Typo: In word 'featuresplit'\n" +
                               "                Typo: In word 'instantapp'\n" +
                               "                Typo: In word 'instantapps'\n" +
                               "                Typo: In word 'instantapps'\n" +
                               "    XML\n" +
                               "        Unused XML schema declaration\n" +
                               "            AndroidManifest.xml\n" +
                               "            activity_main.xml\n" +
                               "                Namespace declaration is never used\n" +
                               "        XML tag empty body\n" +
                               "            strings.xml\n" +
                               "                XML tag has empty body\n"
    );
  }

  @Test
  public void testCanBuildDefaultNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("BuildApp", null);

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testValidPathInDefaultNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("RouteApp", null);

    Module module = guiTest.ideFrame().getModule("basesplit");
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertThat(facet).isNotNull();
    assertThat(new InstantAppUrlFinder(MergedManifest.get(facet)).getAllUrls()).isNotEmpty();
  }

  @Test
  public void testCanCustomiseFeatureModuleInNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("SetFeatureNameApp", "testfeaturename");

    guiTest.ideFrame().getModule("testfeaturename");
    guiTest.ideFrame().getModule("testfeaturenamesplit");
  }

  // With warnings coming from multiple projects the order of warnings is not deterministic, also there are some warnings that show up only
  // on local machines. This method allows us to check that the warnings in the actual result are a sub-set of the expected warnings.
  // This is not a perfect solution, but this state where we have multiple warnings on a new project should only be temporary
  private static void verifyOnlyExpectedWarnings(@NotNull String actualResults, @NotNull String acceptedWarnings) {
    ArrayList<String> lines = new ArrayList<>(Arrays.asList(actualResults.split("\n")));

    // Ignore the first line of the error report
    for (String line : lines.subList(1, lines.size())) {
      assertThat(acceptedWarnings.split("\n")).asList().contains(line);
    }
  }
}
