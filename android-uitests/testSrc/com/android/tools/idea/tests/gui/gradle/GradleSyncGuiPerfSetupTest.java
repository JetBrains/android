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
import com.android.utils.PathUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.android.testutils.TestUtils.getWorkspaceFile;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertAbout;
import static java.nio.file.Files.find;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.write;
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
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish(Wait.seconds(10 * 60));
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

  private static void updateVersionAndDependencies(@NotNull File projectRoot) throws IOException {
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
                                                    "\n" +
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
    contents = contents.replaceAll("(classpath\\s\\('com.uber:okbuck:\\d+.\\d+.\\d+'\\)(\\s\\{\n.*\n.*}))?", "");
    contents = contents.replaceAll("(apply plugin: 'com.uber.okbuck')", "");

    contents = contents.replaceAll("(okbuck\\s\\{\n(.*\n){3}+.*})", "");
    contents = contents.replaceAll("(compile 'com.google.auto.service:auto-service:1.0-rc2')", "");
    write(contents, buildFile, Charsets.UTF_8);
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
    contents = contents.replaceAll("playServicesVersion: '\\d+.\\d+.\\d+'", "playServicesVersion: '9.6.1'");
    contents = contents.replaceAll("leakCanaryVersion\\s*: '\\d+.\\d+'", "leakCanaryVersion: '1.4'");
    contents = contents.replaceAll("daggerVersion\\s*: '\\d+.\\d+'", "daggerVersion: '2.7'");
    contents =
      contents.replaceAll("autoCommon\\s*: 'com.google.auto:auto-common:\\d+.\\d+'", "autoCommon: 'com.google.auto:auto-common:0.6'");

    contents = contents +
               "\n\n// Fixes for support lib versions.\n" +
               "ext.deps.other.appcompat = [\n" +
               "        ext.deps.support.appCompat,\n" +
               "        ext.deps.other.appcompat,\n" +
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
    List<Path> allBuildFiles =
      find(projectRoot.toPath(), Integer.MAX_VALUE, (path, attrs) -> path.getFileName().toString().equals("build.gradle"))
        .filter(p -> !PathUtils.toSystemIndependentPath(p).endsWith("gradle/SourceTemplate/app/build.gradle")).collect(Collectors.toList());
    modifyBuildFiles(allBuildFiles);
  }

  private static void modifyBuildFiles(@NotNull List<Path> buildFiles) throws IOException {
    Pattern appPlugin = Pattern.compile("apply plugin:\\s*['\"]com.android.application['\"]");
    Pattern libPlugin = Pattern.compile("apply plugin:\\s*['\"]com.android.library['\"]");
    Pattern javaPlugin = Pattern.compile("apply plugin:\\s*['\"]java['\"]");

    for (Path build : buildFiles) {
      String fileContent = new String(readAllBytes(build));
      if (appPlugin.matcher(fileContent).find() || libPlugin.matcher(fileContent).find()) {
        appendToFile(
          build.toFile(),
          "\n"
          + "android.defaultConfig.javaCompileOptions {\n"
          + "    annotationProcessorOptions.includeCompileClasspath = false\n"
          + "}");

        replaceIfPresent(fileContent, build, "\\s*compile\\s(.*)", "\napi $1");
        replaceIfPresent(fileContent, build, "\\s*provided\\s(.*)", "\ncompileOnly $1");
        replaceIfPresent(fileContent, build, "\\s*testCompile\\s(.*)", "\ntestImplementation $1");
        replaceIfPresent(fileContent, build, "\\s*debugCompile\\s(.*)", "\ndebugImplementation $1");
        replaceIfPresent(fileContent, build, "\\s*releaseCompile\\s(.*)", "\nreleaseImplementation $1");
        replaceIfPresent(fileContent, build, "\\s*androidTestCompile\\s(.*)", "\nandroidTestImplementation $1");
      }
      else if (javaPlugin.matcher(fileContent).find()) {
        searchAndReplace(build, javaPlugin.pattern(), "apply plugin: 'java-library'");
      }
    }
  }

  private static void replaceIfPresent(@NotNull String content, @NotNull Path destination, @NotNull String pattern, @NotNull String replace)
    throws IOException {
    Pattern compiledPattern = Pattern.compile(pattern);
    if (compiledPattern.matcher(content).find()) {
      searchAndReplace(destination, compiledPattern.pattern(), replace);
    }
  }

  private static void appendToFile(@NotNull File file, @NotNull String content) throws IOException {
    write(file.toPath(), (System.lineSeparator() + content).getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND,
          StandardOpenOption.CREATE);
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
    searchAndReplace(file.toPath(), regex, replaceString);
  }

  private static void searchAndReplace(@NotNull Path file, @NotNull String regex, @NotNull String replaceString) throws IOException {
    String contents = new String(readAllBytes(file));
    contents = contents.replaceAll(regex, replaceString);
    write(file, contents.getBytes());
  }

  @Override
  protected void tearDownProject() {
    if (ideFrame().target().isShowing()) {
      ideFrame().closeProject();
    }
    GuiTests.refreshFiles();
  }
}