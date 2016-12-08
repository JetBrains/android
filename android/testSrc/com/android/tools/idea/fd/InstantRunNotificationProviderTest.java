/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.google.wireless.android.sdk.stats.InstantRunStatus;
import org.jetbrains.android.util.AndroidBundle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InstantRunNotificationProviderTest {
  @Test
  public void firstDeployHasNoNotifications() {
    BuildSelection buildSelection = new BuildSelection(BuildCause.FIRST_INSTALLATION_TO_DEVICE, false);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.FULLAPK, "");
    assertNull("No notifications should be shown the first time an APK is installed on the device during a session",
               provider.getNotificationText());
  }

  @Test
  public void userRequestedRunHasNoNotifications() {
    BuildSelection buildSelection = new BuildSelection(BuildCause.USER_REQUESTED_COLDSWAP, false);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.SPLITAPK, "");
    assertNull("No notifications should be shown when the user presses Run",
               provider.getNotificationText());
  }

  @Test
  public void cleanBuildOnUserRequest() {
    BuildSelection buildSelection = new BuildSelection(BuildCause.USER_REQUESTED_CLEAN_BUILD, false);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.FULLAPK, "");
    assertEquals(AndroidBundle.message("instant.run.notification.cleanbuild.on.user.request"), provider.getNotificationText());
  }

  @Test
  public void appNotRunningAndHaveChanges() {
    BuildSelection buildSelection = new BuildSelection(BuildCause.APP_NOT_RUNNING, false);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.DEX, "");
    assertEquals(AndroidBundle.message("instant.run.notification.coldswap"), provider.getNotificationText());
  }

  @Test
  public void appNotRunningAndHaveNoChanges() {
    BuildSelection buildSelection = new BuildSelection(BuildCause.APP_NOT_RUNNING, false);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.NO_CHANGES, "");
    assertEquals(AndroidBundle.message("instant.run.notification.coldswap.nochanges"), provider.getNotificationText());
  }

  @Test
  public void warmSwapNotification() {
    BuildSelection buildSelection = new BuildSelection(BuildCause.INCREMENTAL_BUILD, false);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.WARMSWAP, "");
    assertEquals(AndroidBundle.message("instant.run.notification.warmswap"), provider.getNotificationText());
  }

  @Test
  public void hotswapNotification() {
    BuildSelection buildSelection = new BuildSelection(BuildCause.INCREMENTAL_BUILD, false);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.HOTSWAP, "");
    assertEquals(AndroidBundle.message("instant.run.notification.hotswap", ""), provider.getNotificationText());
  }

  @Test
  public void multiProcessOnApi19() {
    BuildSelection buildSelection = new BuildSelection(BuildCause.APP_USES_MULTIPLE_PROCESSES, false);

    // if we generated an apk, then we shouldn't talk about multi process
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.FULLAPK, "");
    assertEquals("Instant Run re-installed and restarted the app.", provider.getNotificationText());

    // but if we generated cold swap patches, then we should specify that we did so because of multi process
    provider = new InstantRunNotificationProvider(buildSelection, DeployType.DEX, "");
    assertEquals(AndroidBundle.message("instant.run.notification.coldswap.multiprocess"), provider.getNotificationText());
  }

  @Test
  public void detailsOnFullApk() {
    // tests that build cause and verifier status are reported when full apk builds are required.
    BuildSelection buildSelection = new BuildSelection(BuildCause.INCREMENTAL_BUILD, false);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.FULLAPK,
            InstantRunStatus.VerifierStatus.JAVA_RESOURCES_CHANGED.toString());
    assertEquals("Instant Run re-installed and restarted the app. Java Resources Changed.", provider.getNotificationText());

    buildSelection = new BuildSelection(BuildCause.NO_DEVICE, false);
    provider = new InstantRunNotificationProvider(buildSelection, DeployType.FULLAPK, "");
    assertEquals("Instant Run re-installed and restarted the app. BuildCause: NO_DEVICE, BuildMode: FULL.", provider.getNotificationText());

  }
}
