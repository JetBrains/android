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
package com.android.tools.idea.gradle.notification;

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class QuickFixNotificationListener extends NotificationListener.Adapter {
  @NotNull private Project myProject;
  @NotNull private NotificationHyperlink myQuickFix;

  public QuickFixNotificationListener(@NotNull Project project, @NotNull NotificationHyperlink quickFix) {
    myProject = project;
    myQuickFix = quickFix;
  }

  @Override
  protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
    myQuickFix.executeIfClicked(myProject, e);
  }

  @NotNull
  public NotificationHyperlink getQuickFix() {
    return myQuickFix;
  }
}