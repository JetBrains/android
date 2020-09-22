package com.android.tools.idea.util;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PathUtil;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Code inside Android Studio (as opposed to code inside studio-sdk), should use the methods in this class
 * instead of {@link PathManager#getHomePath()} or {@link PluginManagerCore#isRunningFromSources()}.
 */
public class StudioPathManager {

  // When running from sources with bundled SDK, the SDK is at:
  //   $ROOT/tools/idea
  private static final String ROOT_FROM_BUNDLED_SDK = "../../";

  // When running from sources with unbundled SDK, the SDK is at:
  //   $ROOT/prebuilts/studio/intellij-sdk/AI-201.7846.76.42/linux/android-studio
  private static final String ROOT_FROM_UNBUNDLED_SDK = "../../../../../../";

  /**
   * @return true if running from sources, which includes:
   * <ul>
   *   <li>Running Android Studio from IntelliJ with bundled/unbundled SDK.
   *   <li>Running tests from IntelliJ with bundled/unbundled SDK.
   *   <li>Running tests from Bazel with bundled/unbundled SDK.
   * </ul>
   */
  public static boolean isRunningFromSources() {
    if (PluginManagerCore.isRunningFromSources()) {
      // Bundled Android Studio, or IntelliJ test with bundled SDK.
      return true;
    }

    if (isRunningInBazelTest()) {
      // Bazel test, either bundled or unbundled.
      return true;
    }

    //noinspection RedundantIfStatement
    if (isRunningFromStudioSources()) {
      // Unbundled Android Studio or IntelliJ test with unbundled SDK.
      return true;
    }

    // Not running from sources.
    return false;
  }

  /**
   * @return returns the root of the Android repo when running from sources. This method
   * should be called only when {@link #isRunningFromSources()} returns true.
   */
  public static String getSourcesRoot() {
    assert isRunningFromSources();

    if (PluginManagerCore.isRunningFromSources()) {
      return getSourcesRootBundled();
    }

    if (isRunningInBazelTest()) {
      // This can be a Bazel test running with a bundled or unbundled Studio SDK.
      // We can find it out quickly by probing the directory layout.
      if (PathManager.getHomePath().contains("/prebuilts/studio/intellij-sdk/") ||
          PathManager.getHomePath().contains("\\prebuilts\\studio\\intellij-sdk\\")) {
        // Bazel test running on unbundled build.
        return getSourcesRootUnbundled();
      }
      else {
        // Bazel test running on bundled build or as aswb.
        return getSourcesRootBundled();
      }
    }

    // Running from studio sources.
    return getSourcesRootUnbundled();
  }

  /**
   * Returns true if running inside a Bazel test environment.
   */
  private static boolean isRunningInBazelTest() {
    return System.getenv().containsKey("TEST_WORKSPACE");
  }

  /**
   * @return true if running from sources with unbundled SDK.
   */
  private static boolean isRunningFromStudioSources() {
    return PathUtil.toSystemIndependentName(PathManager.getHomePath())
      .contains("/prebuilts/studio/intellij-sdk/");
  }

  /**
   * @return the source root directory, assuming that bundled SDK is being used.
   */
  private static String getSourcesRootBundled() {
    return Paths.get(PathManager.getHomePath(), ROOT_FROM_BUNDLED_SDK).normalize().toString();
  }

  /**
   * @return the source root directory, assuming that unbundled SDK is being used.
   */
  private static String getSourcesRootUnbundled() {
    String relative = ROOT_FROM_UNBUNDLED_SDK;
    if (SystemInfo.isMac) {
      // On Mac, idea home points to the "Contents" directory
      relative += "../";
    }
    return Paths.get(PathManager.getHomePath(), relative).normalize().toString();
  }
}
