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
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.google.common.base.Joiner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Presents to the user any unexpected events that occurred during project import.
 */
public class ProjectSyncMessageDataService implements ProjectDataService<Message, Void> {
  private static final Logger LOG = Logger.getInstance(ProjectSyncMessageDataService.class);

  @NotNull
  @Override
  public Key<Message> getTargetDataKey() {
    return AndroidProjectKeys.IMPORT_EVENT_MSG;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<Message>> toImport, @NotNull final Project project, boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);

    for (DataNode<Message> node : toImport) {
      Message message = node.getData();
      messages.add(message);

      String text = Joiner.on("\n").join(message.getText());
      LOG.info(text);
    }
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
