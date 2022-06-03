/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static junit.framework.Assert.assertTrue;

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.Display;
import com.android.tools.asdriver.tests.PatchMachinery;
import com.android.tools.asdriver.tests.XvfbServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UpdateTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();
  /**
   * Our hermetic test environment will not be able to resolve Internet URLs, so we have to route
   * those requests to our own {@code FileServer}. This skips downloading anything from https://plugins.jetbrains.com/.
   * This will cause our file server to get requests like "/files/brokenPlugins.json" and "/plugins/list".
   */
  private void setPluginHost(AndroidStudioInstallation install, String fileServerOrigin) throws IOException {
    install.addVmOption("-Didea.plugins.host=" + fileServerOrigin);
  }

  /**
   * Creates the environment to use when running Studio.
   *
   * Inherits the {@code PATH} environment variable from the parent process. We do this so that
   * Python is in our {@code PATH}, otherwise {@link com.intellij.util.Restarter} won't know that
   * we can automatically update and will instead give the user instructions on how to run a shell
   * script to perform the update.
   *
   * Also sets the variables needed to point to the local server.
   */
  private Map<String, String> createEnvironment(String fileServerOrigin) {
    HashMap<String, String> env = new HashMap<>();
    String path = System.getenv("PATH");
    if (path == null) {
      throw new IllegalArgumentException("No PATH environment variable found. When running through IDEA, edit your run configuration, " +
                                         "click the box next to \"Environment variables\", then enable \"Include system environment " +
                                         "variables\".");
    }
    env.put("PATH", path);

    env.put("AS_UPDATE_URL", fileServerOrigin);
    // The URL we provide as SDK_TEST_BASE_URL has to end in a slash.
    String endsInSlash = fileServerOrigin.endsWith("/") ? fileServerOrigin : fileServerOrigin + "/";
    // This skips downloading anything from https://dl.google.com. This will cause our file server
    // to get requests like "/addons_list-5.xml" and "/repository2-2.xml".
    env.put("SDK_TEST_BASE_URL", endsInSlash);
    return env;
  }

  @Test
  @Ignore("b/234170016")
  public void updateTest() throws Exception {
    Path tempDir = tempFolder.newFolder("update-test").toPath();

    try (Display display = new XvfbServer()) {
      AndroidStudioInstallation install = AndroidStudioInstallation.fromZip(tempDir);
      install.createFirstRunXml();
      install.copySdk(TestUtils.getLatestAndroidPlatform());
      install.setBuildNumber(PatchMachinery.PRODUCT_PREFIX + PatchMachinery.FAKE_CURRENT_BUILD_NUMBER);

      PatchMachinery patchMachinery = new PatchMachinery(tempDir, install);
      patchMachinery.setupPatch(tempDir);
      patchMachinery.createFakePluginAndUpdateFiles();

      setPluginHost(install, patchMachinery.getFileServerOrigin());
      Map<String, String> env = createEnvironment(patchMachinery.getFileServerOrigin());

      try (AndroidStudio studio = install.run(display, env)) {
        display.debugTakeScreenshot("before");

        String version = studio.version();
        assertTrue(version.endsWith(PatchMachinery.FAKE_CURRENT_BUILD_NUMBER));

        System.out.println("Updating Android Studio");
        boolean success = studio.updateStudio();
        assertTrue("updateStudio failed", success);
        // The first Studio process should no longer be running.
      } finally {
        install.emitLogs();
      }

      install.getIdeaLog().waitForMatchingLine(".*---- IDE SHUTDOWN ----.*", 20, TimeUnit.SECONDS);
      try (AndroidStudio studio = install.attach()) {
        String version = studio.version();
        assertTrue(version.endsWith(PatchMachinery.FAKE_UPDATED_BUILD_NUMBER));
        studio.kill(0);
      } finally {
        install.emitLogs();
      }
    }
  }
}
