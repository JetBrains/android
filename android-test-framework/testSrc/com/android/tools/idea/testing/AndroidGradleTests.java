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
package com.android.tools.idea.testing;

import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.RegEx;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.EXT_GRADLE_KTS;
import static com.android.testutils.TestUtils.getKotlinVersionForTests;
import static com.android.testutils.TestUtils.getWorkspaceFile;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.util.io.FileUtil.notNullize;

public class AndroidGradleTests {
  private static final Pattern REPOSITORIES_PATTERN = Pattern.compile("repositories[ ]+\\{");
  private static final Pattern GOOGLE_REPOSITORY_PATTERN = Pattern.compile("google\\(\\)");
  private static final Pattern JCENTER_REPOSITORY_PATTERN = Pattern.compile("jcenter\\(\\)");

  public static void updateGradleVersions(@NotNull File folderRootPath, @NotNull String gradlePluginVersion)
    throws IOException {
    doUpdateGradleVersionsAndRepositories(folderRootPath, null, gradlePluginVersion);
  }

  public static void updateGradleVersions(@NotNull File folderRootPath) throws IOException {
    doUpdateGradleVersionsAndRepositories(folderRootPath, null, null);
  }

  public static void updateGradleVersionsAndRepositories(@NotNull File path,
                                                         @NotNull String repositories,
                                                         @Nullable String gradlePluginVersion)
    throws IOException {
    doUpdateGradleVersionsAndRepositories(path, repositories, gradlePluginVersion);
  }

  private static void doUpdateGradleVersionsAndRepositories(@NotNull File path,
                                                            @Nullable String localRepositories,
                                                            @Nullable String gradlePluginVersion)
    throws IOException {
    if (path.isDirectory()) {
      for (File child : notNullize(path.listFiles())) {
        doUpdateGradleVersionsAndRepositories(child, localRepositories, gradlePluginVersion);
      }
    }
    else if (path.getPath().endsWith(DOT_GRADLE) && path.isFile()) {
      String contentsOrig = Files.toString(path, Charsets.UTF_8);
      String contents = contentsOrig;
      if (localRepositories == null) {
        localRepositories = getLocalRepositoriesForGroovy();
      }

      BuildEnvironment buildEnvironment = BuildEnvironment.getInstance();

      String pluginVersion = gradlePluginVersion != null ? gradlePluginVersion : buildEnvironment.getGradlePluginVersion();
      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]",
                                   pluginVersion);

      contents = replaceRegexGroup(contents, "ext.kotlin_version ?= ?['\"](.+)['\"]", getKotlinVersionForTests());

      // App compat version needs to match compile SDK
      String appCompatMainVersion = BuildEnvironment.getInstance().getCompileSdkVersion();
      contents =  replaceRegexGroup(contents, "com.android.support:appcompat-v7:(\\+)",  appCompatMainVersion + ".+");

      contents = updateBuildToolsVersion(contents);
      contents = updateCompileSdkVersion(contents);
      contents = updateTargetSdkVersion(contents);
      contents = updateMinSdkVersion(contents);
      contents = updateLocalRepositories(contents, localRepositories);

      if (!contents.equals(contentsOrig)) {
        write(contents, path, Charsets.UTF_8);
      }
    }
    else if (path.getPath().endsWith(EXT_GRADLE_KTS) && path.isFile()) {
      String contentsOrig = Files.toString(path, Charsets.UTF_8);
      String contents = contentsOrig;
      if (localRepositories == null) {
        localRepositories = getLocalRepositoriesForKotlin();
      }

      BuildEnvironment buildEnvironment = BuildEnvironment.getInstance();

      String pluginVersion = gradlePluginVersion != null ? gradlePluginVersion : buildEnvironment.getGradlePluginVersion();
      contents = replaceRegexGroup(contents, "classpath\\(['\"]com.android.tools.build:gradle:(.+)['\"]",
                                   pluginVersion);
      contents = replaceRegexGroup(contents, "\\(\"com.android.application\"\\) version \"(.+)\"", pluginVersion);
      contents = replaceRegexGroup(contents, "\\(\"com.android.library\"\\) version \"(.+)\"", pluginVersion);
      contents = replaceRegexGroup(contents, "buildToolsVersion\\(\"(.+)\"\\)", buildEnvironment.getBuildToolsVersion());
      contents = replaceRegexGroup(contents, "compileSdkVersion\\((.+)\\)", buildEnvironment.getCompileSdkVersion());
      contents = replaceRegexGroup(contents, "targetSdkVersion\\((.+)\\)", buildEnvironment.getTargetSdkVersion());

      contents = updateLocalRepositories(contents, localRepositories);

      if (!contents.equals(contentsOrig)) {
        write(contents, path, Charsets.UTF_8);
      }
    }
  }

  @NotNull
  public static String updateBuildToolsVersion(@NotNull String contents) {
    return replaceRegexGroup(contents, "buildToolsVersion ['\"](.+)['\"]", BuildEnvironment.getInstance().getBuildToolsVersion());
  }

  @NotNull
  public static String updateCompileSdkVersion(@NotNull String contents) {
    return replaceRegexGroup(contents, "compileSdkVersion ([0-9]+)", BuildEnvironment.getInstance().getCompileSdkVersion());
  }

  @NotNull
  public static String updateTargetSdkVersion(@NotNull String contents) {
    return replaceRegexGroup(contents, "targetSdkVersion ([0-9]+)", BuildEnvironment.getInstance().getTargetSdkVersion());
  }

  @NotNull
  public static String updateMinSdkVersion(@NotNull String contents) {
    String regex = "minSdkVersion ([0-9]+)";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(contents);
    String minSdkVersion = BuildEnvironment.getInstance().getMinSdkVersion();
    if (matcher.find()) {
      try {
        if (Integer.parseInt(matcher.group(1)) < Integer.parseInt(minSdkVersion)) {
          contents = contents.substring(0, matcher.start(1)) + minSdkVersion + contents.substring(matcher.end(1));
        }
      }
      catch (NumberFormatException ignore) {
      }
    }
    return contents;
  }

  @NotNull
  public static String updateLocalRepositories(@NotNull String contents, @NotNull String localRepositories) {
    String newContents = REPOSITORIES_PATTERN.matcher(contents).replaceAll("repositories {\n" + localRepositories);
    newContents = GOOGLE_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");
    newContents = JCENTER_REPOSITORY_PATTERN.matcher(newContents).replaceAll("");
    return newContents;
  }

  @NotNull
  public static String getLocalRepositoriesForGroovy() {
    return StringUtil.join(getLocalRepositoryDirectories(),
                           file -> "maven {url \"" + file.toURI().toString() + "\"}", "\n");
  }

  @NotNull
  public static String getLocalRepositoriesForKotlin() {
    return StringUtil.join(getLocalRepositoryDirectories(),
                           file -> "maven {setUrl(\"" + file.toURI().toString() + "\")}", "\n");
  }

  @NotNull
  public static Collection<File> getLocalRepositoryDirectories() {
    List<File> repositories = new ArrayList<>();
    String prebuiltsRepo = "prebuilts/tools/common/m2/repository";
    String publishLocalRepo = "out/repo";
    if (TestUtils.runningFromBazel()) {
      // Based on EmbeddedDistributionPaths#findAndroidStudioLocalMavenRepoPaths:
      File tmp = new File(PathManager.getHomePath()).getParentFile().getParentFile();
      File file = new File(tmp, prebuiltsRepo);
      if (file.exists()) {
        repositories.add(file);
      }
      else {
        repositories.add(getWorkspaceFile(prebuiltsRepo));
      }
      // publish local should already be available inside prebuilts
    }
    else if (System.getProperty("idea.gui.test.running.on.release") != null) {
      repositories.add(new File(PathManager.getHomePath(), "gradle"));
    }
    else {
      repositories.add(getWorkspaceFile(prebuiltsRepo));
      repositories.add(getWorkspaceFile(publishLocalRepo));
    }
    return repositories;
  }


  /**
   * Take a regex pattern with a single group in it and replace the contents of that group with a
   * new value.
   * <p>
   * For example, the pattern "Version: (.+)" with value "Test" would take the input string
   * "Version: Production" and change it to "Version: Test"
   * <p>
   * The reason such a special-case pattern substitution utility method exists is this class is
   * responsible for loading read-only gradle test files and copying them over into a mutable
   * version for tests to load. When doing so, it updates obsolete values (like old android
   * platforms) to more current versions. This lets tests continue to run whenever we update our
   * tools to the latest versions, without having to go back and change a bunch of broken tests
   * each time.
   * <p>
   * If a regex is passed in with more than one group, later groups will be ignored; and if no
   * groups are present, this will throw an exception. It is up to the caller to ensure that the
   * regex is well formed and only includes a single group.
   *
   * @return The {@code contents} string, modified by the replacement {@code value}, (unless no
   * {@code regex} match was found).
   */
  @NotNull
  public static String replaceRegexGroup(String contents, @RegEx String regex, String value) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(contents);
    if (matcher.find()) {
      contents = contents.substring(0, matcher.start(1)) + value + contents.substring(matcher.end(1));
    }
    return contents;
  }

  /**
   * Creates a gradle wrapper for use in tests under the {@code projectRoot}.
   * @throws IOException
   */
  public static void createGradleWrapper(@NotNull File projectRoot, @NotNull String gradleVersion) throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(projectRoot);
    File path = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionFile(gradleVersion);
    assertAbout(file()).that(path).named("Gradle distribution path").isFile();
    wrapper.updateDistributionUrl(path);
  }

  /**
   * Finds the AndroidFacet to be used by the test.
   */
  @Nullable
  public static AndroidFacet findAndroidFacetForTests(Module[] modules, @Nullable String chosenModuleName) {
    AndroidFacet testAndroidFacet = null;
    // if module name is specified, find it
    if (chosenModuleName != null) {
      for (Module module : modules) {
        if (chosenModuleName.equals(module.getName())) {
          testAndroidFacet = AndroidFacet.getInstance(module);
          break;
        }
      }
    }

    if (testAndroidFacet == null) {
      // then try and find a non-lib facet
      for (Module module : modules) {
        AndroidFacet androidFacet = AndroidFacet.getInstance(module);
        if (androidFacet != null && androidFacet.getConfiguration().isAppProject()) {
          testAndroidFacet = androidFacet;
          break;
        }
      }
    }

    // then try and find ANY android facet
    if (testAndroidFacet == null) {
      for (Module module : modules) {
        testAndroidFacet = AndroidFacet.getInstance(module);
        if (testAndroidFacet != null) {
          break;
        }
      }
    }
    return testAndroidFacet;
  }
}
