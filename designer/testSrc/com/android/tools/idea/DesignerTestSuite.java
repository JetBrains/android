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
import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManagerTest;
import com.android.tools.idea.uibuilder.model.NlModelTest;
import com.android.tools.idea.uibuilder.property.editors.support.StyleFilterTest;
import com.android.tools.idea.uibuilder.structure.NlComponentTreeTest;
import com.android.tools.idea.uibuilder.surface.NlDesignSurfaceTest;
import com.android.tools.idea.uibuilder.surface.InteractionManagerTest;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  DesignerTestSuite.class,
  NlDesignSurfaceTest.class, // flaky in bazel
  NlComponentTreeTest.class,
  InteractionManagerTest.class,

  // Failing after IJ 2017.1 merge
  NlModelTest.class,
  NlUsageTrackerManagerTest.class,
})
public class DesignerTestSuite {

  private static final String TMP_DIR = System.getProperty("java.io.tmpdir");
  private static final String HOST_DIR = OsType.getHostOs().getFolderName();

  static {
    System.setProperty("idea.home", createTmpDir("tools/idea").toString());
    System.setProperty("layoutlib.thread.timeout", "60000");
    VfsRootAccess.allowRootAccess("/");
    symbolicLinkInTmpDir("prebuilts/studio/layoutlib");
    symbolicLinkInTmpDir("tools/adt/idea/android/annotations");
    symbolicLinkInTmpDir("tools/adt/idea/android/lib/androidWidgets");
    symbolicLinkInTmpDir("tools/adt/idea/android/testData");
    symbolicLinkInTmpDir("tools/adt/idea/designer/testData");
    symbolicLinkInTmpDir("tools/base/templates");
    symbolicLinkInTmpDir("tools/idea/java");
    symbolicLinkInTmpDir("prebuilts/studio/sdk/" + HOST_DIR + "/platforms/" + TestUtils.getLatestAndroidPlatform());

    provideRealJdkPathForGradle("prebuilts/studio/jdk");
  }


  /**
   * Gradle cannot handle a JDK set up with symlinks. It gets confused
   * and in two consecutive executions it thinks that we are calling it
   * with two different JDKs. See
   * https://discuss.gradle.org/t/gradle-daemon-different-context/2146/3
   */
  private static void provideRealJdkPathForGradle(@NotNull String dir) {
    try {
      File jdk = TestUtils.getWorkspaceFile(dir);
      File file = new File(jdk, "BUILD").toPath().toRealPath().toFile();
      System.setProperty("studio.dev.jdk", file.getParentFile().getAbsolutePath());
    }
    catch (IOException e) {
      // Ignore if we cannot resolve symlinks.
    }
  }

  @NotNull
  private static Path createTmpDir(@NotNull String p) {
    Path path = Paths.get(TMP_DIR, p);
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

  @AfterClass
  public static void leakChecker() throws Exception {
    Class<?> leakTestClass = Class.forName("_LastInSuiteTest");
    leakTestClass.getMethod("testProjectLeak").invoke(leakTestClass.newInstance());
  }
}
