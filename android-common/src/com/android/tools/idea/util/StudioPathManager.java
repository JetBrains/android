package com.android.tools.idea.util;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.PathManager;
import java.nio.file.Paths;

/**
 * Code inside Android Studio (as opposed to code inside studio-sdk), should use the methods in this class
 * instead of {@link PathManager#getHomePath()} or {@link PluginManagerCore#isRunningFromSources()}.
 */
public class StudioPathManager {
  /**
   * The name of the property that contains the relative path from {@link PathManager#getHomePath()} to repo root.
   */
  @SuppressWarnings("FieldCanBeLocal")
  private static final String STUDIO_SOURCE_ROOT = "studio.source.root";

  /**
   * @return true if Android Studio is currently running from sources (works for both bundled and unbundled builds), as opposed to
   * packaged release build.
   */
  public static boolean isRunningFromSources() {
    return PluginManagerCore.isRunningFromSources() || isUnBundledDevBuild() || isRunningInBazelTest();
  }

  /**
   * @return returns the root of the Android repo when Android Studio is running from sources (works for both
   * bundled or unbundled builds).
   */
  public static String getSourcesRoot() {
    assert isRunningFromSources();
    if (isUnBundledDevBuild()) {
      String studioSourceRoot = System.getProperty(STUDIO_SOURCE_ROOT);
      return Paths.get(PathManager.getHomePath(), studioSourceRoot).normalize().toString();
    }

    // Bundled dev build, or bazel test.
    //     PathManager.getHomePath(): $SRC/tools/idea
    return Paths.get(PathManager.getHomePath(), "../..").normalize().toString();
  }

  /**
   * @return true if running Android Studio from sources and this is an unbundled build.
   */
  private static boolean isUnBundledDevBuild() {
    return System.getProperties().containsKey(STUDIO_SOURCE_ROOT);
  }

  /**
   * Returns true if Android Studio is running inside a Bazel test environment.
   */
  private static boolean isRunningInBazelTest() {
    return System.getenv().containsKey("TEST_WORKSPACE");
  }
}
