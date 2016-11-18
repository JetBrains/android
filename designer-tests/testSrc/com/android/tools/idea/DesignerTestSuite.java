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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestUtils;
import com.android.tools.idea.uibuilder.property.editors.support.StyleFilterTest;
import com.android.tools.idea.uibuilder.structure.NlComponentTreeTest;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceTest;
import com.android.tools.idea.uibuilder.surface.InteractionManagerTest;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import org.junit.AfterClass;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  DesignerTestSuite.class,
  DesignSurfaceTest.class, // flaky in bazel
  StyleFilterTest.class,
  NlComponentTreeTest.class,
  InteractionManagerTest.class
})
public class DesignerTestSuite {

  private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

  static {
    System.setProperty("idea.home", createTmpDir("tools/idea").toString());
    System.setProperty("layoutlib.thread.timeout", "60000");
    symbolicLinkInTmpDir("tools/adt/idea/android/annotations");
    VfsRootAccess.allowRootAccess("/");
    symbolicLinkInTmpDir("tools/idea/java/jdkAnnotations");
    symbolicLinkInTmpDir("tools/base/templates");
    symbolicLinkInTmpDir("tools/adt/idea/android/device-art-resources");
    symbolicLinkInTmpDir("tools/adt/idea/android/testData");
    symbolicLinkInTmpDir("tools/adt/idea/designer-tests/testData");
    symbolicLinkInTmpDir("tools/adt/idea/android/lib");
    symbolicLinkInTmpDir("prebuilts/studio/jdk");
    symbolicLinkInTmpDir("prebuilts/studio/layoutlib");
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

  private static void symbolicLinkInTmpDir(String target) {
    Path targetPath = TestUtils.getWorkspaceFile(target).toPath();
    Path linkName = Paths.get(TMP_DIR, target);
    try {
      Files.createDirectories(linkName.getParent());
      Files.createSymbolicLink(linkName, targetPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @AfterClass
  public static void leakChecker() throws Exception {
    Class<?> leakTestClass = Class.forName("_LastInSuiteTest");
    leakTestClass.getMethod("testProjectLeak").invoke(leakTestClass.newInstance());
  }
}
