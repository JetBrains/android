/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * Gets notified when users click on hyperlinks in a notification balloon.
 */
public class CustomNotificationListener extends NotificationListener.Adapter {
  @NotNull private final Project myProject;
  @NotNull private final NotificationHyperlink[] myHyperlinks;

  public CustomNotificationListener(@NotNull Project project, @NotNull NotificationHyperlink... hyperlinks) {
    myProject = project;
    myHyperlinks = hyperlinks;
  }

  @Override
  protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
    for (NotificationHyperlink hyperlink : myHyperlinks) {
      if (hyperlink.executeIfClicked(myProject, e)) {
        // If there is only one link, or if clicking this link is supposed to close the
        // notification, do so
        if (hyperlink.isCloseOnClick() || myHyperlinks.length == 1) {
          notification.expire();
        }
        return;
      }
    }
  }

  @NotNull
  public NotificationHyperlink[] getHyperlinks() {
    return myHyperlinks;
  }
}
