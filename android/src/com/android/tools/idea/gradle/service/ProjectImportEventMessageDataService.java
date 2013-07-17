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
package com.android.tools.idea.gradle.service;

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.ProjectImportEventMessage;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemIdeNotificationManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;

/**
 * Presents to the user any unexpected events that occurred during project import.
 */
public class ProjectImportEventMessageDataService implements ProjectDataService<ProjectImportEventMessage, Void> {
  private static final Logger LOG = Logger.getInstance(ProjectImportEventMessageDataService.class);

  @NotNull
  @Override
  public Key<ProjectImportEventMessage> getTargetDataKey() {
    return AndroidProjectKeys.IMPORT_EVENT_MSG;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ProjectImportEventMessage>> toImport,
                         @NotNull final Project project,
                         boolean synchronous) {
    final ExternalSystemIdeNotificationManager notificationManager = ServiceManager.getService(ExternalSystemIdeNotificationManager.class);
    if (notificationManager == null) {
      return;
    }

    Multimap<String, String> messagesByCategory = ArrayListMultimap.create();
    for (DataNode<ProjectImportEventMessage> node : toImport) {
      ProjectImportEventMessage message = node.getData();
      String category = message.getCategory();
      messagesByCategory.put(category, message.getText());
      LOG.info(message.toString());
    }
    final StringBuilder builder = new StringBuilder();
    builder.append("<html>");
    for (String category : messagesByCategory.keySet()) {
      Collection<String> messages = messagesByCategory.get(category);
      if (category.isEmpty()) {
        Joiner.on("<br>").join(messages);
      }
      else {
        // If the category is not an empty String, we show the category and each message as a list.
        builder.append(category).append("<ul>");
        for (String message : messages) {
          builder.append("<li>").append(message).append("</li>");
        }
        builder.append("</ul>");
      }
    }
    builder.append("</html>");

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        String title = "Unexpected events:";
        String messageToShow = builder.toString();
        notificationManager.showNotification(title, messageToShow, NotificationType.ERROR, project, GradleConstants.SYSTEM_ID, null);
      }
    });
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
