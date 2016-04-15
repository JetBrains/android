/*
 * Copyright (C) 2015 The Android Open Source Project
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

public class InstantRunSettings {
  /**
   * Returns whether instant run is enabled in the given project.
   * Note: Even if instant run is enabled for the project, instant run related information should not be accessed
   * unless {@link InstantRunGradleUtils#getIrSupportStatus} returns true.
   */
  public static boolean isInstantRunEnabled() {
    InstantRunConfiguration configuration = InstantRunConfiguration.getInstance();
    return configuration.INSTANT_RUN;
  }

  /** Is showing toasts enabled in the given project */
  public static boolean isShowToastEnabled() {
    InstantRunConfiguration configuration = InstantRunConfiguration.getInstance();
    return configuration.SHOW_TOAST;
  }

  /** Assuming instant run is enabled, does code patching require an activity restart in the given project? */
  public static boolean isRestartActivity() {
    InstantRunConfiguration configuration = InstantRunConfiguration.getInstance();
    return configuration.RESTART_ACTIVITY;
  }

  public static boolean isShowNotificationsEnabled() {
    InstantRunConfiguration configuration = InstantRunConfiguration.getInstance();
    return configuration.SHOW_IR_STATUS_NOTIFICATIONS;
  }

  public static void setShowStatusNotifications(boolean en) {
    InstantRunConfiguration configuration = InstantRunConfiguration.getInstance();
    configuration.SHOW_IR_STATUS_NOTIFICATIONS = en;
  }
}
