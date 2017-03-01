/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.perf.idea.gradle.project.sync;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.JournalingUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.BuildEnvironment;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;

import static com.google.common.io.Files.write;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

/**
 * Tests to collect performance data for Gradle Sync
 */
public class GradleSyncPerfTest extends AndroidGradleTestCase {
  private JournalingUsageTracker myUsageTracker;
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
    myUsageTracker = new JournalingUsageTracker(new AnalyticsSettings(), myScheduler, Paths.get(spoolLocation));
    UsageTracker.setInstanceForTest(myUsageTracker);

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

  @Override
  @NotNull
  protected File findSdkPath() {
    String localSdkPath = System.getenv("ANDROID_HOME");

    if (localSdkPath != null) {
      File localSdk = new File(localSdkPath);
      if (localSdk.exists()) {
        return localSdk;
      }
    }
    return super.findSdkPath();
  }

  @Override
  protected void updateVersionAndDependencies(@NotNull File file) throws IOException {
    //Update build.gradle in root directory
    updateBuildFile();

    //Update dependencies.gradle
    updateDependenciesFile();

    //Update dependencies in build.gradle of sub-modules
    updateContentsInFile("outissue/cyclus/build.gradle", "dependencies \\{\n", "dependencies {\n" +
                                                                               "compile deps.support.leanback\n" +
                                                                               "compile deps.support.appCompat\n" +
                                                                               "compile deps.external.rxjava\n");
    updateContentsInFile("outissue/embrace/build.gradle", "dependencies \\{\n", "dependencies { compile deps.external.rxjava\n");
    updateContentsInFile("outissue/nutate/build.gradle", "dependencies \\{\n", "dependencies { compile deps.support.mediarouter\n");
    updateContentsInFile("outissue/edictally/build.gradle", "compileOnly deps.apt.autoValueAnnotations", "/* $0 */");

    // Remove butterknife plugin.
    for (String path : ImmutableList
      .of("outissue/carnally", "outissue/airified", "Padraig/follicle", "outissue/Glumaceae", "fratry/sodden", "subvola/zelator",
          "subvola/doored", "subvola/transpire", "subvola/atbash", "subvola/gorgoneum/Chordata", "subvola/gorgoneum/metanilic/agaric",
          "subvola/gorgoneum/teerer/polytonal", "subvola/gorgoneum/teerer/Cuphea", "harvestry/Timbira")) {
      updateContentsInFile(path + "/build.gradle", "apply plugin: 'com.jakewharton.butterknife'", "/* $0 */");
    }
  }

  public void testGradleSyncPerf() throws Exception {
    loadProject("android-studio-gradle-test");
  }

  // Update build.gradle in root directory
  private void updateBuildFile() throws IOException {
    File buildFile = getAbsolutionFilePath("build.gradle");
    String contents = Files.toString(buildFile, Charsets.UTF_8);
    contents = contents.replaceAll("jcenter\\(\\)", AndroidGradleTests.getLocalRepositories());
    contents = contents.replaceAll("classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'",
                                   "classpath 'com.android.tools.build:gradle:" +
                                   BuildEnvironment.getInstance().getGradlePluginVersion() +
                                   "'");
    contents = contents.replaceAll("(classpath 'com.uber:okbuck:[^']+')", "// $0");
    contents = contents.replaceAll("(classpath 'com.jakewharton:butterknife-gradle-plugin:8.4.0')", "// $1");
    contents = contents.replaceAll("(force 'com.android.support:[^:]*):[^']*'", "$1:" + "25.1.0" + "'");
    int pos = contents.indexOf("apply plugin: 'com.uber");
    write(contents.substring(0, pos - 1), buildFile, Charsets.UTF_8);
  }

  // Update dependencies.gradle
  private void updateDependenciesFile() throws IOException {
    File dependenciesFile = getAbsolutionFilePath("dependencies.gradle");
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
                                                        "mediarouter : [\"com.android.support:mediarouter-v7:25.0.1\"," +
                                                        "\"com.android.support:appcompat-v7:\\${versions.supportVersion}\"," +
                                                        "\"com.android.support:palette-v7:\\${versions.supportVersion}\"],\n");
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

  // Returns full path, given relative path to root directory
  @NotNull
  private File getAbsolutionFilePath(@NotNull String relativePath) {
    File projectRoot = virtualToIoFile(getProject().getBaseDir());
    return new File(projectRoot, relativePath);
  }

  // Replace all occurrence of regex in file
  private void updateContentsInFile(@NotNull String relativePath, @NotNull String regex, @NotNull String replaceString) throws IOException {
    File file = getAbsolutionFilePath(relativePath);
    String contents = Files.toString(file, Charsets.UTF_8);
    contents = contents.replaceAll(regex, replaceString);
    write(contents, file, Charsets.UTF_8);
  }
}
