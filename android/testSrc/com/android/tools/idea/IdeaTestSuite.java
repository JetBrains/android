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

import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
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
  com.android.tools.idea.IdeaTestSuite.class,  // a suite mustn't contain itself
  com.android.tools.idea.rendering.RenderSecurityManagerTest.class,  // calls System.setSecurityManager
  com.android.tools.idea.testing.TestProjectPathsGeneratorTest.class, // This is for a standalone, test-only application
  com.android.tools.idea.templates.TemplateTest.CoverageChecker.class, // Inner class is used to test TemplateTest covers all templates

  // The following classes had failures when run in Bazel.
  com.android.tools.idea.gradle.project.NonAndroidGradleProjectImportingTestSuite.class,
  com.android.tools.perf.idea.gradle.project.sync.GradleSyncPerfTest.class, // Sync performance test only runs on perf buildbot
  org.jetbrains.android.databinding.DataBindingScopeTest.class,
  org.jetbrains.android.databinding.GeneratedCodeMatchTest.class,

  // Require resources with spaces (HTML File template)
  // https://github.com/bazelbuild/bazel/issues/374
  com.android.tools.idea.actions.annotations.InferSupportAnnotationsTest.class,
  org.jetbrains.android.dom.CreateMissingClassFixTest.class,

  // Empty test in gradle-feature - http://b.android.com/230792
  com.android.tools.idea.editors.manifest.ManifestConflictTest.class,

  // http://b/35788260
  com.android.tools.idea.gradle.project.sync.errors.OldAndroidPluginErrorHandlerTest.class,
})
public class IdeaTestSuite {

  private static final String TMP_DIR = System.getProperty("java.io.tmpdir");
  private static final String HOST_DIR = OsType.getHostOs().getFolderName();

  // Initialize Idea specific environment
  static {
    setProperties();

    setUpOfflineMavenRepos();

    // Bazel tests are sandboxed so we disable VfsRoot checks.
    VfsRootAccess.allowRootAccess("/");

    symbolicLinkInTmpDir("tools/adt/idea/android/annotations");
    symbolicLinkInTmpDir("tools/adt/idea/artwork/resources/device-art-resources");
    symbolicLinkInTmpDir("tools/adt/idea/android/testData");
    symbolicLinkInTmpDir("tools/adt/idea/android/lib");
    symbolicLinkInTmpDir("tools/base/templates");
    symbolicLinkInTmpDir("tools/idea/java");
    symbolicLinkInTmpDir("prebuilts/studio/jdk");
    symbolicLinkInTmpDir("prebuilts/studio/layoutlib");
    symbolicLinkInTmpDir("prebuilts/studio/sdk/" + HOST_DIR + "/platforms/" + TestUtils.getLatestAndroidPlatform());

    provideRealJdkPathForGradle("prebuilts/studio/jdk");

    // Enable Kotlin plugin (see PluginManagerCore.PROPERTY_PLUGIN_PATH).
    System.setProperty("plugin.path", TestUtils.getWorkspaceFile("prebuilts/tools/common/kotlin-plugin/Kotlin").getAbsolutePath());
  }

  /**
   * Gradle cannot handle a JDK set up with symlinks. It gets confused
   * and in two consecutive executions it thinks that we are calling it
   * with two different JDKs. See
   * https://discuss.gradle.org/t/gradle-daemon-different-context/2146/3
   */
  private static void provideRealJdkPathForGradle(String dir) {
    try {
      File jdk = TestUtils.getWorkspaceFile(dir);
      File file = new File(jdk, "BUILD").toPath().toRealPath().toFile();
      System.setProperty("studio.dev.jdk", file.getParentFile().getAbsolutePath());
    }
    catch (IOException e) {
      // Ignore if we cannot resolve symlinks.
    }
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

  private static void setProperties() {
    System.setProperty("idea.home", createTmpDir("tools/idea").toString());
    System.setProperty("gradle.user.home", createTmpDir("home").toString());
    // See AndroidLocation.java for more information on this system property.
    System.setProperty("ANDROID_SDK_HOME", createTmpDir(".android").toString());
    System.setProperty("layoutlib.thread.timeout", "60000");
  }

  private static Path createTmpDir(String p) {
    Path path = Paths.get(TMP_DIR, p);
    try {
      Files.createDirectories(path);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return path;
  }

  private static void setUpOfflineMavenRepos() {
    // Adds embedded Maven repo directory for tests, see EmbeddedDistributionPaths for details.
    symbolicLinkInTmpDir("prebuilts/tools/common/offline-m2");

    // If present, also adds the offline repo we built from the source tree.
    setUpOfflineRepo("tools/base/bazel/offline_repo_repo.zip", "out/studio/repo");

    // Parts of prebuilts/tools/common that we need.
    setUpOfflineRepo("tools/adt/idea/android/test_deps_repo.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/adt/idea/android/android-gradle-1.5.0_repo_repo.zip", "prebuilts/tools/common/m2/repository");
  }

  private static void setUpOfflineRepo(@NotNull String repoZip, @NotNull String outputPath) {
    File offlineRepoZip = TestUtils.getWorkspaceFile(repoZip);
    try {
      InstallerUtil.unzip(
        offlineRepoZip,
        createTmpDir(outputPath).toFile(),
        FileOpUtils.create(),
        offlineRepoZip.length(),
        new FakeProgressIndicator());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @AfterClass
  public static void leakChecker() throws Exception {
    Class<?> leakTestClass = Class.forName("_LastInSuiteTest");
    leakTestClass.getMethod("testProjectLeak").invoke(leakTestClass.newInstance());
  }

  @AfterClass
  public static void killGradleDaemons() {
    DefaultGradleConnector.close();
  }
}
