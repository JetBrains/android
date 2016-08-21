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
package com.android.tools.idea.gradle.project.sync.messages.reporter;

import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static org.mockito.Mockito.mock;

public class SyncMessageReporterStub extends SyncMessageReporter {
  @Nullable private SyncMessage myMessage;
  @Nullable private NotificationData myNotification;

  public SyncMessageReporterStub(@NotNull Project project) {
    super(project, mock(ExternalSystemNotificationManager.class));
  }

  @Override
  public void report(@NotNull SyncMessage message) {
    myMessage = message;
  }

  @Nullable
  public SyncMessage getReportedMessage() {
    return myMessage;
  }

  @Override
  public void report(@NotNull NotificationData notification) {
    myNotification = notification;
  }

  @Nullable
  public NotificationData getReportedNotification() {
    return myNotification;
  }
}
