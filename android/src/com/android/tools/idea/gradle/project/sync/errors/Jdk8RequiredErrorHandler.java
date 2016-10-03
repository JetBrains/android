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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.gradle.project.sync.messages.MessageType;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class Jdk8RequiredErrorHandler extends SyncErrorHandler {
  @NotNull private final Jdks myJdks;

  public Jdk8RequiredErrorHandler() {
    this(Jdks.getInstance());
  }

  public Jdk8RequiredErrorHandler(@NotNull Jdks jdks) {
    myJdks = jdks;
  }

  @Override
  public boolean handleError(@NotNull Throwable error, @NotNull Project project) {
    String text = findJdk8RequiredMessage(error);
    if (text != null) {
      SyncMessage message = new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.ERROR, text);
      message.add(myJdks.getWrongJdkQuickFixes(project));
      SyncMessages.getInstance(project).report(message);
      return true;
    }
    return false;
  }

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String text = findJdk8RequiredMessage(error);
    if (text != null) {
      SyncMessages.getInstance(project).updateNotification(notification, text, myJdks.getWrongJdkQuickFixes(project));
      return true;
    }
    return false;
  }

  @Nullable
  private static String findJdk8RequiredMessage(@NotNull Throwable error) {
    //noinspection ThrowableResultOfMethodCallIgnored
    Throwable cause = getUsefulRootCause(error);
    // Example:
    // com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0
    String text = cause.getMessage();
    if (isNotEmpty(text) && text.contains("Unsupported major.minor version 52.0")) {
      if (!text.endsWith(".")) {
        text += ".";
      }
      text += " Please use JDK 8 or newer.";
      return text;
    }
    return null;
  }

  @NotNull
  private static Throwable getUsefulRootCause(@NotNull Throwable error) {
    Throwable rootCause = error;
    while (true) {
      if (rootCause.getCause() == null || rootCause.getCause().getMessage() == null) {
        break;
      }
      rootCause = rootCause.getCause();
    }
    return rootCause;
  }

}
