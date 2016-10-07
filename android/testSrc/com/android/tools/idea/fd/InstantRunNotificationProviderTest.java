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

import org.jetbrains.android.util.AndroidBundle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InstantRunNotificationProviderTest {
  @Test
  public void firstDeployHasNoNotifications() {
    BuildSelection buildSelection = new BuildSelection(BuildMode.FULL, BuildCause.FIRST_INSTALLATION_TO_DEVICE);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.FULLAPK, "");
    assertNull("No notifications should be shown the first time an APK is installed on the device during a session",
               provider.getNotificationText());
  }

  @Test
  public void cleanBuildOnUserRequest() {
    BuildSelection buildSelection = new BuildSelection(BuildMode.CLEAN, BuildCause.USER_REQUESTED_CLEAN_BUILD);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.FULLAPK, "");
    assertEquals(AndroidBundle.message("instant.run.notification.cleanbuild.on.user.request"), provider.getNotificationText());
  }

  @Test
  public void appNotRunningAndHaveChanges() {
    BuildSelection buildSelection = new BuildSelection(BuildMode.COLD, BuildCause.APP_NOT_RUNNING);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.DEX, "");
    assertEquals(AndroidBundle.message("instant.run.notification.coldswap"), provider.getNotificationText());
  }

  @Test
  public void appNotRunningAndHaveNoChanges() {
    BuildSelection buildSelection = new BuildSelection(BuildMode.COLD, BuildCause.APP_NOT_RUNNING);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.NO_CHANGES, "");
    assertEquals(AndroidBundle.message("instant.run.notification.coldswap.nochanges"), provider.getNotificationText());
  }

  @Test
  public void warmSwapNotification() {
    BuildSelection buildSelection = new BuildSelection(BuildMode.HOT, BuildCause.INCREMENTAL_BUILD);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.WARMSWAP, "");
    assertEquals(AndroidBundle.message("instant.run.notification.warmswap"), provider.getNotificationText());
  }

  @Test
  public void hotswapNotification() {
    BuildSelection buildSelection = new BuildSelection(BuildMode.HOT, BuildCause.INCREMENTAL_BUILD);
    InstantRunNotificationProvider provider = new InstantRunNotificationProvider(buildSelection, DeployType.HOTSWAP, "");
    assertEquals(AndroidBundle.message("instant.run.notification.hotswap", ""), provider.getNotificationText());
  }
}
