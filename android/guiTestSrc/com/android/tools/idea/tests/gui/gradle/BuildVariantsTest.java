/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.BuildVariantsToolWindowFixture;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.util.io.FileUtil.join;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class BuildVariantsTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String MODULE_NAME = "app";

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testSwitchVariantWithFlavor() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("Flavoredlib");

    BuildVariantsToolWindowFixture buildVariants = guiTest.ideFrame().getBuildVariantsWindow();
    buildVariants.selectVariantForModule(MODULE_NAME, "flavor1Release");

    String generatedSourceDirPath = MODULE_NAME + "/build/generated/source/";

    Collection<String> sourceFolders = guiTest.ideFrame().getSourceFolderRelativePaths(MODULE_NAME, SOURCE);
    assertThat(sourceFolders).containsAllOf(
      generatedSourceDirPath + "r/flavor1/release",
      generatedSourceDirPath + "aidl/flavor1/release",
      generatedSourceDirPath + "buildConfig/flavor1/release",
      generatedSourceDirPath + "rs/flavor1/release",
      MODULE_NAME + "/src/flavor1Release/aidl",
      MODULE_NAME + "/src/flavor1Release/java",
      MODULE_NAME + "/src/flavor1Release/jni",
      MODULE_NAME + "/src/flavor1Release/rs");

    Module appModule = guiTest.ideFrame().getModule(MODULE_NAME);
    AndroidFacet androidFacet = AndroidFacet.getInstance(appModule);
    assertNotNull(androidFacet);

    JpsAndroidModuleProperties androidFacetProperties = androidFacet.getProperties();
    assertEquals("assembleFlavor1Release", androidFacetProperties.ASSEMBLE_TASK_NAME);
    // 'release' variant does not have the _android_test_ artifact.
    assertEquals("", androidFacetProperties.ASSEMBLE_TEST_TASK_NAME);

    buildVariants.selectVariantForModule(MODULE_NAME, "flavor1Debug");

    sourceFolders = guiTest.ideFrame().getSourceFolderRelativePaths(MODULE_NAME, SOURCE);
    assertThat(sourceFolders).containsAllOf(
      generatedSourceDirPath + "r/flavor1/debug", generatedSourceDirPath + "aidl/flavor1/debug",
      generatedSourceDirPath + "buildConfig/flavor1/debug", generatedSourceDirPath + "rs/flavor1/debug",
      MODULE_NAME + "/src/flavor1Debug/aidl", MODULE_NAME + "/src/flavor1Debug/java",
      MODULE_NAME + "/src/flavor1Debug/jni", MODULE_NAME + "/src/flavor1Debug/rs");

    assertEquals("assembleFlavor1Debug", androidFacetProperties.ASSEMBLE_TASK_NAME);
    // Verifies that https://code.google.com/p/android/issues/detail?id=83077 is not a bug.
    assertEquals("assembleFlavor1DebugAndroidTest", androidFacetProperties.ASSEMBLE_TEST_TASK_NAME);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void switchingTestArtifacts() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication");

    BuildVariantsToolWindowFixture buildVariants = guiTest.ideFrame().getBuildVariantsWindow();
    assertEquals("Android Instrumentation Tests", buildVariants.getSelectedTestArtifact());

    String androidTestSrc = MODULE_NAME + "/src/androidTest/java";
    String unitTestSrc = MODULE_NAME + "/src/test/java";

    Collection<String> testSourceFolders = guiTest.ideFrame().getSourceFolderRelativePaths(MODULE_NAME, TEST_SOURCE);
    assertThat(testSourceFolders).contains(androidTestSrc);
    assertThat(testSourceFolders).doesNotContain(unitTestSrc);

    buildVariants.selectTestArtifact("Unit Tests");

    testSourceFolders = guiTest.ideFrame().getSourceFolderRelativePaths(MODULE_NAME, TEST_SOURCE);
    assertThat(testSourceFolders).contains(unitTestSrc);
    assertThat(testSourceFolders).doesNotContain(androidTestSrc);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/345 and from IDEA")
  @Test
  // TODO add data provider to UI test infrastruture, similar to JUnit 4
  public void generatedFolders_1_0() throws IOException {
    doTestGeneratedFolders("1.0.1", "2.2.1");
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/345 and from IDEA")
  @Test
  public void generatedFolders_1_1() throws IOException {
    doTestGeneratedFolders("1.1.3", "2.2.1");
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/345 and from IDEA")
  @Test
  public void generatedFolders_1_2() throws IOException {
    doTestGeneratedFolders("1.2.3", "2.4");
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/345 and from IDEA")
  @Test
  public void generatedFolders_1_3() throws IOException {
    doTestGeneratedFolders("1.3.0", "2.4");
  }

  private void doTestGeneratedFolders(@NotNull String pluginVersion, @NotNull String gradleVersion) throws IOException {
    guiTest.importMultiModule();
    guiTest.ideFrame().updateAndroidGradlePluginVersion(pluginVersion);
    guiTest.ideFrame().updateGradleWrapperVersion(gradleVersion);

    // Add generated folders to all kinds of variants.
    File appBuildFile = new File(guiTest.ideFrame().getProjectPath(), join("app", SdkConstants.FN_BUILD_GRADLE));
    assertAbout(file()).that(appBuildFile).isFile();
    String gradleSnippet = "project.afterEvaluate {\n" +
                  "  android.applicationVariants.all { variant ->\n" +
                  "    if (variant.name != 'debug') return" +
                  "\n" +
                  "" +
                  "    File sourceFolder = file(\"${buildDir}/generated/customCode/${variant.dirName}\")\n" +
                  "    variant.addJavaSourceFoldersToModel(sourceFolder)\n" +
                  "\n" +
                  "    def androidTestVariant = variant.testVariant\n" +
                  "    File androidTestSourceFolder = file(\"${buildDir}/generated/customCode/${androidTestVariant.dirName}\")\n" +
                  "    androidTestVariant.addJavaSourceFoldersToModel(androidTestSourceFolder)\n";

    if (pluginVersion.startsWith("1.3")) {
      gradleSnippet +=
                  "\n" +
                  "    def unitTestVariant = variant.unitTestVariant\n" +
                  "    File unitTestSourceFolder = file(\"${buildDir}/generated/customCode/${unitTestVariant.dirName}\")\n" +
                  "    unitTestVariant.addJavaSourceFoldersToModel(unitTestSourceFolder)\n";
    }

    gradleSnippet += "}\n}";

    appendToFile(appBuildFile, gradleSnippet);
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    BuildVariantsToolWindowFixture buildVariants = guiTest.ideFrame().getBuildVariantsWindow();
    assertEquals("Android Instrumentation Tests", buildVariants.getSelectedTestArtifact());

    String generatedSourceDirPath = MODULE_NAME + "/build/generated/customCode/";
    String mainSrc = generatedSourceDirPath + "debug";
    String androidTestSrc =  generatedSourceDirPath + "androidTest/debug";
    String unitTestSrc = generatedSourceDirPath + "test/debug";

    if (compareVersions(pluginVersion, "1.1") < 0) {
      // In 1.0, we used "test/debug" for android tests and there was no concept of unit testing.
      androidTestSrc = unitTestSrc;
      unitTestSrc = null;
    }

    Collection<String> sourceFolders = guiTest.ideFrame().getSourceFolderRelativePaths(MODULE_NAME, SOURCE);
    assertThat(sourceFolders).contains(mainSrc);
    assertThat(sourceFolders).containsNoneOf(androidTestSrc, unitTestSrc);
    Collection<String> testSourceFolders = guiTest.ideFrame().getSourceFolderRelativePaths(MODULE_NAME, TEST_SOURCE);
    assertThat(testSourceFolders).contains(androidTestSrc);
    assertThat(testSourceFolders).containsNoneOf(unitTestSrc, mainSrc);

    if (compareVersions(pluginVersion, "1.1") >= 0) {
      buildVariants.selectTestArtifact("Unit Tests");

      sourceFolders = guiTest.ideFrame().getSourceFolderRelativePaths(MODULE_NAME, SOURCE);
      assertThat(sourceFolders).contains(mainSrc);
      assertThat(sourceFolders).containsNoneOf(androidTestSrc, unitTestSrc);

      testSourceFolders = guiTest.ideFrame().getSourceFolderRelativePaths(MODULE_NAME, TEST_SOURCE);
      if (compareVersions(pluginVersion, "1.3") >= 0) {
        // In 1.3 we started to include unit testing generated folders in the model.
        assertThat(testSourceFolders).contains(unitTestSrc);
        assertThat(testSourceFolders).containsNoneOf(androidTestSrc, mainSrc);
      } else {
        assertThat(testSourceFolders).containsNoneOf(unitTestSrc, androidTestSrc, mainSrc);
      }
    }
  }

  private static int compareVersions(@NotNull String lhs, @NotNull String rhs) {
    return Revision.parseRevision(lhs).compareTo(Revision.parseRevision(rhs),
                                                        // Treat 1.3.0-beta3 as 1.3:
                                                        Revision.PreviewComparison.IGNORE);
  }
}
