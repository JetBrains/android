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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;

public final class AndroidSyncFailure {
  @Contract(pure = true)
  @NotNull
  public static Failure create(@NotNull NotificationData data) {
    return create(data, "");
  }

  @Contract(pure = true)
  @NotNull
  public static Failure create(@NotNull NotificationData data, @NotNull String suffix) {
    return new Failure() {
      @NotNull
      @Override
      public String getMessage() {
        return data.getTitle();
      }

      @NotNull
      @Override
      public String getDescription() {
        return data.getMessage() + suffix;
      }

      @Override
      public List<? extends Failure> getCauses() {
        return null;
      }

      @NotNull
      @Override
      public Notification getNotification() {
        return new Notification(GRADLE_SYSTEM_ID.getReadableName() + " sync", data.getTitle(), data.getMessage(), data.getNotificationCategory().getNotificationType())
          .setListener(data.getListener());
      }

      @Nullable
      @Override
      public Navigatable getNavigatable() {
        return data.getNavigatable();
      }
    };
  }
}
