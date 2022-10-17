/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.testutils;

import static com.android.SdkConstants.FD_PLATFORMS;
import static org.junit.Assume.assumeFalse;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout;

/**
 * Utility methods to deal with loading the test data.
 */
public class TestUtils {
  public static final AndroidLayoutlibDownloaderProxy llDownloader = new AndroidLayoutlibDownloaderProxy();
  public static final AndroidProfilerDownloaderProxy profilerDownloader = new AndroidProfilerDownloaderProxy();

  /**
   * Kotlin version that is used in AGP integration tests. Please note that this version does not
   * have to be the same as the version of kotlinc used to build AGP (in Gradle or Bazel).
   *
   * <p>This version needs to be present in prebuilts for tests to pass (see
   * tools/base/bazel/README.md).
   */
  public static final String KOTLIN_VERSION_FOR_TESTS = getKotlinVersionForTests();

  /**
   * The Android platform version used in the gradle-core and builder unit tests.
   *
   * <p>If changing this value, also update //tools/base/build-system:android_platform_for_tests
   */
  public static final int ANDROID_PLATFORM_FOR_AGP_UNIT_TESTS = 33;

  /**
   * Unix file-mode mask indicating that the file is executable by owner, group, and other.
   *
   * <p>See https://askubuntu.com/a/485001
   */
  public static final int UNIX_EXECUTABLE_MODE = 1 | 1 << 3 | 1 << 6;

  /** Default timeout for the {@link #eventually(Runnable)} check. */
  private static final Duration DEFAULT_EVENTUALLY_TIMEOUT = Duration.ofSeconds(10);

  /** Time to wait between checks to obtain the value of an eventually supplier. */
  private static final long EVENTUALLY_CHECK_CYCLE_TIME_MS = 10;

  @GuardedBy("TestUtils.class")
  private static Path workspaceRoot = null;

  /**
   * Returns the Kotlin version to be used in new project templates and integration tests.
   *
   * If you are looking for the version of the Kotli compiler used during code highlighting
   * in the IDE, use KotlinPluginLayout.getAnalyzerCompilerVersion() instead.
   */
  @NonNull
  public static String getKotlinVersionForTests() {
    return KotlinPluginLayout.getStandaloneCompilerVersion().getArtifactVersion();
  }

  /**
   * Creates a temporary directory that is deleted when the JVM exits.
   *
   * @deprecated Temporary directories and files should be deleted after each test, not kept
   *     around for the lifetime of the JVM. This can be achieved by using
   *     a {@link org.junit.rules.TemporaryFolder} rule, or by calling the
   *     {@link PathUtils#deleteRecursivelyIfExists(Path)} method in {@code tearDown}.
   */
  @Deprecated
  public static Path createTempDirDeletedOnExit() throws IOException {
    Path tempDir = Files.createTempDirectory("");
    PathUtils.addRemovePathHook(tempDir);
    return tempDir;
  }

  /**
   * Returns the root of the entire Android Studio codebase.
   *
   * <p>From this path, you should be able to access any file in the workspace via its full path,
   * e.g.
   *
   * <p>TestUtils.getWorkspaceRoot().resolve("tools/adt/idea/android/testSrc");
   *
   * <p>TestUtils.getWorkspaceRoot().resolve("prebuilts/studio/jdk");
   *
   * <p>If this method is called by code run via IntelliJ / Gradle, it will simply walk its
   * ancestor tree looking for the WORKSPACE file at its root; if called from Bazel, it will
   * simply return the runfiles directory (which should be a mirror of the WORKSPACE root except
   * only populated with explicitly declared dependencies).
   *
   * <p>Instead of calling this directly, prefer calling {@link #resolveWorkspacePath(String)} as
   * it is more resilient to cross-platform testing.
   *
   * @throws IllegalStateException if the current directory of the test is not a subdirectory of
   *     the workspace directory when this method is called. This shouldn't happen if the test is
   *     run by Bazel or run by IntelliJ with default configuration settings (where the working
   *     directory is initialized to the module root).
   */
  @NonNull
  public static synchronized Path getWorkspaceRoot() {
    if (workspaceRoot == null) {
      workspaceRoot = AndroidSdkDownloader.downloadSdk(new BuildDependenciesCommunityRoot(Paths.get(PathManager.getCommunityHomePath())));
    }

    llDownloader.makeSureComponentIsInPlace();
    profilerDownloader.makeSureComponentIsInPlace();

    return workspaceRoot;
  }

  /**
   * Given a relative path to a file or directory from the base of the current workspace, returns
   * the absolute path.
   *
   * <p>For example:
   *
   * <p>TestUtils.resolveWorkspacePath("tools/adt/idea/android/testSrc");
   *
   * <p>TestUtils.resolveWorkspacePath("prebuilts/studio/jdk");
   *
   * <p>This method guarantees the file or directory exists, throwing an exception if not found,
   * so tests can safely use the file immediately after receiving it.
   *
   * <p>In order to have the same method call work on both Windows and non-Windows machines, if
   * the current OS is Windows and the target path is found with a common windows extension on it,
   * then it will automatically be returned, e.g. "/path/to/binary" -> "/path/to/binary.exe".
   *
   * @throws IllegalArgumentException if the path results in a file that's not found.
   */
  @NonNull
  public static Path resolveWorkspacePath(@NonNull String relativePath) {
    String ijRelativeFile = mapRelativePath(relativePath);
    if (!ijRelativeFile.equals(relativePath)) {
      Path path = Paths.get(PathManager.getHomePath(), ijRelativeFile);
      if (!Files.exists(path)) {
        throw new IllegalArgumentException("File \"" + path + "\" not found");
      }
      return path;
    }

    return legacyWorkspaceFile(relativePath);
  }

  // returns path relative to PathManager.getHomePath(). TODO: in idea PathManager.getHomePath() should work as workspace root.
  @NonNull
  private static String mapRelativePath(@NonNull String relativePath) {
    Map<String, String> pathMappings = new HashMap<>();
    Path homePath = Paths.get(PathManager.getHomePath());
    Path communityHomePath = Paths.get(PathManager.getCommunityHomePath());

    pathMappings.put("tools/adt/idea", homePath.relativize(communityHomePath) + "/android");
    pathMappings.put("prebuilts/tools/common/kotlin-plugin/Kotlin", "out/artifacts/KotlinPlugin");

    for (Map.Entry<String, String> entry : pathMappings.entrySet()) {
      String aospPathPrefix = entry.getKey();
      String ijPathPrefix = entry.getValue();
      if (relativePath.startsWith(aospPathPrefix)){
        String ijFile = ijPathPrefix + relativePath.substring(aospPathPrefix.length());
        return ijFile;
      }
    }

    return relativePath;
  }

  @NonNull
  private static Path legacyWorkspaceFile(@NonNull String path) {
    Path f = getWorkspaceRoot().resolve(path);

    if (!Files.exists(f) && OsType.getHostOs() == OsType.WINDOWS) {
      // This file may be a binary with a .exe extension
      f = Paths.get(f.toAbsolutePath().toString() + ".exe");
    }

    if (!Files.exists(f)) {
      throw new IllegalArgumentException("File \"" + path + "\" not found at \"" + getWorkspaceRoot() + "\"");
    }

    return f;
  }

  /**
   * Given a relative path to a file or directory from the base of the current workspace, returns
   * the absolute path.
   *
   * This method don't check if the file actually exists
   */
  @NonNull
  public static Path resolveWorkspacePathUnchecked(@NonNull String relativePath) {
    String ijRelativeFile = mapRelativePath(relativePath);
    if (!ijRelativeFile.equals(relativePath)) {
      return Paths.get(PathManager.getHomePath(), ijRelativeFile);
    }

    return getWorkspaceRoot().resolve(relativePath);
  }

  /** Gets the path to a specific Bazel workspace. */
  @NonNull
  public static Path getWorkspaceRoot(@NonNull String workspaceName) throws IOException {
    throw new UnsupportedOperationException("Multiple workspace roots are not supported");
  }

  /** Returns true if the file exists in the workspace. */
  public static boolean workspaceFileExists(@NonNull String path) {
    return Files.exists(resolveWorkspacePath(path));
  }

  /**
   * Returns the absolute {@link Path} to the {@param bin} from the current workspace, or from
   * bazel-bin if not present.
   */
  public static Path getBinPath(String bin) {
    Path path = TestUtils.resolveWorkspacePathUnchecked(bin);
    if (!Files.exists(path)) {
      // running from IJ
      path = TestUtils.resolveWorkspacePathUnchecked("bazel-bin/" + bin);
    }
    return path;
  }

  /**
   * Returns a directory which tests can output data to. If running through Bazel's test runner,
   * this returns the directory as specified by the TEST_UNDECLARED_OUTPUTS_DIR environment
   * variable. Data written to this directory will be zipped and made available under the
   * WORKSPACE_ROOT/bazel-testlogs/ after the test completes. For non-Bazel runs, this currently
   * returns a tmp directory that is deleted on exit.
   */
  @NonNull
  public static Path getTestOutputDir() throws IOException {
    // If running via bazel, returns the sandboxed test output dir.
    String testOutputDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (testOutputDir != null) {
      return Paths.get(testOutputDir);
    }

    return createTempDirDeletedOnExit();
  }

  /**
   * Returns a file at {@code path} relative to the root for {@link #getLatestAndroidPlatform}.
   *
   * @throws IllegalStateException if the current OS is not supported.
   * @throws IllegalArgumentException if the path results in a file not found.
   */
  @NonNull
  public static Path resolvePlatformPath(@NonNull String path) {
    return resolvePlatformPath(path, TestType.OTHER);
  }

  /**
   * Returns a file at {@code path} relative to the root for {@link #getLatestAndroidPlatform}.
   *
   * @throws IllegalStateException if the current OS is not supported.
   * @throws IllegalArgumentException if the path results in a file not found.
   */
  @NonNull
  public static Path resolvePlatformPath(@NonNull String path, @NonNull TestType testType) {
    String latestAndroidPlatform = getLatestAndroidPlatform(testType);
    Path file = getSdk().resolve(FD_PLATFORMS).resolve(latestAndroidPlatform).resolve(path);
    if (Files.notExists(file)) {
      throw new IllegalArgumentException(
        "File \"" + path + "\" not found in platform " + latestAndroidPlatform);
    }
    return file;
  }

  public static enum TestType {
    AGP,
    OTHER,
  }

  /** Checks if tests were started by Bazel. */
  public static boolean runningFromBazel() {
    return System.getenv().containsKey("TEST_WORKSPACE");
  }

  /** Checks if tests are running with Jdk 11 or above. */
  public static boolean runningWithJdk11Plus(String version) {
    return Integer.parseInt(version.split("\\.")[0]) >= 11;
  }

  /**
   * Returns the SDK directory relative to the workspace.
   *
   * @throws IllegalStateException if the current OS is not supported.
   * @throws IllegalArgumentException if the path results in a file not found.
   */
  @NonNull
  public static String getRelativeSdk() {
    OsType osType = OsType.getHostOs();
    if (osType == OsType.UNKNOWN) {
      throw new IllegalStateException(
        "SDK test not supported on unknown platform: " + OsType.getOsName());
    }

    String hostDir = osType.getFolderName();
    return "prebuilts/studio/sdk/" + hostDir;
  }

  /**
   * Returns the SDK directory.
   *
   * @throws IllegalStateException if the current OS is not supported.
   * @throws IllegalArgumentException if the path results in a file not found.
   * @return a valid Path object pointing at the SDK directory.
   */
  @NonNull
  public static Path getSdk() {
    return resolveWorkspacePath(getRelativeSdk());
  }

  @NonNull
  public static Path getMockJdk() {
    return Paths.get(PathManager.getCommunityHomePath(), "java/mockJDK-11");
  }

  /**
   * Returns the path to checked-in NDK.
   *
   * @see #getSdk()
   */
  @NonNull
  public static Path getNdk() {
    return getSdk().resolve(SdkConstants.FD_NDK);
  }

  /** Returns the prebuilt offline Maven repository used during IDE tests. */
  @NonNull
  public static Path getPrebuiltOfflineMavenRepo() {
    if (runningFromBazel()) {
      // If running with Bazel, then Maven artifacts are unzipped or linked into this
      // directory at runtime using IdeaTestSuiteBase#unzipIntoOfflineMavenRepo or
      // IdeaTestSuiteBase#linkIntoOfflineMavenRepo. Thus, we use a writeable temp
      // directory instead of //prebuilts/tools/common/m2/repository.
      // See b/148081564#comment1 for how this could be simplified in the future.
      Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "offline-maven-repo");
      try {
        Files.createDirectories(dir);
      } catch (IOException e) {
        throw new UncheckedIOException(
          "Failed to create directory for offline maven repository: " + dir, e);
      }
      return dir;
    } else {
      return resolveWorkspacePath("prebuilts/tools/common/m2/repository");
    }
  }

  /**
   * Returns the remote SDK directory.
   *
   * @throws IllegalArgumentException if the path results in a file not found.
   */
  @NonNull
  public static Path getRemoteSdk() {
    return resolveWorkspacePath("prebuilts/studio/sdk/remote/dl.google.com/android/repository");
  }

  /** Returns the checked in AAPT2 binary that is shipped with the gradle plugin. */
  @NonNull
  public static Path getAapt2() {
    OsType osType = OsType.getHostOs();
    if (osType == OsType.UNKNOWN) {
      throw new IllegalStateException(
        "AAPT2 not supported on unknown platform: " + OsType.getOsName());
    }
    String hostDir = osType.getFolderName();
    return resolveWorkspacePath(
      "prebuilts/tools/common/aapt/" + hostDir + "/" + SdkConstants.FN_AAPT2);
  }

  @NonNull
  public static Path getDesugarLibJar() {
    // the default version is the latest version
    return getDesugarLibJarWithVersion("1.1.5");
  }

  /**
   * Returns the path to a file in the local maven repository.
   *
   * @param path the path of the file relative to the maven repository root
   * @throws IllegalArgumentException if the path results in a file that's not found.
   */
  @NonNull
  public static Path getLocalMavenRepoFile(@NonNull String path) {
    if (runningFromBazel()) {
      return resolveWorkspacePath("../maven/repository/" + path);
    } else {
      return resolveWorkspacePath("prebuilts/tools/common/m2/repository/" + path);
    }
  }

  @NonNull
  private static Path getDesugarLibJarWithVersion(@NonNull String version) {
    return getLocalMavenRepoFile(
      "com/android/tools/desugar_jdk_libs/"
      + version
      + "/desugar_jdk_libs-"
      + version
      + ".jar");
  }

  @NonNull
  private static Path getDesugarLibConfigJarWithVersion(@NonNull String version) {
    return getLocalMavenRepoFile(
      "com/android/tools/desugar_jdk_libs_configuration/"
      + version
      + "/desugar_jdk_libs_configuration-"
      + version
      + ".jar");
  }

  @NonNull
  public static String getDesugarLibConfigContent() throws IOException {
    // the default version is the latest version
    return getDesugarLibConfigContentWithVersion("1.1.5");
  }

  @NonNull
  private static String getDesugarLibConfigContentWithVersion(String version) throws IOException {
    StringBuilder stringBuilder = new StringBuilder();

    try (JarFile jarFile = new JarFile(getDesugarLibConfigJarWithVersion(version).toFile())) {
      JarEntry jarEntry = jarFile.getJarEntry("META-INF/desugar/d8/desugar.json");
      try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(jarFile.getInputStream(jarEntry)))) {
        String line = reader.readLine();
        while (line != null) {
          stringBuilder.append(line);
          line = reader.readLine();
        }
      }
    }
    return stringBuilder.toString();
  }

  @NonNull
  public static Path getJava11Jdk() {
    if (System.getenv("JDK_11") != null) {
      Path jdkPath = Paths.get(System.getenv("JDK_11"));
      if (Files.isDirectory(jdkPath)) {
        return jdkPath;
      }
      else {
        Logger.getInstance(TestUtils.class).warn("Ignore env.JDK_11 because it is not a directory: " + jdkPath);
      }
    }

    Path jdk11Path = findInDownloadedJdks("corretto-11.");
    if (jdk11Path != null) return jdk11Path;

    assert Runtime.version().feature() == 11 : "To continue we need to know where JDK11 is. env.JDK_11 didn't work.";
    return Paths.get(SystemProperties.getJavaHome());
  }

  @Nullable
  private static Path findInDownloadedJdks(String prefix) {
    String userHome = System.getProperty("user.home");
    Path jdks = Paths.get(userHome, ".jdks");
    if (Files.isDirectory(jdks)) {
      try (Stream<Path> stream = Files.list(jdks)) {
        Optional<Path> jdk11Path = stream
          .filter(file -> Files.isDirectory(file) && file.getFileName().toString().startsWith(prefix))
          .findAny();

        if (jdk11Path.isPresent()) {
          Logger.getInstance(TestUtils.class).info("Found JDK11 at " + jdk11Path);
          return jdk11Path.get();
        }
      }
      catch (IOException e) {
        Logger.getInstance(TestUtils.class).warn("Cannot list directory " + jdks, e);
      }
    }
    return null;
  }

  @NonNull
  public static String getEmbeddedJdk8Path() {
    if (System.getenv("JDK_18") != null) {
      Path jdkPath = Paths.get(System.getenv("JDK_18"));
      if (Files.isDirectory(jdkPath)) {
        return jdkPath.toString();
      }
      else {
        Logger.getInstance(TestUtils.class).warn("Ignore env.JDK_18 because it is not a directory: " + jdkPath);
      }
    }

    Path jdk8Path = findInDownloadedJdks("corretto-1.8.");
    if (jdk8Path != null) return jdk8Path.toString();

    assert Runtime.version().feature() == 8 : "To continue we need to know where JDK8 is. env.JDK_18 didn't work.";
    return SystemProperties.getJavaHome();
  }

  @NonNull
  public static String getLatestAndroidPlatform() {
    return getLatestAndroidPlatform(TestType.OTHER);
  }

  @NonNull
  public static String getLatestAndroidPlatform(@NonNull TestType testType) {
    if (testType == TestType.AGP) {
      return "android-" + ANDROID_PLATFORM_FOR_AGP_UNIT_TESTS;
    }
    return "android-32";
  }

  /**
   * Sleeps the current thread for enough time to ensure that the local file system had enough
   * time to notice a "tick". This method is usually called in tests when it is necessary to
   * ensure filesystem writes are detected through timestamp modification.
   *
   * @throws InterruptedException waiting interrupted
   * @throws IOException issues creating a temporary file
   */
  public static void waitForFileSystemTick() throws InterruptedException, IOException {
    waitForFileSystemTick(getFreshTimestamp());
  }

  /**
   * Sleeps the current thread for enough time to ensure that the local file system had enough
   * time to notice a "tick". This method is usually called in tests when it is necessary to
   * ensure filesystem writes are detected through timestamp modification.
   *
   * @param currentTimestamp last timestamp read from disk
   * @throws InterruptedException waiting interrupted
   * @throws IOException issues creating a temporary file
   */
  public static void waitForFileSystemTick(long currentTimestamp)
    throws InterruptedException, IOException {
    while (getFreshTimestamp() <= currentTimestamp) {
      Thread.sleep(100);
    }
  }

  private static long getFreshTimestamp() throws IOException {
    Path notUsed = Files.createTempFile(TestUtils.class.getName(), "waitForFileSystemTick");
    try {
      return Files.getLastModifiedTime(notUsed).toMillis();
    } finally {
      Files.delete(notUsed);
    }
  }

  @NonNull
  public static String getDiff(@NonNull String before, @NonNull  String after) {
    return getDiff(before, after, 0);
  }

  @NonNull
  public static String getDiff(@NonNull String before, @NonNull  String after, int windowSize) {
    return getDiff(before.split("\n"), after.split("\n"), windowSize);
  }

  @NonNull
  public static String getDiff(@NonNull String[] before, @NonNull String[] after) {
    return getDiff(before, after, 0);
  }

  public static String getDiff(@NonNull String[] before, @NonNull String[] after,
                               int windowSize) {
    // Based on the LCS section in http://introcs.cs.princeton.edu/java/96optimization/
    StringBuilder sb = new StringBuilder();
    int n = before.length;
    int m = after.length;

    // Compute longest common subsequence of x[i..m] and y[j..n] bottom up
    int[][] lcs = new int[n + 1][m + 1];
    for (int i = n - 1; i >= 0; i--) {
      for (int j = m - 1; j >= 0; j--) {
        if (before[i].equals(after[j])) {
          lcs[i][j] = lcs[i + 1][j + 1] + 1;
        } else {
          lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
        }
      }
    }

    int i = 0;
    int j = 0;
    while ((i < n) && (j < m)) {
      if (before[i].equals(after[j])) {
        i++;
        j++;
      } else {
        sb.append("@@ -");
        sb.append(Integer.toString(i + 1));
        sb.append(" +");
        sb.append(Integer.toString(j + 1));
        sb.append('\n');

        if (windowSize > 0) {
          for (int context = Math.max(0, i - windowSize); context < i; context++) {
            sb.append("  ");
            sb.append(before[context]);
            sb.append("\n");
          }
        }

        while (i < n && j < m && !before[i].equals(after[j])) {
          if (lcs[i + 1][j] >= lcs[i][j + 1]) {
            sb.append('-');
            if (!before[i].trim().isEmpty()) {
              sb.append(' ');
            }
            sb.append(before[i]);
            sb.append('\n');
            i++;
          } else {
            sb.append('+');
            if (!after[j].trim().isEmpty()) {
              sb.append(' ');
            }
            sb.append(after[j]);
            sb.append('\n');
            j++;
          }
        }

        if (windowSize > 0) {
          for (int context = i; context < Math.min(n, i + windowSize); context++) {
            sb.append("  ");
            sb.append(before[context]);
            sb.append("\n");
          }
        }
      }
    }

    if (i < n || j < m) {
      assert i == n || j == m;
      sb.append("@@ -");
      sb.append(Integer.toString(i + 1));
      sb.append(" +");
      sb.append(Integer.toString(j + 1));
      sb.append('\n');
      for (; i < n; i++) {
        sb.append('-');
        if (!before[i].trim().isEmpty()) {
          sb.append(' ');
        }
        sb.append(before[i]);
        sb.append('\n');
      }
      for (; j < m; j++) {
        sb.append('+');
        if (!after[j].trim().isEmpty()) {
          sb.append(' ');
        }
        sb.append(after[j]);
        sb.append('\n');
      }
    }

    return sb.toString();
  }

  /**
   * Asserts that a runnable will eventually not throw an assertion exception. Equivalent to
   * {@link #eventually(Runnable, Duration)}, but using a default timeout
   *
   * @param runnable a description of the failure, if the condition never becomes {@code true}
   */
  public static void eventually(@NonNull Runnable runnable) {
    eventually(runnable, DEFAULT_EVENTUALLY_TIMEOUT);
  }

  /**
   * Asserts that a runnable will eventually not throw {@link AssertionError} before
   * {@code timeoutMs} milliseconds have ellapsed
   *
   * @param runnable a description of the failure, if the condition never becomes {@code true}
   * @param duration the timeout for the predicate to become true
   */
  public static void eventually(@NonNull Runnable runnable, Duration duration) {
    AssertionError lastError = null;

    Instant timeoutTime = Instant.now().plus(duration);
    while (Instant.now().isBefore(timeoutTime)) {
      try {
        runnable.run();
        return;
      } catch (AssertionError e) {
        /*
         * It is OK to throw this. Save for later.
         */
        lastError = e;
      }

      try {
        Thread.sleep(EVENTUALLY_CHECK_CYCLE_TIME_MS);
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    }

    throw new AssertionError(
      "Timed out waiting for runnable not to throw; last error was:",
      lastError);
  }

  /**
   * Launches a new process to execute the class {@code toRun} main() method and blocks until the
   * process exits.
   *
   * @param toRun the class whose main() method will be executed in a new process.
   * @param args the arguments for the class {@code toRun} main() method
   * @throws RuntimeException if any (checked or runtime) exception occurs or the process returns
   *     an exit value other than 0
   */
  public static void launchProcess(@NonNull Class toRun, String... args) {
    List<String> commandAndArgs = new LinkedList<>();
    commandAndArgs.add(FileUtils.join(System.getProperty("java.home"), "bin", "java"));
    commandAndArgs.add("-cp");
    commandAndArgs.add(System.getProperty("java.class.path"));
    commandAndArgs.add(toRun.getName());
    commandAndArgs.addAll(Arrays.asList(args));

    ProcessBuilder processBuilder = new ProcessBuilder(commandAndArgs);
    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    Process process;
    try {
      process = processBuilder.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      process.waitFor();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    if (process.exitValue() != 0) {
      throw new RuntimeException(
        "Process returned non-zero exit value: " + process.exitValue());
    }
  }

  // disable tests when running on Windows in Bazel.
  public static void disableIfOnWindowsWithBazel() {
    assumeFalse(
      (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
      && System.getenv("TEST_TMPDIR") != null);
  }

  private static void archiveFile(Path root, ArchiveOutputStream out, Path path)
    throws IOException {
    ZipArchiveEntry archiveEntry =
      (ZipArchiveEntry)
        out.createArchiveEntry(path.toFile(), root.relativize(path).toString());
    out.putArchiveEntry(archiveEntry);
    if (Files.isSymbolicLink(path)) {
      archiveEntry.setUnixMode(UnixStat.LINK_FLAG | archiveEntry.getUnixMode());
      out.write(
        path.getParent()
          .relativize(Files.readSymbolicLink(path))
          .toString()
          .getBytes());
    } else if (!Files.isDirectory(path)) {
      if (Files.isExecutable(path)) {
        archiveEntry.setUnixMode(archiveEntry.getUnixMode() | UNIX_EXECUTABLE_MODE);
      }
      out.write(Files.readAllBytes(path));
    }
    out.closeArchiveEntry();
  }

  public static void zipDirectory(@NonNull Path root, @NonNull Path zipPath) throws IOException {
    try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(zipPath.toFile())) {
      Files.walk(root)
        .forEach(
          path -> {
            try {
              archiveFile(root, out, path);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }
}
