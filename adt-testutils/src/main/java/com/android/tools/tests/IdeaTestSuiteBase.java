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

import static com.android.testutils.TestUtils.resolveWorkspacePath;

import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.RepoLinker;
import com.android.testutils.TestUtils;
import com.android.testutils.diff.UnifiedDiff;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;

public class IdeaTestSuiteBase {
  protected static final String TMP_DIR = System.getProperty("java.io.tmpdir");

  // Note: the leak checker can be disabled in an individual test suite by setting leakChecker.enabled = false.
  @ClassRule public static final LeakCheckerRule leakChecker = new LeakCheckerRule();

  static {
    try {
      System.setProperty("NO_FS_ROOTS_ACCESS_CHECK", "true"); // Bazel tests are sandboxed so we disable VfsRoot checks.
      setProperties();
      setupKotlinPlugin();
    } catch(Throwable e) {
      // See b/143359533 for why we are handling errors here
      System.err.println("ERROR: Error initializing test suite, tests will likely fail following this error");
      e.printStackTrace();
    }
  }

  private static void setProperties() throws IOException {
    System.setProperty("idea.system.path", createTmpDir("idea/system").toString());
    System.setProperty("idea.config.path", createTmpDir("idea/config").toString());
    System.setProperty("idea.force.use.core.classloader", "true");
    System.setProperty("idea.log.path", TestUtils.getTestOutputDir().toString());
    System.setProperty("idea.log.config.file", resolveWorkspacePath("tools/adt/idea/adt-testutils/test-log.xml").toString());
    System.setProperty("gradle.user.home", createTmpDir(".gradle").toString());
    System.setProperty("user.home", TMP_DIR);

    // Run in headless mode by default. This property is set by the IntelliJ test framework too,
    // but we want to set it sooner before any test initializers have run.
    if (System.getProperty("java.awt.headless") == null) {
      System.setProperty("java.awt.headless", "true");
    }

    // Set roots for java.util.prefs API.
    System.setProperty("java.util.prefs.userRoot", createTmpDir("userRoot").toString());
    System.setProperty("java.util.prefs.systemRoot", createTmpDir("systemRoot").toString());

    // See AndroidLocation.java for more information on this system property.
    System.setProperty("ANDROID_PREFS_ROOT", createTmpDir(".android").toString());
    System.setProperty("layoutlib.thread.timeout", "60000");
    // When running tests from the IDE, IntelliJ allows plugin descriptors to be anywhere if a plugin.xml is found in a directory.
    // On bazel we pack each directory in a jar, so we have to tell IJ explicitely that we are still "in directory mode"
    System.setProperty("resolve.descriptors.in.resources", "true");

    // Configure JNA and other native libs.
    Map<String,String>  requiredJvmArgs = readJvmArgsProperties(TestUtils.getBinPath("tools/adt/idea/studio/required_jvm_args.txt"));
    System.setProperty("jna.noclasspath", "true");
    System.setProperty("jna.nosys", "true");
    System.setProperty("jna.boot.library.path",
                       resolveWorkspacePath(requiredJvmArgs.get("jna.boot.library.path")).toString());
    System.setProperty("pty4j.preferred.native.folder",
                       resolveWorkspacePath(requiredJvmArgs.get("pty4j.preferred.native.folder")).toString());

    // TODO(b/213385827): Fix Kotlin script classpath calculation during tests
    System.setProperty("kotlin.script.classpath", "");
  }

  private static Map<String, String> readJvmArgsProperties(Path path) throws IOException {
    Map<String, String> result = new HashMap<>();
    for (String line : Files.readAllLines(path)) {
      int eqIndex = line.indexOf('=');
      if (line.startsWith("-D") && eqIndex > 0) {
        String key = line.substring(2, eqIndex);
        String value = line.substring(eqIndex + 1);
        result.put(key, value);
      }
    }
    return result;
  }

  private static void setupKotlinPlugin() {
    // Run Kotlin in-process for easier control over its JVM args.
    System.setProperty("kotlin.compiler.execution.strategy", "in-process");
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
   * Sets up a project with content of a zip file, optionally applying a collection of git diff files to the unzipped project source code.
   */
  protected static void setUpSourceZip(@NotNull String sourceZip, @NotNull String outputPath, DiffSpec... diffSpecs) {
    File sourceZipFile = getWorkspaceFileAndEnsureExistence(sourceZip);
    File outDir = TestUtils.getWorkspaceRoot().resolve(outputPath).toFile();
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

  protected static void unzipIntoOfflineMavenRepo(@NotNull String repoZip) {
    File offlineRepoZip = getWorkspaceFileAndEnsureExistence(repoZip);
    File outDir = TestUtils.getPrebuiltOfflineMavenRepo().toFile();
    System.out.printf("Unzipping offline repo %s to %s%n", offlineRepoZip, outDir);
    unzip(offlineRepoZip, outDir);
  }

  protected static void linkIntoOfflineMavenRepo(@NotNull String repoManifest) {
    Path offlineRepoManifest = getWorkspaceFileAndEnsureExistence(repoManifest).toPath();
    Path outDir = TestUtils.getPrebuiltOfflineMavenRepo();
    System.out.printf("Linking offline repo %s to %s%n", offlineRepoManifest, outDir);

    try {
      RepoLinker linker = new RepoLinker();
      List<String> artifacts = Files.readAllLines(offlineRepoManifest);
      linker.link(outDir, artifacts);
    } catch (Exception e) {
      // linkIntoOfflineMavenRepo is only called from Java static blocks in test suites, which can
      // only throw RuntimeExceptions, so convert all exceptions into RuntimeExceptions.
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static File getWorkspaceFileAndEnsureExistence(@NotNull String relativePath) {
    Path file = TestUtils.getWorkspaceRoot().resolve(relativePath);
    if (!Files.exists(file)) {
      throw new IllegalArgumentException(relativePath + " does not exist");
    }
    return file.toFile();
  }

  private static void unzip(File offlineRepoZip, File outDir) {
    try {
      InstallerUtil.unzip(
        offlineRepoZip.toPath(),
        outDir.toPath(),
        offlineRepoZip.length(),
        new FakeProgressIndicator());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
