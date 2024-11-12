/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.rendering;

import static org.junit.Assert.fail;

import com.android.sdklib.devices.Device;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.rendering.RenderLogger;
import com.android.tools.rendering.RenderService;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Utils for ASwB's render tests. This class is packaged under "com.android.tools.idea.rendering"
 * because it makes use of package private utility methods in that package.
 */
public class AswbRenderTestUtils {
  public static final String DEFAULT_DEVICE_ID = "Nexus 4";

  /** Path to layoutlib resources under the android studio SDK. */
  public static final String LAYOUTLIB_SRC_PATH = "plugins/android/lib/layoutlib/";

  /**
   * Returns the workspace path to the android studio SDK used by this test which can change
   * depending on the SDK used for testing.
   */
  private static Optional<String> getStudioSdkPath() {
    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    return Stream.of(
            "./third_party/java/jetbrains/plugin_api/android_studio_%s_%s/",
            // Matches path of SDK in github/bazelbuild/intellij.
            "./external/android_studio_%s_%s/android-studio/")
        .map(
            (template) ->
                String.format(template, appInfo.getMajorVersion(), appInfo.getMinorVersion()))
        .filter((path) -> Paths.get(path).toAbsolutePath().toFile().exists())
        .findFirst();
  }

  /**
   * Set up layoutlib resources and initialize the render executor required for rendering layouts.
   */
  public static void beforeRenderTestCase() throws IOException {
    // layoutlib resources are provided by the SDK. Studio only searches through a few pre-defined
    // locations. All of those locations are sub directories of android studio's installation
    // directory and therefore can't be used directly in this test environment.  Therefore we need
    // to symlink the resources folder to a location studio will find.
    Optional<String> externalStudioSdkPath = getStudioSdkPath();
    if (externalStudioSdkPath.isPresent()) {
      Path srcFolder = Paths.get(externalStudioSdkPath.get(), LAYOUTLIB_SRC_PATH).toAbsolutePath();
      Path destFolder = Paths.get(PathManager.getHomePath(), LAYOUTLIB_SRC_PATH);
      Files.createDirectories(destFolder.getParent());
      Files.createSymbolicLink(destFolder, srcFolder);
    }

    // Give the render executor 5 seconds to shutdown.
    RenderService.shutdownRenderExecutor(5);
    RenderService.initializeRenderExecutor();
  }

  /**
   * Clean up layoutlib resources set up in {@link #beforeRenderTestCase()} and shutdown the render
   * executor.
   */
  public static void afterRenderTestCase() throws IOException {
    // Delete the layoutlib resource folder manually linked in #beforeRenderTestCase if exists.
    Files.deleteIfExists(Paths.get(PathManager.getHomePath(), LAYOUTLIB_SRC_PATH));

    RenderLogger.resetFidelityErrorsFilters();
    waitForRenderTaskDisposeToFinish();
  }

  /** Waits for any RenderTask dispose threads to finish */
  public static void waitForRenderTaskDisposeToFinish() {
    // Make sure there is no RenderTask disposing event in the event queue.
    UIUtil.dispatchAllInvocationEvents();
    Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().startsWith("RenderTask dispose"))
        .forEach(
            t -> {
              try {
                t.join(10 * 1000); // 10s
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            });
  }

  public static Configuration getConfiguration(Module module, VirtualFile file) {
    return getConfiguration(module, file, DEFAULT_DEVICE_ID);
  }

  public static Configuration getConfiguration(
      Module module, VirtualFile file, String deviceId) {
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(module);
    Configuration configuration = configurationManager.getConfiguration(file);
    configuration.setDevice(findDeviceById(configurationManager, deviceId), false);

    return configuration;
  }

  private static Device findDeviceById(ConfigurationManager manager, String id) {
    for (Device device : manager.getDevices()) {
      if (device.getId().equals(id)) {
        return device;
      }
    }
    fail("Can't find device " + id);
    throw new IllegalStateException();
  }

  private AswbRenderTestUtils() {}
}
