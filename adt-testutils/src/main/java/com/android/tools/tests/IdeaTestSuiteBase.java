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
package com.android.tools.tests;

import static com.android.testutils.TestUtils.getWorkspaceRoot;

import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.BazelRunfilesManifestProcessor;
import com.android.testutils.TestUtils;
import com.android.testutils.diff.UnifiedDiff;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;


public class IdeaTestSuiteBase {
  protected static final String TMP_DIR = System.getProperty("java.io.tmpdir");

  static {
    try {
      String[] roots = Arrays.stream(File.listRoots()).map(file -> file.getPath()).toArray(String[]::new);
      VfsRootAccess.allowRootAccess(roots);  // Bazel tests are sandboxed so we disable VfsRoot checks.
      BazelRunfilesManifestProcessor.setUpRunfiles();
      setProperties();
      setupKotlinPlugin();
    } catch(Throwable e) {
      // See b/143359533 for why we are handling errors here
      System.err.println("ERROR: Error initializing test suite, tests will likely fail following this error");
      e.printStackTrace();
    }
  }

  private static void setProperties() {
    System.setProperty("idea.home.path", TestUtils.getWorkspaceFile("tools/idea").getPath());
    System.setProperty("idea.system.path", createTmpDir("idea/system").toString());
    System.setProperty("idea.config.path", createTmpDir("idea/config").toString());
    System.setProperty("gradle.user.home", createTmpDir("home").toString());

    // Set roots for java.util.prefs API.
    System.setProperty("java.util.prefs.userRoot", createTmpDir("userRoot").toString());
    System.setProperty("java.util.prefs.systemRoot", createTmpDir("systemRoot").toString());

    System.setProperty("local.gradle.distribution.path", new File(getWorkspaceRoot(), "tools/external/gradle/").getAbsolutePath());
    // See AndroidLocation.java for more information on this system property.
    System.setProperty("ANDROID_SDK_HOME", createTmpDir(".android").toString());
    System.setProperty("layoutlib.thread.timeout", "60000");
    // When running tests from the IDE, IntelliJ allows plugin descriptors to be anywhere if a plugin.xml is found in a directory.
    // On bazel we pack each directory in a jar, so we have to tell IJ explicitely that we are still "in directory mode"
    System.setProperty("resolve.descriptors.in.resources", "true");

    setRealJdkPathForGradle();
  }

  private static void setupKotlinPlugin() {
    // Platform major version is needed to match the Kotlin plugin's compatibility range
    symlinkToIdeaHome("tools/idea/build.txt");
    // Run Kotlin in-process for easier control over its JVM args.
    System.setProperty("kotlin.compiler.execution.strategy", "in-process");
    // As a side-effect, the following line initializes an initial application. Some tests create
    // their own temporary mock application and then dispose it. However, the ApplicationManager API
    // doesn't fallback to an older application if one was never set, which leaves other tests that
    // call ApplicationManager.getApplication() unexpectedly accessing a disposed application - leading
    // to exceptions if the tests happen to be called in a bad order.
    IdeaTestApplication.getInstance();
  }

  public static Path createTmpDir(String p) {
    Path path = Paths.get(TMP_DIR, p);
    try {
      Files.createDirectories(path);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return path;
  }

  /**
   * Gradle cannot handle a JDK set up with symlinks. It gets confused
   * and in two consecutive executions it thinks that we are calling it
   * with two different JDKs. See
   * https://discuss.gradle.org/t/gradle-daemon-different-context/2146/3
   */
  private static void setRealJdkPathForGradle() {
    try {
      File jdk = new File(getWorkspaceRoot(), "prebuilts/studio/jdk");
      if (jdk.exists()) {
        File file = new File(jdk, "BUILD").toPath().toRealPath().toFile();
        System.setProperty("studio.dev.jdk", file.getParentFile().getAbsolutePath());
      }
    }
    catch (IOException e) {
      // Ignore if we cannot resolve symlinks.
    }
  }

  /**
   * An idea test is run in a temp writable directory. Idea home
   * is set to $TMP/tools/idea. This method creates symlinks from
   * the readonly runfiles to the home directory tree. These
   * directories must first exist as test data for the test.
   */
  protected static void symlinkToIdeaHome(String... targets) {
    // TODO: Remove all calls to this method, which now does nothing.
  }

  /**
   * An idea test is run in a temp writable directory. Idea home
   * is set to $TMP/tools/idea. This method creates symlinks from
   * the readonly runfiles to the home directory tree. If a directory
   * does not exist it will be ignored.
   */
  protected static void optSymlinkToIdeaHome(String... targets) {
    // TODO: Remove all calls to this method, which now does nothing.
  }

  protected static void symlinkToIdeaHome(boolean ignoreMissing, String... targets) {
    // TODO: Remove all calls to this method, which now does nothing.
  }

  /**
   * Sets up a project with content of a zip file, optionally applying a collection of git diff files to the unzipped project source code.
   */
  protected static void setUpSourceZip(@NotNull String sourceZip, @NotNull String outputPath, DiffSpec... diffSpecs) {
    File sourceZipFile = getWorkspaceFileAndEnsureExistence(sourceZip);
    File outDir = new File(getWorkspaceRoot(), outputPath);
    if (!outDir.isDirectory() && !outDir.mkdirs()) {
      throw new RuntimeException("Failed to create output directory: " + outDir);
    }
    unzip(sourceZipFile, outDir);
    for (DiffSpec diffSpec : diffSpecs) {
      try {
        new UnifiedDiff(Paths.get(diffSpec.relativeDiffPath)).apply(
          outDir,
          // plus 1 since the UnifiedDiff implementation artificially includes the
          // diff prefix "a/" and "b/" when counting path segments.
          diffSpec.diffDistance + 1);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Simple value wrapper describing a git diff file.
   */
  protected static class DiffSpec {

    /**
     * The relative path of the diff file from the workspace root.
     */
    public final String relativeDiffPath;

    /**
     * The distance from the source directory and the git root when the diff file
     * was generated. For example, for diffs in benchmark projects, the source code is usually located
     * at `$git-root/project_name.123/src`, hence the diff distance is 2.
     */
    public final int diffDistance;

    public DiffSpec(String relativeDiffPath, int diffDistance) {
      this.relativeDiffPath = relativeDiffPath;
      this.diffDistance = diffDistance;
    }
  }

  protected static void setUpOfflineRepo(@NotNull String repoZip, @NotNull String outputPath) {
    // TODO: Remove the `outputPath` parameter, which is no longer used.
    File offlineRepoZip = getWorkspaceFileAndEnsureExistence(repoZip);
    File outDir = TestUtils.getPrebuiltOfflineMavenRepo();
    unzip(offlineRepoZip, outDir);
  }

  @NotNull
  private static File getWorkspaceFileAndEnsureExistence(@NotNull String relativePath) {
    File file = new File(getWorkspaceRoot(), relativePath);
    if (!file.exists()) {
      throw new IllegalArgumentException(relativePath + " does not exist");
    }
    return file;
  }

  private static void unzip(File offlineRepoZip, File outDir) {
    try {
      InstallerUtil.unzip(
        offlineRepoZip,
        outDir,
        FileOpUtils.create(),
        offlineRepoZip.length(),
        new FakeProgressIndicator());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
