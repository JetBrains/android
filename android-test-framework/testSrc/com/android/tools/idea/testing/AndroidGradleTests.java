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

import javax.annotation.RegEx;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.testutils.TestUtils.getWorkspaceFile;
import static com.google.common.io.Files.write;

public class AndroidGradleTests {
  public static void updateGradleVersions(@NotNull File file) throws IOException {
    updateGradleVersions(file, getLocalRepositories());
  }

  private static void updateGradleVersions(@NotNull File file, @NotNull String localRepositories) throws IOException {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          updateGradleVersions(child, localRepositories);
        }
      }
    }
    else if (file.getPath().endsWith(DOT_GRADLE) && file.isFile()) {
      String contentsOrig = Files.toString(file, Charsets.UTF_8);
      String contents = contentsOrig;

      BuildEnvironment buildEnvironment = BuildEnvironment.getInstance();

      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]",
                                   buildEnvironment.getGradlePluginVersion());
      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle-experimental:(.+)['\"]",
                                   buildEnvironment.getExperimentalPluginVersion());

      contents = replaceRegexGroup(contents, "buildToolsVersion ['\"](.+)['\"]", buildEnvironment.getBuildToolsVersion());
      contents = replaceRegexGroup(contents, "compileSdkVersion ([0-9]+)", buildEnvironment.getCompileSdkVersion());
      contents = replaceRegexGroup(contents, "targetSdkVersion ([0-9]+)", buildEnvironment.getTargetSdkVersion());
      contents = contents.replaceAll("repositories[ ]+\\{", "repositories {\n" + localRepositories);

      if (!contents.equals(contentsOrig)) {
        write(contents, file, Charsets.UTF_8);
      }
    }
  }

  @NotNull
  public static String getLocalRepositories() {
    return getLocalRepository("prebuilts/tools/common/m2/repository") + getLocalRepository("prebuilts/tools/common/offline-m2");
  }

  @NotNull
  protected static String getLocalRepository(@NotNull String dir) {
    String uri = getWorkspaceFile(dir).toURI().toString();
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
