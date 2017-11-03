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
package com.android.tools.idea.updater;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestUtils;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings("JUnitTestClassNamingConvention")
@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  SdkUpdaterTestSuite.class
})
public class SdkUpdaterTestSuite {

  public static final String TMP_DIR = System.getProperty("java.io.tmpdir");

  static {
    System.setProperty("idea.home", createTmpDir("tools/idea").toString());
    VfsRootAccess.allowRootAccess("/");

    symbolicLinkInTmpDir("tools/adt/idea/android/annotations");
    symbolicLinkInTmpDir("tools/adt/idea/android/testData");
    symbolicLinkInTmpDir("tools/base/templates");
  }

  private static Path createTmpDir(@NotNull String dirName) {
    Path path = Paths.get(TMP_DIR, dirName);
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return path;
  }

  private static void symbolicLinkInTmpDir(@NotNull String target) {
    Path targetPath = TestUtils.getWorkspaceFile(target).toPath();
    Path linkName = Paths.get(TMP_DIR, target);
    try {
      Files.createDirectories(linkName.getParent());
      Files.createSymbolicLink(linkName, targetPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
