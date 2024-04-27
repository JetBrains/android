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
package com.android.tools.idea.actions.license;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class LicensesLocatorTest {
  @Test
  public void linuxOrWindowsLayout() throws Exception {
    Path testRoot = Paths.get(AndroidTestBase.getTestDataPath(), "licenseLocator");
    Path ideHome = testRoot.resolve("linux");

    LicensesLocator locator = new LicensesLocator(ideHome, false);
    List<String> expectedPaths = Arrays.asList(
      "LICENSE.txt",
      "NOTICE.txt",
      "license/ant_license.txt",
      "plugins/android/lib/licenses/antlr4-runtime-4.5.3.jar-NOTICE",
      "plugins/android/lib/licenses/asm-5.0.3-NOTICE"
    );

    assertThat(relativize(locator.getLicenseFiles(), ideHome)).containsAllIn(expectedPaths);
  }

  @Test
  public void macLayout() throws Exception {
    Path testRoot = Paths.get(AndroidTestBase.getTestDataPath(), "licenseLocator");
    Path ideHome = testRoot.resolve("mac/Contents");

    LicensesLocator locator = new LicensesLocator(ideHome, true);
    List<String> expectedPaths = Arrays.asList(
      "Resources/LICENSE.txt",
      "Resources/NOTICE.txt",
      "license/ant_license.txt",
      "plugins/android/lib/licenses/antlr4-runtime-4.5.3.jar-NOTICE",
      "plugins/android/lib/licenses/asm-5.0.3-NOTICE"
    );

    assertThat(relativize(locator.getLicenseFiles(), ideHome)).containsAllIn(expectedPaths);
  }

  @NotNull
  private static List<String> relativize(@NotNull List<Path> files, @NotNull Path home) {
    return files.stream()
      .map(p -> home.relativize(p).toString())
      .map(s -> s.replace('\\', '/'))
      .collect(Collectors.toList());
  }
}