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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.RegEx;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.testutils.TestUtils.getWorkspaceFile;
import static com.google.common.io.Files.write;
import static com.intellij.openapi.util.io.FileUtil.notNullize;

public class AndroidGradleTests {
  public static void updateGradleVersions(@NotNull File folderRootPath, @NotNull String gradlePluginVersion) throws IOException {
    doUpdateGradleVersions(folderRootPath, getLocalRepositories(), gradlePluginVersion);
  }

  public static void updateGradleVersions(@NotNull File folderRootPath) throws IOException {
    doUpdateGradleVersions(folderRootPath, getLocalRepositories(), null);
  }

  private static void doUpdateGradleVersions(@NotNull File path, @NotNull String localRepositories, @Nullable String gradlePluginVersion)
    throws IOException {
    if (path.isDirectory()) {
      for (File child : notNullize(path.listFiles())) {
        doUpdateGradleVersions(child, localRepositories, gradlePluginVersion);
      }
    }
    else if (path.getPath().endsWith(DOT_GRADLE) && path.isFile()) {
      String contentsOrig = Files.toString(path, Charsets.UTF_8);
      String contents = contentsOrig;

      BuildEnvironment buildEnvironment = BuildEnvironment.getInstance();

      String pluginVersion = gradlePluginVersion != null ? gradlePluginVersion : buildEnvironment.getGradlePluginVersion();
      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]",
                                   pluginVersion);
      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle-experimental:(.+)['\"]",
                                   buildEnvironment.getExperimentalPluginVersion());

      contents = updateBuildToolsVersion(contents);
      contents = updateCompileSdkVersion(contents);
      contents = updateTargetSdkVersion(contents);
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
  public static String updateLocalRepositories(@NotNull String contents, @NotNull String localRepositories) {
    return contents.replaceAll("repositories[ ]+\\{", "repositories {\n" + localRepositories);
  }

  @NotNull
  public static String getLocalRepositories() {
    String path = "prebuilts/tools/common/m2/repository";

    File prebuiltsRepoDir = getWorkspaceFile(path);
    String uri = prebuiltsRepoDir.toURI().toString();
    return "maven { url \"" + uri + "\" }\n";
  }


  /**
   * Take a regex pattern with a single group in it and replace the contents of that group with a
   * new value.
   *
   * For example, the pattern "Version: (.+)" with value "Test" would take the input string
   * "Version: Production" and change it to "Version: Test"
   *
   * The reason such a special-case pattern substitution utility method exists is this class is
   * responsible for loading read-only gradle test files and copying them over into a mutable
   * version for tests to load. When doing so, it updates obsolete values (like old android
   * platforms) to more current versions. This lets tests continue to run whenever we update our
   * tools to the latest versions, without having to go back and change a bunch of broken tests
   * each time.
   *
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
}
