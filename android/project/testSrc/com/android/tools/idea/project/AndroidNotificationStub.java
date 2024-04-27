/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.project;

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class AndroidNotificationStub extends AndroidNotification {
  @NotNull private final List<NotificationMessage> myMessages = new ArrayList<>();

  public AndroidNotificationStub(@NotNull Project project) {
    super(project);
  }

  @NotNull
  public static AndroidNotificationStub replaceSyncMessagesService(@NotNull Project project, @NotNull Disposable parentDisposable) {
    AndroidNotificationStub notification = new AndroidNotificationStub(project);
    ServiceContainerUtil.replaceService(project, AndroidNotification.class, notification, parentDisposable);
    return notification;
  }

  @Override
  public void showBalloon(@NotNull String title,
                          @NotNull String text,
                          @NotNull NotificationType type,
                          @NotNull NotificationGroup group,
                          @NotNull NotificationHyperlink... hyperlinks) {
    NotificationMessage message = new NotificationMessage(title, text, type, group, hyperlinks);
    myMessages.add(message);
  }

  @NotNull
  public List<NotificationMessage> getMessages() {
    return myMessages;
  }

  public static class NotificationMessage {
    @NotNull private final String myTitle;
    @NotNull private final String myText;
    @NotNull private final NotificationType myType;
    @NotNull private final NotificationGroup myGroup;
    @NotNull private final NotificationHyperlink[] myHyperlinks;

    NotificationMessage(@NotNull String title,
                        @NotNull String text,
                        @NotNull NotificationType type,
                        @NotNull NotificationGroup group,
                        @NotNull NotificationHyperlink... hyperlinks) {
      myTitle = title;
      myText = text;
      myType = type;
      myGroup = group;
      myHyperlinks = hyperlinks;
    }

    @NotNull
    public String getTitle() {
      return myTitle;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @NotNull
    public NotificationType getType() {
      return myType;
    }

    @NotNull
    public NotificationGroup getGroup() {
      return myGroup;
    }

    @NotNull
    public NotificationHyperlink[] getHyperlinks() {
      return myHyperlinks;
    }
  }
}
