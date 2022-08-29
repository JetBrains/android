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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.AndroidSdk;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.Display;
import com.android.tools.asdriver.tests.FileServer;
import com.android.tools.asdriver.tests.TestFileSystem;
import com.intellij.openapi.util.SystemInfo;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UpdateTest {

  public static final String PRODUCT_CODE = "AI";
  public static final String PRODUCT_PREFIX = PRODUCT_CODE + "-";

  // TODO: form all of these dynamically.
  private static final String FAKE_API_VERSION = "213.7172.25";
  public static final String FAKE_CURRENT_BUILD_NUMBER = "213.7172.25.2113.31337";
  public static final String FAKE_UPDATED_BUILD_NUMBER = "213.7172.25.2113.8473230";

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Our hermetic test environment will not be able to resolve internet URLs, so we have to route
   * those requests to our own {@code FileServer}. This skips downloading anything from https://plugins.jetbrains.com/.
   * This will cause our file server to get requests like "/files/brokenPlugins.json" and "/plugins/list".
   */
  private void setPluginHost(AndroidStudioInstallation install, String fileServerOrigin) throws IOException {
    install.addVmOption("-Didea.plugins.host=" + fileServerOrigin);
  }

  /**
   * Creates the environment to use when running Studio.
   * <p>
   * Inherits the {@code PATH} environment variable from the parent process. We do this so that
   * Python is in our {@code PATH}, otherwise {@link com.intellij.util.Restarter} won't know that
   * we can automatically update and will instead give the user instructions on how to run a shell
   * script to perform the update.
   * <p>
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
   * Creates {@code updates.xml}, which is what Android Studio requests at start-up to figure out
   * if it can be updated.
   */
  public Path createUpdatesXml(Path tempDir) throws IOException {
    Path updatesXml = tempDir.resolve("updates.xml");
    String apiVersion = PRODUCT_PREFIX + FAKE_API_VERSION;
    String fakeUpdatedBuild = PRODUCT_PREFIX + FAKE_UPDATED_BUILD_NUMBER;
    // Note: these are individual String.format calls to make it easier to spot where strings are being replaced
    String xmlContents = String.format("<?xml version=\"1.0\" ?><products>%n") +
                         String.format("<product name=\"Android Studio\">%n") +
                         String.format("<code>AI</code>%n") +
                         String.format(
                           "<channel feedback=\"https://code.google.com/p/android/issues/entry?template=Android+Studio+bug\" id=\"AI-1-release\" majorVersion=\"1\" name=\"Android Studio updates\" status=\"release\" url=\"https://developer.android.com/r/studio-ui/release-updates.html\">%n") +
                         String.format("<build apiVersion=\"%s\" number=\"%s\" version=\"Electric Eel | 2021.3.1 Stable 11\">%n", apiVersion,
                                       fakeUpdatedBuild) +
                         String.format("<message><![CDATA[<html> Fake channel for updating </html>]]></message>%n") +
                         String.format(
                           "<button download=\"true\" name=\"Download\" url=\"https://developer.android.com/r/studio-ui/download-canary.html\"/>%n") +
                         String.format(
                           "<button name=\"Release Notes\" url=\"https://developer.android.com/r/studio-ui/release-updates.html\"/>%n") +
                         String.format("<patch from=\"%s\" size=\"1234\"/> <!-- 2021.3.1.9 -->%n", FAKE_CURRENT_BUILD_NUMBER) +
                         String.format("</build>%n") +
                         String.format("</channel>%n") +
                         String.format("</product>%n") +
                         String.format("</products>%n");

    Files.writeString(updatesXml, xmlContents, StandardCharsets.UTF_8);
    System.out.println("Created " + updatesXml);

    return updatesXml;
  }

  /**
   * Creates files that Android Studio requests from jetbrains.com and dl.google.com. In a hermetic
   * test environment, such internet sites are unreachable.
   * <p>
   * If Android Studio attempts to contact an unresolvable domain while updating, it will get stuck
   * on "Check for updates" until the timeout (20 seconds) is hit. Such a timeout doesn't
   * <i>necessarily</i> cause a test failure, but it can change the UI in unexpected ways (e.g. the
   * update-checker may run in a background task which may spawn an extra dialog when Android
   * Studio is closed).
   * <p>
   * Similarly, if Android Studio attempts to download a file which is not found (i.e. the server
   * returns a 404), tests <i>may</i> still succeed, but the UI and timings may be subtly
   * different.
   * <p>
   * As a result, this function is used to produce files whose sole purpose is to be downloaded and
   * ideally ignored. In other words, the contents of each file only need to pass validation. This
   * property is what makes them "fake" files. If "real" files are needed, they should be set up by
   * the test that requires them.
   */
  private void createFakePluginAndUpdateFiles(Path tempDir, FileServer fileServer) throws IOException {
    Path updateArtifacts = tempDir.resolve("update_artifacts");
    Files.createDirectories(updateArtifacts);

    // Creates addons_list-1.xml through addons_list-5.xml
    for (int i = 1; i <= 5; i++) {
      Path addonsFile = createFakeAddonsFile(updateArtifacts, i);
      fileServer.registerFile("/" + addonsFile.getFileName(), addonsFile);
    }

    Path repoFile22 = createFakeRepositoryFile(updateArtifacts, "repository2-2.xml", "02", "02");
    fileServer.registerFile("/" + repoFile22.getFileName(), repoFile22);

    Path repoFile23 = createFakeRepositoryFile(updateArtifacts, "repository2-3.xml", "02", "03");
    fileServer.registerFile("/" + repoFile23.getFileName(), repoFile23);

    // Note: as long as the "idea.plugins.host" system property is specified, Android Studio will
    // make a request for "/plugins/list" rather than "/files/pluginsXMLIds.json".
    Path pluginList = createFakePluginList(updateArtifacts);
    fileServer.registerFile("/plugins/list/", pluginList);

    Path brokenPlugins = createFakeBrokenPlugins(updateArtifacts);
    fileServer.registerFile("/files/brokenPlugins.json", brokenPlugins);
  }

  /**
   * @see UpdateTest#createFakePluginAndUpdateFiles
   */
  private Path createFakeBrokenPlugins(Path parentDir) throws IOException {
    // All we need is an empty JSON array
    String contents = "[]";

    Path dest = parentDir.resolve("brokenPlugins.json");
    Files.writeString(dest, contents, StandardCharsets.UTF_8);
    return dest;
  }

  /**
   * @see UpdateTest#createFakePluginAndUpdateFiles
   */
  private Path createFakePluginList(Path parentDir) throws IOException {
    String contents = "<?xml version='1.0' encoding='UTF-8'?><plugin-repository></plugin-repository>";

    Path dest = parentDir.resolve("plugin_list.xml");
    Files.writeString(dest, contents, StandardCharsets.UTF_8);
    return dest;
  }

  /**
   * Creates a fake repositoryX-Y.xml file. This file must contain a remote package in order to
   * pass validation (see {@link com.android.repository.impl.manager.RemoteRepoLoaderImpl#parseSource}).
   *
   * @see UpdateTest#createFakePluginAndUpdateFiles
   */
  private Path createFakeRepositoryFile(Path parentDir, String fileName, String majorVersion, String minorVersion) throws IOException {
    String remotePackage = null;
    if (majorVersion.equals("02")) {
      // repository2-3.xml differs slightly from repository2-2.xml.
      String v3SpecificBits = "   <extension-level>1</extension-level>\n" +
                              "   <base-extension>true</base-extension>\n";

      // android-Tiramisu was chosen arbitrarily; the specific platform used here isn't significant
      remotePackage = "<remotePackage path=\"platforms;android-Tiramisu\">\n" +
                      "  <!--Generated from bid:8250781, branch:git_tm-preview2-release-->\n" +
                      "  <type-details xsi:type=\"sdk:platformDetailsType\">\n" +
                      "   <api-level>32</api-level>\n" +
                      "   <codename>Tiramisu</codename>\n" +
                      (minorVersion.equals("02") ? "" : v3SpecificBits) +
                      "   <layoutlib api=\"15\"/>\n" +
                      "  </type-details>\n" +
                      "  <revision>\n" +
                      "   <major>2</major>\n" +
                      "  </revision>\n" +
                      "  <display-name>Android SDK Platform Tiramisu</display-name>\n" +
                      "  <uses-license ref=\"android-sdk-license\"/>\n" +
                      "  <channelRef ref=\"channel-0\"/>\n" +
                      "  <archives>\n" +
                      "   <archive>\n" +
                      "    <!--Built on: Thu Mar  3 20:46:47 2022.-->\n" +
                      "    <complete>\n" +
                      "     <size>67290653</size>\n" +
                      "     <checksum type=\"sha1\">2ac79862a909392d68d8ad503c45809e725d71f6</checksum>\n" +
                      "     <url>platform-Tiramisu_r02.zip</url>\n" +
                      "    </complete>\n" +
                      "   </archive>\n" +
                      "  </archives>\n" +
                      " </remotePackage>\n";
    }

    if (remotePackage == null) {
      throw new IllegalArgumentException(String.format("Major and minor versions not recognized: %s %s", majorVersion, minorVersion));
    }

    String contents =
      "<?xml version=\"1.0\" ?>\n" +
      "<sdk:sdk-repository " +
      String.format("xmlns:common=\"http://schemas.android.com/repository/android/common/%s\" ", majorVersion) +
      String.format("xmlns:generic=\"http://schemas.android.com/repository/android/generic/%s\" ", majorVersion) +
      String.format("xmlns:sdk=\"http://schemas.android.com/sdk/android/repo/repository2/%s\" ", minorVersion) +
      String.format("xmlns:sdk-common=\"http://schemas.android.com/sdk/android/repo/common/%s\" ", minorVersion) +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
      " <license id=\"android-sdk-license\" type=\"text\">\n" +
      " </license>\n" +
      " <channel id=\"channel-0\">stable</channel>\n" +
      " <channel id=\"channel-1\">beta</channel>\n" +
      " <channel id=\"channel-2\">dev</channel>\n" +
      " <channel id=\"channel-3\">canary</channel>\n" +
      remotePackage +
      "</sdk:sdk-repository>\n";

    Path dest = parentDir.resolve(fileName);
    Files.writeString(dest, contents, StandardCharsets.UTF_8);
    return dest;
  }

  /**
   * @see UpdateTest#createFakePluginAndUpdateFiles
   */
  private Path createFakeAddonsFile(Path parentDir, int index) throws IOException {
    String addonsContents =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      String.format("<sdk:sdk-addons-list xmlns:sdk=\"http://schemas.android.com/sdk/android/addons-list/%d\">\n", index) +
      "</sdk:sdk-addons-list>\n";

    Path dest = parentDir.resolve(String.format("addons_list-%d.xml", index));
    Files.writeString(dest, addonsContents, StandardCharsets.UTF_8);
    return dest;
  }

  /**
   * Note: we explicitly do NOT call the "CheckForUpdate" action as part of this test since the
   * platform will already call it, and calling it more than once will produce a race condition
   * where the notification panel with the "Update…" link may close on us before we can interact
   * with it.
   */
  @Test
  public void updateTest() throws Exception {
    TestFileSystem fileSystem = new TestFileSystem(tempFolder.getRoot().toPath());
    AndroidSystem.createRemediationShutdownHook();

    AndroidStudioInstallation install;
    try (Display display = Display.createDefault();
         FileServer fileServer = new FileServer()) {
      fileServer.start();
      install = AndroidStudioInstallation.fromZip(fileSystem);
      // Every time a notification shows up, NotificationsManagerImpl#createActionPanel is called.
      // If this happens while the notification panel is already open, it will be closed and this
      // test will be unable to proceed since it never tries reopening that panel. Thus, we need to
      // ensure that a single notification is produced: the "Update available" notification.
      install.preventProguardNotification();
      install.createFirstRunXml();
      // Prevent an error related to jumplists on Windows.
      install.createGeneralPropertiesXml();
      install.setBuildNumber(PRODUCT_PREFIX + FAKE_CURRENT_BUILD_NUMBER);

      Patcher patcher = new Patcher(fileSystem, install.getStudioDir());
      Path patchFile = patcher.createPatch(FAKE_CURRENT_BUILD_NUMBER, FAKE_UPDATED_BUILD_NUMBER);
      fileServer.registerFile("/" + patchFile.getFileName(), patchFile);

      Path updatesXml = createUpdatesXml(fileSystem.getRoot());
      fileServer.registerFile("/" + updatesXml.getFileName(), updatesXml);

      createFakePluginAndUpdateFiles(fileSystem.getRoot(), fileServer);

      setPluginHost(install, fileServer.getOrigin());
      Map<String, String> env = createEnvironment(fileServer.getOrigin());
      AndroidSdk sdk = new AndroidSdk(TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk()));
      sdk.install(env);

      try (AndroidStudio studio = install.run(display, env)) {
        String version = studio.version();
        assertTrue(version.endsWith(FAKE_CURRENT_BUILD_NUMBER));

        System.out.println("Updating Android Studio");
        // This invokes the "update button" in the bottom right of the "Welcome" window. It may
        // have different icons depending on the state of the platform, but we only look for the
        // "update" icon because the test's setup ensures that the only notification available is
        // the update notification.
        studio.invokeByIcon("ide/notification/ideUpdate.svg");

        // This will activate the update link inside the NotificationActionPanel in the "Welcome"
        // window. The Unicode character in this string is an ellipsis. The string corresponds to
        // IdeBundle.message("updates.notification.update.action").
        studio.invokeComponent("Update\u2026");

        // This button is in a DialogWindowWrapper. The string corresponds to
        // IdeBundle.message("updates.download.and.restart.button").
        studio.invokeComponent("Update and Restart");

        // The "Restart" link is an HTML hyperlink inside a notification. Because of that:
        //
        // - It actually doesn't need to be showing for us to be able to invoke it (and as a
        //   result, we make no effort to show it).
        // - The text we're passing here is its display ID, not the text it renders with.
        studio.invokeComponent("ide.update.suggest.restart");

        // The first Studio process should no longer be running; wait for it to finish running and trigger the update fully.
        studio.waitForProcess();
      }

      // Ensure that updates.xml was requested a single time
      List<URI> updatesRequests = fileServer.getRequestHistoryForPath("/updates.xml");
      assertEquals(1, updatesRequests.size());

      // Ensure that updates.xml was requested with the correct query parameters
      URI uri = updatesRequests.get(0);
      List<NameValuePair> queryParamList = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
      Map<String, String> queryParams = queryParamList.stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
      System.out.println("Query params when requesting updates.xml: " + queryParams);
      // The value of "uid" is random, so just ensure it exists. Note that the "mid" (machine ID)
      // isn't guaranteed to be set.
      assertTrue(queryParams.containsKey("uid"));
      assertFalse(queryParams.containsKey("eap"));
      String osParam = queryParams.get("os");
      if (SystemInfo.isWindows) {
        // This will look like "Windows 10 10.0" or "Windows Server 2019 10.0".
        assertTrue(osParam.contains("Windows"));
      } else if (SystemInfo.isLinux) {
        // This will look like "Linux 5.4.0-1083-gcp" or "Linux 5.17.11-1rodete2-amd64".
        assertTrue(osParam.toLowerCase().contains("linux"));
      } else if (SystemInfo.isMac) {
        // This will look like "Mac OS X 12.5".
        assertTrue(osParam.contains("Mac OS X"));
      }
      String buildParam = queryParams.get("build");
      assertEquals(PRODUCT_PREFIX + FAKE_CURRENT_BUILD_NUMBER, buildParam);

      install.getIdeaLog().waitForMatchingLine(".*run restarter:.*", 120, TimeUnit.SECONDS);
      try (AndroidStudio studio = install.attach()) {
        String version = studio.version();
        assertTrue(version.endsWith(FAKE_UPDATED_BUILD_NUMBER));
      }
    }
  }
}
