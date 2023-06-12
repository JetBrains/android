/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.annotations.Nullable;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;


public class StartLiveUpdateMonitoringTask implements LaunchTask {
  private static final String ID = "LIVE_UPDATE_MONITORING";

  @Nullable private final Callable<?> myStartLiveUpdate;

  public StartLiveUpdateMonitoringTask(@Nullable Callable<?> startLiveUpdate) {
    myStartLiveUpdate = startLiveUpdate;
  }

  @Override
  public @NotNull String getDescription() {
    // This task only start LL under the right conditions so lets not
    // lets not mention anything about LL on the status because
    // users might not expect it in non-compose related projects.
    return "";
  }

  @Override
  public int getDuration() {
    return 1;
  }

  @Override
  public void run(@NotNull LaunchContext launchContext) {
    if (myStartLiveUpdate != null) {
      try {
        myStartLiveUpdate.call();
      }
      catch (Exception e) {
        NotificationGroupManager.getInstance().getNotificationGroup("Deploy")
          .createNotification("Error starting live edit.\n" + e, NotificationType.WARNING);
        // Monitoring should always start successfully start.
      }
    }
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }
}
