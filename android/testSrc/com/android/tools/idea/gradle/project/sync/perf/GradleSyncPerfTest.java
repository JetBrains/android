/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.perf;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.testutils.TestUtils.getSdk;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

import com.android.SdkConstants;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.BuildEnvironment;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Tests to collect performance data for Gradle Sync
 */
public class GradleSyncPerfTest extends AndroidGradleTestCase {
  private TestUsageTracker myUsageTracker;
  private VirtualTimeScheduler myScheduler;

  @Override
  public void setUp() throws Exception {
    FSRecords.invalidateCaches();
    super.setUp();
    // Setup up an instance of the JournalingUsageTracker using defined spool directory and
    // virtual time scheduler.
    myScheduler = new VirtualTimeScheduler();

    String spoolLocation = System.getenv("ANDROID_SPOOL_HOME");
    if (spoolLocation == null) {
      // Use project directory so it will be cleaned up in tearDown
      spoolLocation = getProject().getBasePath();
    }
    myUsageTracker = new TestUsageTracker(myScheduler);

    Project project = getProject();
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myScheduler.advanceBy(0);
      myUsageTracker.close();
      UsageTracker.cleanAfterTesting();
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  private static File findSdkPath() {
    String localSdkPath = System.getenv(SdkConstants.ANDROID_HOME_ENV);

    if (localSdkPath != null) {
      File localSdk = new File(localSdkPath);
      if (localSdk.exists()) {
        return localSdk;
      }
    }

    localSdkPath = System.getenv(SdkConstants.ANDROID_SDK_ROOT_ENV);

    if (localSdkPath != null) {
      File localSdk = new File(localSdkPath);
      if (localSdk.exists()) {
        return localSdk;
      }
    }

    return getSdk().toFile();
  }

  @Override
  protected void patchPreparedProject(@NotNull File projectRoot,
                                      @Nullable String gradleVersion,
                                      @Nullable String gradlePluginVersion,
                                      @Nullable String kotlinVersion,
                                      @Nullable String ndkVersion,
                                      @Nullable String compileSdkVersion,
                                      File... localRepos)
    throws IOException {
    // Override settings just for tests (e.g. sdk.dir)
    AndroidGradleTests.updateLocalProperties(projectRoot, findSdkPath());
    // We need the wrapper for import to succeed
    AndroidGradleTests.createGradleWrapper(projectRoot, gradleVersion != null ? gradleVersion : GRADLE_LATEST_VERSION);

    //Update build.gradle in root directory
    updateBuildFile(gradlePluginVersion != null ? gradlePluginVersion : BuildEnvironment.getInstance().getGradlePluginVersion());

    //Update dependencies.gradle
    updateDependenciesFile();

    updateAndroidAptConfiguration();

    //Update dependencies in build.gradle of sub-modules
    searchAndReplace("outissue/cyclus/build.gradle", "dependencies \\{\n", "dependencies {\n" +
                                                                           "compile deps.support.leanback\n" +
                                                                           "compile deps.support.appCompat\n" +
                                                                           "compile deps.external.rxjava\n");
    searchAndReplace("outissue/embrace/build.gradle", "dependencies \\{\n", "dependencies { compile deps.external.rxjava\n");
    searchAndReplace("outissue/nutate/build.gradle", "dependencies \\{\n", "dependencies { compile deps.support.mediarouter\n");
    searchAndReplace("outissue/edictally/build.gradle", "compileOnly deps.apt.autoValueAnnotations", "/* $0 */");

    // Remove butterknife plugin.
    for (String path : ImmutableList
      .of("outissue/carnally", "outissue/airified", "Padraig/follicle", "outissue/Glumaceae", "fratry/sodden", "subvola/zelator",
          "subvola/doored", "subvola/transpire", "subvola/atbash", "subvola/gorgoneum/Chordata", "subvola/gorgoneum/metanilic/agaric",
          "subvola/gorgoneum/teerer/polytonal", "subvola/gorgoneum/teerer/Cuphea", "harvestry/Timbira")) {
      searchAndReplace(path + "/build.gradle", "apply plugin: 'com.jakewharton.butterknife'", "/* $0 */");
    }
  }

  private void updateAndroidAptConfiguration() throws IOException {
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
        path + "/build.gradle", "apply plugin: 'com\\.neenbedankt\\.android-apt'", "/* $0 */");
      searchAndReplace(path + "/build.gradle", " apt ", " annotationProcessor ");
    }

    for (String path : ImmutableList.of("subvola/absconsa", "phthalic", "fratry/endothys")) {
      searchAndReplace(
        path + "/build.gradle", "estApt", "estAnnotationProcessor");
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
        path + "/build.gradle",
        "apply plugin: 'com\\.neenbedankt\\.android-apt'",
        "/* $0 */");
    }
  }

  public void testGradleSyncPerf() throws Exception {
    loadProject("android-studio-gradle-test");

    runWriteCommandAction(getProject(), () -> {
      try {
        // Build the project, since sync times can be different for built/unbuilt projects
        GradleBuildInvoker.getInstance(getProject()).generateSources(ModuleManager.getInstance(getProject()).getModules());

        // Do a few syncs to warm up the JVM to get typical real-world runtimes
        requestSyncAndWait();
        requestSyncAndWait();
        requestSyncAndWait();

        UsageTracker.setWriterForTest(myUsageTracker); // Start logging data for performance dashboard
        requestSyncAndWait();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  // Update build.gradle in root directory
  private void updateBuildFile(@NotNull String gradlePluginVersion) throws IOException {
    File buildFile = getAbsolutionFilePath("build.gradle");
    String contents = Files.toString(buildFile, StandardCharsets.UTF_8);
    contents = contents.replaceAll("jcenter\\(\\)", AndroidGradleTests.getLocalRepositoriesForGroovy());
    contents = contents.replaceAll("classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'",
                                   "classpath 'com.android.tools.build:gradle:" +
                                   gradlePluginVersion +
                                   "'");
    contents = contents.replaceAll("(classpath 'com.uber:okbuck:[^']+')", "// $0");
    contents = contents.replaceAll("(classpath 'com.jakewharton:butterknife-gradle-plugin:8.4.0')", "// $1");
    contents = contents.replaceAll("(force 'com.android.support:[^:]*):[^']*'", "$1:" + "25.1.0" + "'");
    int pos = contents.indexOf("apply plugin: 'com.uber");
    write(contents.substring(0, pos - 1), buildFile, StandardCharsets.UTF_8);
  }

  // Update dependencies.gradle
  private void updateDependenciesFile() throws IOException {
    File dependenciesFile = getAbsolutionFilePath("dependencies.gradle");
    String contents = Files.toString(dependenciesFile, StandardCharsets.UTF_8);

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
    write(contents, dependenciesFile, StandardCharsets.UTF_8);
  }

  // Returns full path, given relative path to root directory
  @NotNull
  private File getAbsolutionFilePath(@NotNull String relativePath) {
    File projectRoot = virtualToIoFile(PlatformTestUtil.getOrCreateProjectBaseDir(getProject()));
    return new File(projectRoot, relativePath);
  }

  // Replace all occurrence of regex in file
  private void searchAndReplace(@NotNull String relativePath, @NotNull String regex, @NotNull String replaceString) throws IOException {
    searchAndReplace(getAbsolutionFilePath(relativePath), regex, replaceString);
  }

  // Replace all occurrence of regex in file
  private void searchAndReplace(@NotNull File file, @NotNull String regex, @NotNull String replaceString) throws IOException {
    String contents = Files.toString(file, StandardCharsets.UTF_8);
    contents = contents.replaceAll(regex, replaceString);
    write(contents, file, StandardCharsets.UTF_8);
  }
}
