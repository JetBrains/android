/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.events;

import com.intellij.build.events.Failure;
import com.intellij.notification.Notification;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;

public class AndroidSyncFailure implements Failure {
  @NotNull private final Notification myNotification;
  @NotNull private final NotificationData myNotificationData;

  public AndroidSyncFailure(@NotNull NotificationData notificationData) {
    myNotificationData = notificationData;
    myNotification = new Notification(GRADLE_SYSTEM_ID.getReadableName() + " sync", notificationData.getTitle(),
                                      notificationData.getMessage(),
                                      notificationData.getNotificationCategory().getNotificationType(),
                                      notificationData.getListener());
  }

  @NotNull
  @Override
  public String getMessage() {
    return myNotificationData.getTitle();
  }

  @NotNull
  @Override
  public String getDescription() {
    return myNotificationData.getMessage();
  }

  @Override
  public List<? extends Failure> getCauses() {
    return null;
  }

  @NotNull
  @Override
  public Notification getNotification() {
    return myNotification;
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    return myNotificationData.getNavigatable();
  }
}
