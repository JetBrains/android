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
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.MessageEventResult;
import com.intellij.notification.Notification;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEvent.convertCategory;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;

public class AndroidSyncIssueEventResult implements MessageEventResult, FailureResult {

  @NotNull private final List<Failure> myFailures;
  @NotNull private final MessageEvent.Kind myKind;
  @NotNull private final String myDetails;
  @NotNull private final Notification myNotification;

  public AndroidSyncIssueEventResult(@NotNull NotificationData notificationData) {
    Failure failure = new Failure() {
      @NotNull
      @Override
      public String getMessage() {
        return notificationData.getTitle();
      }

      @NotNull
      @Override
      public String getDescription() {
        return notificationData.getMessage();
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
        return notificationData.getNavigatable();
      }
    };
    myFailures = Collections.singletonList(failure);
    myKind = convertCategory(notificationData.getNotificationCategory());
    myDetails = notificationData.getMessage();
    myNotification = new Notification(GRADLE_SYSTEM_ID.getReadableName() + " sync", notificationData.getTitle(),
                                      notificationData.getMessage(),
                                      notificationData.getNotificationCategory().getNotificationType(),
                                      notificationData.getListener());
  }

  @NotNull
  @Override
  public List<? extends Failure> getFailures() {
    return myFailures;
  }

  @NotNull
  @Override
  public MessageEvent.Kind getKind() {
    return myKind;
  }

  @NotNull
  @Override
  public String getDetails() {
    return myDetails;
  }
}
