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

import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.Display;
import com.android.tools.asdriver.tests.InvokeComponentRequestBuilder;
import com.android.tools.asdriver.tests.PatchMachinery;
import com.android.tools.asdriver.tests.XvfbServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

  /**
   * The "update button" that this method refers to is the icon in the bottom right of the
   * "Welcome" window. It may have different icons depending on the state of the platform.
   */
  private void invokeUpdateButton(AndroidStudio studio) {
    InvokeComponentRequestBuilder updateButtonBuilder = new InvokeComponentRequestBuilder();
    updateButtonBuilder.addSvgIconMatch(new ArrayList<>(
      Arrays.asList("ide/notification/ideUpdate.svg", "ide/notification/infoEvents.svg", "ide/notification/warningEvents.svg")));
    studio.invokeComponent(updateButtonBuilder);
  }

  private void invokeUpdateFlow(AndroidStudio studio) {
    studio.executeAction("CheckForUpdate");
    invokeUpdateButton(studio);

    // This will activate the update link inside the NotificationActionPanel in the "Welcome"
    // window. The Unicode character in this string is an ellipsis. The string corresponds to
    // IdeBundle.message("updates.notification.update.action").
    studio.invokeComponent("Update\u2026");

    // This button is in a DialogWindowWrapper. The string corresponds to
    // IdeBundle.message("updates.download.and.restart.button").
    studio.invokeComponent("Update and Restart");
    invokeUpdateButton(studio);

    // This link is an HTML hyperlink inside a Notification.
    String notificationDisplayId = "ide.update.suggest.restart";
    studio.invokeComponent(notificationDisplayId);
  }

  @Test
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
        String version = studio.version();
        assertTrue(version.endsWith(PatchMachinery.FAKE_CURRENT_BUILD_NUMBER));

        System.out.println("Updating Android Studio");
        invokeUpdateFlow(studio);

        // The first Studio process should no longer be running; wait for it to finish running and trigger the update fully.
        studio.waitForProcess();
      } finally {
        install.emitLogs();
      }

      install.getIdeaLog().waitForMatchingLine(".*run restarter:.*", 120, TimeUnit.SECONDS);
      try (AndroidStudio studio = install.attach()) {
        String version = studio.version();
        assertTrue(version.endsWith(PatchMachinery.FAKE_UPDATED_BUILD_NUMBER));
      } finally {
        install.emitLogs();
      }
    }
  }
}
