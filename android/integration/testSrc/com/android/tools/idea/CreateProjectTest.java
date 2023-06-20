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

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.FileServer;
import com.android.tools.asdriver.tests.MavenRepo;
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher;
import com.android.tools.asdriver.tests.MemoryUsageReportProcessor;
import org.junit.Rule;
import org.junit.Test;

public class CreateProjectTest {
  @Rule
  public AndroidSystem system = AndroidSystem.standard();

  @Rule
  public MemoryDashboardNameProviderWatcher watcher = new MemoryDashboardNameProviderWatcher();

  @Test
  public void createProjectTest() throws Exception {
    system.installRepo(new MavenRepo("tools/adt/idea/android/integration/createproject_deps.manifest"));

    // Attempting to create a project on a fresh installation of Android Studio will produce an
    // error saying that no SDK has been configured, so we configure it first.
    system.getInstallation().setGlobalSdk(system.getSdk());

    // Set the AGP version so that we don't end up with something like "8.0.0-dev" and also so that
    // we don't need to update this test every time a new AGP version is released.
    //
    // The Gradle version is chosen based on this. AGP 7.4.1 corresponds to Gradle 7.5.0 (see
    // GradleVersionRefactoringProcessor#getCompatibleGradleVersion).
    // AGP version 7.4.1 is chosen because version catalogs requires at least AGP 7.4.0
    system.getInstallation().addVmOption("-Dgradle.ide.agp.version.to.use=7.4.1");

    String distributionPath = "tools/external/gradle/";
    String localDistributionUrl = TestUtils.resolveWorkspacePathUnchecked(distributionPath).toUri().toString();
    system.getInstallation().addVmOption("-Dgradle.ide.local.distribution.url=" + localDistributionUrl);

    // The New Project Wizard attempts to contact dl.google.com to ensure that the latest SDKs and
    // SDK components (e.g. build tools) are installed. If they AREN'T, then a download is
    // automatically invoked, and a modal progress dialog is displayed to the user. Our test is
    // unaware of how to proceed through this modal dialog, so we override SDK_TEST_BASE_URL to
    // point to a local server that will produce 404s on all requests.
    try (FileServer fileServer = new FileServer()) {
      fileServer.start();
      String fileServerOrigin = fileServer.getOrigin();
      String endsInSlash = fileServerOrigin.endsWith("/") ? fileServerOrigin : fileServerOrigin + "/";
      system.setEnv("SDK_TEST_BASE_URL", endsInSlash);

      try (AndroidStudio studio = system.runStudioWithoutProject()) {
        // "New Project" is selected by default, so we have to use the version of the icon that
        // appears to be selected.
        studio.invokeByIcon("welcome/createNewProjectTab.svg");

        // This only causes the item to be selected, so we still have to click "Next" below.
        studio.invokeComponent("Empty Activity");
        studio.invokeComponent("Next");
        studio.invokeComponent("Finish");
        studio.waitForSync();
        studio.waitForIndex();
        studio.executeAction("MakeGradleProject");
        studio.waitForBuild();
        MemoryUsageReportProcessor.Companion.collectMemoryUsageStatistics(studio, system.getInstallation(), watcher, "afterBuild");
      }
    }
  }
}
