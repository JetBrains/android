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
package com.android.tools.idea.tests.gui.gradle;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.NullUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.BuildEnvironment;
import com.android.tools.idea.tests.gui.framework.*;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.android.SdkConstants.*;
import static com.android.testutils.TestUtils.getWorkspaceFile;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertTrue;

/**
 * GradleSyncGuiPerfTestSetup launches AndroidStudio using the GUI robot and triggers a project
 * build, for the purpose of preparing/building the sample project to be used by {@see GradleSyncGuiPerfTest}
 * to measure the initial sync performance on a prebuilt project.
 *
 * GradleSyncGuiPerfTestSetup uses the SYNC_PERFTEST_PROJECT_DIR environment variable, and writes a built project
 * to that directory (creating if necessary) if the directory appears to not already be a built project.
 *
 * As of current plans (June 7, 2017), the GradleSyncGuiPerfTest will not meaningfully modify the project
 * directory state during the test.  Since building is the expensive step, there is no known reason why
 * this class couldn't be run once during machine/configuration initialization, and the artifacts retained
 * for all future runs of GradleSyncGuiPerfTest.  In fact, this is the recommended configuration.
 */
@RunIn(TestGroup.SYNC_PERFORMANCE_SETUP)
@RunWith(GuiTestRunner.class)
public class GradleSyncGuiPerfSetupTest {

  @Rule public final GuiTestRule guiTest = new GradleSyncGuiPerfSetupTestRule().withTimeout(30, TimeUnit.MINUTES);

  @Before
  public void setUp() {
    Assume.assumeNotNull(System.getenv("SYNC_PERFTEST_PROJECT_DIR"));
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Test
  @RunIn(TestGroup.SYNC_PERFORMANCE_SETUP)
  public void syncPerfTest() throws IOException, NullPointerException {
    UsageTracker.setInstanceForTest(new NullUsageTracker(new AnalyticsSettings(), new VirtualTimeScheduler()));
    guiTest.importProjectAndWaitForProjectSyncToFinish("android-studio-gradle-test");
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish(Wait.seconds(5 * 60));
    guiTest.ideFrame().invokeProjectMake(Wait.seconds(30 * 60));
  }
}

class GradleSyncGuiPerfSetupTestRule extends GuiTestRule {
  @Override
  public File copyProjectBeforeOpening(@NotNull String projectDirName) throws IOException {
    File masterProjectPath = new File(PathManager.getHomePath() + "/../../external/" + projectDirName);

    File testProjectPath = new File(System.getenv("SYNC_PERFTEST_PROJECT_DIR"));
    if (testProjectPath.isDirectory()) {
      FileUtilRt.delete(testProjectPath);
    }
    FileUtil.copyDir(masterProjectPath, testProjectPath);
    prepareProjectForImport(testProjectPath);
    System.out.println(String.format("Copied project '%1$s' to path '%2$s'", projectDirName, testProjectPath.getPath()));
    return testProjectPath;
  }


  private void prepareProjectForImport(@NotNull File projectRoot) throws IOException {
    assertTrue(projectRoot.getPath(), projectRoot.exists());
    File build = new File(projectRoot, FN_BUILD_GRADLE);
    File settings = new File(projectRoot, FN_SETTINGS_GRADLE);
    assertTrue("Couldn't find build.gradle or settings.gradle in " + projectRoot.getPath(), build.exists() || settings.exists());

    // We need the wrapper for import to succeed
    createGradleWrapper(projectRoot);

    // Override settings just for tests (e.g. sdk.dir)
    updateLocalProperties(projectRoot);

    // Update dependencies to latest, and possibly repository URL too if android.mavenRepoUrl is set
    updateVersionAndDependencies(projectRoot);
  }

  protected static void updateVersionAndDependencies(@NotNull File projectRoot) throws IOException {
    //Update build.gradle in root directory
    updateBuildFile(projectRoot);

    //Update dependencies.gradle
    updateDependenciesFile(projectRoot);

    updateAndroidAptConfiguration(projectRoot);

    //Update dependencies in build.gradle of sub-modules
    searchAndReplace(new File(projectRoot, "outissue/cyclus/build.gradle"), "dependencies \\{\n", "dependencies {\n" +
                                                                                                  "compile deps.support.leanback\n" +
                                                                                                  "compile deps.support.appCompat\n" +
                                                                                                  "compile deps.external.rxjava\n");
    searchAndReplace(new File(projectRoot, "outissue/embrace/build.gradle"), "dependencies \\{\n",
                     "dependencies { compile deps.external.rxjava\n");
    searchAndReplace(new File(projectRoot, "outissue/nutate/build.gradle"), "dependencies \\{\n",
                     "dependencies { compile deps.support.mediarouter\n");
    searchAndReplace(new File(projectRoot, "outissue/edictally/build.gradle"), "compileOnly deps.apt.autoValueAnnotations", "/* $0 */");

    // Remove butterknife plugin.
    for (String path : ImmutableList
      .of("outissue/carnally", "outissue/airified", "Padraig/follicle", "outissue/Glumaceae", "fratry/sodden", "subvola/zelator",
          "subvola/doored", "subvola/transpire", "subvola/atbash", "subvola/gorgoneum/Chordata", "subvola/gorgoneum/metanilic/agaric",
          "subvola/gorgoneum/teerer/polytonal", "subvola/gorgoneum/teerer/Cuphea", "harvestry/Timbira")) {
      searchAndReplace(new File(projectRoot, path + "/build.gradle"), "apply plugin: 'com.jakewharton.butterknife'", "/* $0 */");
    }
  }


  /**
   * Update build.gradle in root directory
   **/
  private static void updateBuildFile(@NotNull File projectRoot) throws IOException {
    File buildFile = new File(projectRoot, "build.gradle");
    String contents = Files.toString(buildFile, Charsets.UTF_8);
    contents = contents.replaceAll("jcenter\\(\\)", AndroidGradleTests.getLocalRepositories() +
                                                    " maven { url \"" + getWorkspaceFile("./prebuilts/maven_repo/android/") + "\" }\n");
    contents = contents.replaceAll("classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'",
                                   "classpath 'com.android.tools.build:gradle:" +
                                   BuildEnvironment.getInstance().getGradlePluginVersion() +
                                   "'");
    contents = contents.replaceAll("(classpath 'com.uber:okbuck:[^']+')", "// $0");
    contents = contents.replaceAll("(classpath 'com.jakewharton:butterknife-gradle-plugin:8.4.0')", "// $1");
    contents = contents.replaceAll("(force 'com.android.support:[^:]*):[^']*'", "$1:" + "25.1.0" + "'");
    contents = contents
      .replaceAll("defaultConfig \\{", "defaultConfig {\njavaCompileOptions.annotationProcessorOptions.includeCompileClasspath = false\n");
    int pos = contents.indexOf("apply plugin: 'com.uber");
    write(contents.substring(0, pos - 1), buildFile, Charsets.UTF_8);
  }

  /**
   * Update dependencies.gradle
   **/
  private static void updateDependenciesFile(@NotNull File projectRoot) throws IOException {
    File dependenciesFile = new File(projectRoot, "dependencies.gradle");
    String contents = Files.toString(dependenciesFile, Charsets.UTF_8);

    contents = contents.replaceAll("buildToolsVersion: '\\d+.\\d+.\\d+',",
                                   "buildToolsVersion: '" + BuildEnvironment.getInstance().getBuildToolsVersion() + "',");
    contents = contents.replaceAll("(minSdkVersion *): \\d+,", "$1: 18,");
    contents = contents.replaceAll("supportVersion *: '\\d+.\\d+.\\d+',", "supportVersion: '25.1.0',");
    contents = contents.replaceAll("('io.reactivex:rxjava):[^']*'", "$1:1.2.3'");
    contents = contents.replaceAll("('com.jakewharton:butterknife[^:]*):[^']*'", "$1:8.4.0'");
    contents = contents.replaceAll("('com.squareup.okio:okio):[^']*'", "$1:1.9.0'");
    contents = contents.replaceAll("('com.jakewharton.rxbinding:rxbinding[^:]*):[^']+'", "$1:1.0.0'");
    contents = contents.replaceAll("('com.google.auto.value:auto-value):[^']*'", "$1:1.3'");
    contents = contents.replaceAll("('com.google.code.gson:gson):[^']+'", "$1:2.8.0'");
    contents = contents.replaceAll("def support = \\[", "def support = [\n" +
                                                        "leanback : \"com.android.support:leanback-v17:\\${versions.supportVersion}\",\n" +
                                                        "mediarouter : \"com.android.support:mediarouter-v7:25.0.1\",\n");
    contents = contents +
               "\n\n// Fixes for support lib versions.\n" +
               "ext.deps.other.appcompat = [\n" +
               "        ext.deps.support.appCompat,\n" +
               "        ext.deps.other.appcompat,\n" +
               "]\n" +
               "\n" +
               "ext.deps.external.butterKnife = [\n" +
               "        ext.deps.support.annotations,\n" +
               "        ext.deps.external.butterKnife,\n" +
               "]\n" +
               "\n" +
               "ext.deps.apt.butterKnifeCompiler = [\n" +
               "        ext.deps.support.annotations,\n" +
               "        ext.deps.apt.butterKnifeCompiler,\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.cast = [\n" +
               "        ext.deps.other.cast,\n" +
               "        ext.deps.support.mediarouter,\n" +
               "        ext.deps.support.appCompat\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.design = [\n" +
               "        ext.deps.support.design,\n" +
               "        ext.deps.other.design,\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.facebook = [\n" +
               "        ext.deps.other.facebook,\n" +
               "        ext.deps.support.cardView,\n" +
               "        \"com.android.support:customtabs:${versions.supportVersion}\",\n" +
               "        \"com.android.support:support-v4:${versions.supportVersion}\",\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.fresco = [\n" +
               "        ext.deps.other.fresco,\n" +
               "        \"com.android.support:support-v4:${versions.supportVersion}\",\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.googleMap = [\n" +
               "        ext.deps.other.googleMap,\n" +
               "        \"com.android.support:support-v4:${versions.supportVersion}\",\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.leanback = [\n" +
               "        ext.deps.other.leanback,\n" +
               "        ext.deps.support.leanback,\n" +
               "]\n" +
               "\n" +
               "ext.deps.playServices.maps = [\n" +
               "        ext.deps.playServices.maps,\n" +
               "        ext.deps.support.appCompat,\n" +
               "        \"com.android.support:support-v4:${versions.supportVersion}\",\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.rave = [\n" +
               "        ext.deps.other.gson,\n" +
               "        ext.deps.other.rave,\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.recyclerview = [\n" +
               "        ext.deps.support.recyclerView,\n" +
               "        ext.deps.other.recyclerview,\n" +
               "]\n" +
               "\n" +
               "ext.deps.other.utils = [\n" +
               "        ext.deps.other.utils,\n" +
               "        \"com.android.support:support-v4:${versions.supportVersion}\",\n" +
               "]\n" +
               "\n" +
               "\n // End support lib version fixes. \n";
    write(contents, dependenciesFile, Charsets.UTF_8);
  }

  private static void updateAndroidAptConfiguration(@NotNull File projectRoot) throws IOException {
    // Remove the android-apt plugin
    Set<String> aptConfigurationProjects =
      ImmutableSet.of(
        "Padraig/endocoele",
        "Padraig/follicle",
        "Padraig/ratafee",
        "Tripoline",
        "fratry/Cosmati",
        "fratry/Krapina",
        "fratry/cepaceous",
        "fratry/crankum",
        "fratry/crapple",
        "fratry/crippling",
        "fratry/endothys",
        "fratry/fortunate",
        "fratry/halsen",
        "fratry/linotype",
        "fratry/matchy",
        "fratry/passbook",
        "fratry/psoriasis",
        "fratry/savory",
        "fratry/sodden",
        "fratry/subradius",
        "fratry/wiredraw",
        "harvestry/Bokhara",
        "harvestry/Timbira",
        "harvestry/digallate",
        "harvestry/isocryme",
        "harvestry/suchness",
        "harvestry/thribble",
        "outissue/Glumaceae",
        "outissue/airified",
        "outissue/carnally",
        "outissue/caudate",
        "outissue/eyesore",
        "outissue/nonparty",
        "outissue/nursing",
        "outissue/situla",
        "outissue/worldway",
        "preprice",
        "subvola/Dipnoi",
        "subvola/Leporis",
        "subvola/absconsa",
        "subvola/aluminize",
        "subvola/atbash",
        "subvola/cleithral",
        "subvola/copsewood",
        "subvola/doored",
        "subvola/emergency",
        "subvola/gorgoneum/Chordata",
        "subvola/gorgoneum/metanilic/agaric",
        "subvola/gorgoneum/teerer/Cuphea",
        "subvola/gorgoneum/teerer/Onondaga",
        "subvola/gorgoneum/teerer/lucrific",
        "subvola/gorgoneum/teerer/perscribe",
        "subvola/gorgoneum/teerer/polytonal",
        "subvola/gorgoneum/teerer/revalenta",
        "subvola/gorgoneum/unwincing",
        "subvola/graphite",
        "subvola/haploidic",
        "subvola/inhumanly",
        "subvola/liming",
        "subvola/ocracy",
        "subvola/remigrate",
        "subvola/suborder",
        "subvola/tourer",
        "subvola/transpire",
        "subvola/unmilked",
        "subvola/wordsmith",
        "subvola/zealotic",
        "subvola/zelator");

    for (String path : aptConfigurationProjects) {
      searchAndReplace(
        new File(projectRoot, path + "/build.gradle"), "apply plugin: 'com\\.neenbedankt\\.android-apt'", "/* $0 */");
      searchAndReplace(new File(projectRoot, path + "/build.gradle"), " apt ", " annotationProcessor ");
    }

    for (String path : ImmutableList.of("subvola/absconsa", "phthalic", "fratry/endothys")) {
      searchAndReplace(
        new File(projectRoot, path + "/build.gradle"), "estApt", "estAnnotationProcessor");
    }
    Set<String> aptPluginProjects =
      ImmutableSet.of(
        "Padraig/arbitrate",
        "Padraig/cuminoin",
        "Padraig/decollete",
        "Padraig/emerse",
        "Padraig/limitary",
        "Padraig/paegle",
        "Padraig/quaestor/triduum",
        "Padraig/signist",
        "fratry/Ormond",
        "fratry/assumpsit",
        "fratry/asteep",
        "fratry/audience",
        "fratry/tentlet",
        "harvestry/Savannah/penumbra",
        "harvestry/eelgrass",
        "harvestry/unwormy",
        "outissue/aricine",
        "outissue/bracciale",
        "outissue/browntail",
        "outissue/caricetum/midship",
        "outissue/caricetum/scientist",
        "outissue/caricetum/skiapod",
        "outissue/coherence",
        "outissue/cyclus",
        "outissue/defusion",
        "outissue/embrace",
        "outissue/extended",
        "outissue/gliadin",
        "outissue/nonjurant",
        "outissue/nonunion",
        "outissue/nutate",
        "outissue/oleometer",
        "outissue/phasmatid",
        "outissue/shortsome",
        "outissue/synarchy",
        "outissue/tetragram",
        "phthalic",
        "subvola/Brittany",
        "subvola/Brittany",
        "subvola/papistry");
    assertThat(aptPluginProjects).containsNoneIn(aptConfigurationProjects);
    for (String path : aptPluginProjects) {
      searchAndReplace(
        new File(projectRoot, path + "/build.gradle"),
        "apply plugin: 'com\\.neenbedankt\\.android-apt'",
        "/* $0 */");
    }
  }


  protected static void createGradleWrapper(@NotNull File projectRoot) throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(projectRoot);
    File path = getWorkspaceFile("tools/external/gradle/gradle-" + GRADLE_LATEST_VERSION + "-bin.zip");
    assertAbout(file()).that(path).named("Gradle distribution path").isFile();
    wrapper.updateDistributionUrl(path);
  }

  /**
   * Replace all occurrence of regex in file
   **/
  private static void searchAndReplace(@NotNull File file, @NotNull String regex, @NotNull String replaceString) throws IOException {
    String contents = Files.toString(file, Charsets.UTF_8);
    contents = contents.replaceAll(regex, replaceString);
    write(contents, file, Charsets.UTF_8);
  }

  @Override
  protected void tearDownProject() {
    if (ideFrame().target().isShowing()) {
      ideFrame().closeProject();
    }
    GuiTests.refreshFiles();
  }
}