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
package com.android.tools.idea.lang;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestUtils;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses(AndroidLangTestSuite.class)  // a suite mustn't contain itself
public class AndroidLangTestSuite {
  private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

  static {
    System.setProperty("idea.home", createTmpDir("tools/idea").toString());
    VfsRootAccess.allowRootAccess("/");  // Bazel tests are sandboxed so we disable VfsRoot checks.

    symbolicLinkInTmpDir("tools/adt/idea/android-lang/testData");
    symbolicLinkInTmpDir("tools/idea/java"); // For the mock JDK.
  }

  private static void symbolicLinkInTmpDir(String target) {
    Path targetPath = TestUtils.getWorkspaceFile(target).toPath();
    Path linkName = Paths.get(TMP_DIR, target);
    try {
      Files.createDirectories(linkName.getParent());
      Files.createSymbolicLink(linkName, targetPath);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Path createTmpDir(String p) {
    Path path = Paths.get(TMP_DIR, p);
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return path;
  }
}

