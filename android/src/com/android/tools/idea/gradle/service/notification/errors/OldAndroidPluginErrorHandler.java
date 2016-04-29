/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class OldAndroidPluginErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);
    if (firstLine.startsWith("Plugin is too old, please update to a more recent version")) {
      List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
      hyperlinks.add(new FixAndroidGradlePluginVersionHyperlink(false));
      String filePath = notification.getFilePath();
      if (isNotEmpty(filePath)) {
        Integer line = notification.getLine();
        hyperlinks.add(new OpenFileHyperlink(filePath,"Open File", line - 1, notification.getColumn()));
      }
      String newMsg = Joiner.on('\n').join(message); // This way we remove extra lines and spaces from original message.
      updateNotification(notification, project, newMsg, hyperlinks);
      return true;
    }
    return false;
  }
}
