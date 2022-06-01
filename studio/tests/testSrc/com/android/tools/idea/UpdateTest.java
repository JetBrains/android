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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

public class UpdateTest {

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
    // TODO(b/234069200): change how we detect whether Studio is running and whether it's restarted
    //  to instead reuse the agent we inject.
    if (AndroidStudio.isAnyInstanceOfStudioRunning()) {
      System.out.println("The update test requires that no instances of Android Studio are running since it tests the restart flow in " +
                         "addition to the update flow.");
      AndroidStudio.terminateAllStudioInstances();
    }

    try (Display display = new XvfbServer();
         AndroidStudioInstallation install = new AndroidStudioInstallation()) {
      install.createFirstRunXml();
      install.copySdk(TestUtils.getLatestAndroidPlatform());
      install.setBuildNumber(PatchMachinery.PRODUCT_PREFIX + PatchMachinery.FAKE_CURRENT_BUILD_NUMBER);

      PatchMachinery patchMachinery = new PatchMachinery(install);
      patchMachinery.setupPatch();
      patchMachinery.createFakePluginAndUpdateFiles();

      setPluginHost(install, patchMachinery.getFileServerOrigin());
      Map<String, String> env = createEnvironment(patchMachinery.getFileServerOrigin());

      try (AndroidStudio studio = install.run(display, env)) {
        display.debugTakeScreenshot("before");

        System.out.println("Updating Android Studio");
        boolean success = studio.updateStudio();
        assertTrue("updateStudio failed", success);
        // The first Studio process should no longer be running.
        System.out.println("Waiting for the original instance of Android Studio to have closed");
        studio.waitForProcess(20, TimeUnit.SECONDS);

        // Ensure it restarted on its own.
        System.out.println("Waiting for Android Studio to restart automatically");
        AndroidStudio.waitForRestart(30000);
        patchMachinery.ensureIdeaPropertiesWereModified();

        // Terminate the restarted instance since we're not attached to it.
        AndroidStudio.terminateAllStudioInstances();
      } catch (Throwable t) {
        throw t;
      } finally {
        install.emitLogs();
      }

      System.out.println("Trying to start the updated Android Studio");
      try (AndroidStudio studio = install.run(display)) {
        System.out.println("The new instance of Android Studio successfully started");
      } catch (Throwable t) {
        throw t;
      } finally {
        install.emitLogs();
      }
    }
  }
}
