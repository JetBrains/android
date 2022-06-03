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
package com.android.tools.asdriver.tests;

import static com.android.tools.asdriver.tests.AndroidStudioInstallation.getBinPath;
import static org.apache.commons.io.file.PathUtils.copyDirectory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Tracks everything to do with producing, serving, and verifying patches.
 *
 * TODO(b/234144947): refactor this class into multiple classes.
 */
public class PatchMachinery implements AutoCloseable {
  public static final String PRODUCT_CODE = "AI";
  public static final String PRODUCT_PREFIX = PRODUCT_CODE + "-";

  // TODO: form all of these dynamically.
  private static final String FAKE_API_VERSION = "213.7172.25";
  public static final String FAKE_CURRENT_BUILD_NUMBER = "213.7172.25.2113.31337";
  public static final String FAKE_UPDATED_BUILD_NUMBER = "213.7172.25.2113.8473230";

  AndroidStudioInstallation install;

  Path tempDir;

  /**
   * A temporary working directory specifically for {@code PatchMachinery} inside {@code e2eTempDir}.
   */
  Path modifiedStudioDir;
  private FileServer fileServer;

  public PatchMachinery(Path tempDir, AndroidStudioInstallation install) throws IOException {
    this.install = install;
    this.tempDir = tempDir;
    modifiedStudioDir = this.tempDir.resolve("patch_machinery");

    startFileServer();
  }

  public String getFileServerOrigin() {
    return fileServer == null ? null : fileServer.getOrigin();
  }

  private void startFileServer() throws IOException {
    fileServer = new FileServer();
    fileServer.start();
  }

  /**
   * Copies the entire Android Studio installation, modifies a file, creates a patch out of the
   * modification, then registers the patch with the {@code FileServer}.
   */
  public void setupPatch(Path tempDir) throws IOException {
    System.out.println("Creating patch machinery in " + modifiedStudioDir);
    Files.createDirectories(modifiedStudioDir);

    long startTime = System.currentTimeMillis();
    copyDirectory(install.getStudioDir(), modifiedStudioDir);
    long elapsedTime = System.currentTimeMillis() - startTime;
    System.out.println("Copying took " + elapsedTime + "ms");
    AndroidStudioInstallation installation = AndroidStudioInstallation.fromDir(tempDir, modifiedStudioDir);
    installation.setBuildNumber(FAKE_UPDATED_BUILD_NUMBER);

    runPatcher();

    Path updatesXml = createUpdatesXml();

    fileServer.registerFile("/updates.xml", updatesXml);
  }

  /**
   * Runs the patcher to produce a patch file from two input directories.
   */
  private void runPatcher() {
    try {
      Path updaterBin = getBinPath("tools/adt/idea/studio/updater");
      Path patchDir = this.tempDir.resolve("patch");
      Files.createDirectories(patchDir);
      System.out.println("Creating the patch in " + patchDir);

      // TODO: On macOS, "--root=Contents" should be passed in.
      Path oldDir = install.getStudioDir();
      Path newDir = modifiedStudioDir;

      // TODO: on macOS, the platform would either be "mac" or "mac_arm". On Windows, it's "win".
      String patchName = String.format("AI-%s-%s-patch-%s.jar", FAKE_CURRENT_BUILD_NUMBER, FAKE_UPDATED_BUILD_NUMBER, "unix");
      Path patchFile = patchDir.resolve(patchName);
      ProcessBuilder pb = new ProcessBuilder(
        updaterBin.toString(),
        "create",
        "old",
        "new",
        oldDir.toString(),
        newDir.toString(),
        patchFile.toString(),
        "--zip_as_binary",
        "--strict"
      );
      pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectError(ProcessBuilder.Redirect.INHERIT);

      long startTime = System.currentTimeMillis();
      Process p = pb.start();
      p.waitFor(90, TimeUnit.SECONDS);
      long elapsedTime = System.currentTimeMillis() - startTime;
      System.out.println("Creating the patch took " + elapsedTime + "ms");

      fileServer.registerFile("/" + patchName, patchFile);
      if (p.exitValue() != 0) {
        throw new RuntimeException("The patcher exited with code " + p.exitValue());
      }
    }
    catch (IOException | InterruptedException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates {@code updates.xml}, which is what Android Studio requests at start-up to figure out
   * if it can be updated.
   */
  private Path createUpdatesXml() throws IOException {
    Path updatesXml = tempDir.resolve("updates.xml");
    StringBuilder sb = new StringBuilder();
    String apiVersion = PRODUCT_PREFIX + FAKE_API_VERSION;
    String fakeUpdatedBuild = PRODUCT_PREFIX + FAKE_UPDATED_BUILD_NUMBER;
    sb.append("<?xml version=\"1.0\" ?><products>\n");
    sb.append("<product name=\"Android Studio\">\n");
    sb.append("<code>AI</code>\n");
    sb.append("<channel feedback=\"https://code.google.com/p/android/issues/entry?template=Android+Studio+bug\" id=\"AI-1-eap\" majorVersion=\"1\" name=\"Android Studio updates\" status=\"eap\" url=\"https://developer.android.com/r/studio-ui/release-updates.html\">\n");
    sb.append(String.format("<build apiVersion=\"%s\" number=\"%s\" version=\"Dolphin | 2021.3.1 Canary 9\">\n", apiVersion, fakeUpdatedBuild));
    sb.append("<message><![CDATA[<html> Fake channel for updating </html>]]></message>\n");
    sb.append("<button download=\"true\" name=\"Download\" url=\"https://developer.android.com/r/studio-ui/download-canary.html\"/>\n");
    sb.append("<button name=\"Release Notes\" url=\"https://developer.android.com/r/studio-ui/release-updates.html\"/>\n");
    sb.append(String.format("<patch from=\"%s\" size=\"1234\"/> <!-- 2021.3.1.9 -->\n", FAKE_CURRENT_BUILD_NUMBER));
    sb.append("</build>\n");
    sb.append("</channel>\n");
    sb.append("</product>\n");
    sb.append("</products>\n");

    Files.writeString(updatesXml, sb.toString(), StandardCharsets.UTF_8);
    System.out.println("Created " + updatesXml);

    return updatesXml;
  }

  @Override
  public void close() throws Exception {
    if (fileServer != null) {
      fileServer.stop(3);
      fileServer = null;
    }
  }

  /**
   * Creates files that Android Studio requests from jetbrains.com and dl.google.com. In a hermetic
   * test environment, such Internet sites are unreachable.
   *
   * If Android Studio attempts to contact an unresolvable domain while updating, it will get stuck
   * on "Check for updates" until the timeout (20 seconds) is hit. Such a timeout doesn't
   * <i>necessarily</i> cause a test failure, but it can change the UI in unexpected ways (e.g. the
   * update-checker may run in a background task which may spawn an extra dialog when Android
   * Studio is closed).
   *
   * Similarly, if Android Studio attempts to download a file which is not found (i.e. the server
   * returns a 404), tests <i>may</i> still succeed, but the UI and timings may be subtly
   * different.
   *
   * As a result, this function is needed to produce files whose sole purpose is to be downloaded
   * and ideally ignored. In other words, the contents of each file only need to pass validation.
   * This property is what makes them "fake" files. If "real" files are needed, they should be set
   * up by the test that requires them.
   */
  public void createFakePluginAndUpdateFiles() throws IOException {
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
   * @see PatchMachinery#createFakePluginAndUpdateFiles
   */
  private Path createFakeBrokenPlugins(Path parentDir) throws IOException {
    // All we need is an empty JSON array
    String contents = "[]";

    Path dest = parentDir.resolve("brokenPlugins.json");
    Files.writeString(dest, contents, StandardCharsets.UTF_8);
    return dest;
  }

  /**
   * @see PatchMachinery#createFakePluginAndUpdateFiles
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
   * @see PatchMachinery#createFakePluginAndUpdateFiles
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
   * @see PatchMachinery#createFakePluginAndUpdateFiles
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
}
