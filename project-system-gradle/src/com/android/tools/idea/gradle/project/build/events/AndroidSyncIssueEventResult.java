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

import static com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEvent.convertCategory;

import com.intellij.build.events.Failure;
import com.intellij.build.events.MessageEvent.Kind;
import com.intellij.build.events.MessageEventResult;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AndroidSyncIssueEventResult implements MessageEventResult {

  @NotNull private final List<Failure> myFailures;
  @NotNull private final Kind myKind;
  @NotNull private final String myDetails;

  public AndroidSyncIssueEventResult(@NotNull NotificationData notificationData) {
    myKind = convertCategory(notificationData.getNotificationCategory());
    myFailures = myKind.equals(Kind.ERROR)
                 ? Collections.singletonList(AndroidSyncFailure.create(notificationData))
                 : Collections.emptyList();
    myDetails = notificationData.getMessage();
  }

  @NotNull
  public List<? extends Failure> getFailures() {
    return myFailures;
  }

  @NotNull
  @Override
  public Kind getKind() {
    return myKind;
  }

  @NotNull
  @Override
  public String getDetails() {
    return myDetails;
  }
}
