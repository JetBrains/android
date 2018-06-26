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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.ui.QuickFixNotificationListener;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

/**
 * This class provides simple deduplication behaviour for other reporters.
 * The abstract method {@link #getDeduplicationKey(SyncIssue)} should be overwritten to return the key that should be
 * used for the deduplication.
 */
public abstract class SimpleDeduplicatingSyncIssueReporter extends BaseSyncIssuesReporter {
  /**
   * Reporting single sync issues falls back to the result message generation. This method should be overridden
   * in subclasses should different semantics be required.
   */
  @Override
  void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    getSyncMessages(module).report(generateSyncMessage(syncIssue, module, buildFile));
  }

  @Override
  void reportAll(@NotNull List<SyncIssue> syncIssues,
                 @NotNull Map<SyncIssue, Module> moduleMap,
                 @NotNull Map<Module, VirtualFile> buildFileMap) {
    // Group by the deduplication key.
    Map<Object, List<SyncIssue>> groupedIssues = new LinkedHashMap<>();
    for (SyncIssue issue : syncIssues) {
      Object key = getDeduplicationKey(issue);
      if (key != null) {
        groupedIssues.computeIfAbsent(key, (config) -> new ArrayList<>()).add(issue);
      }
    }

    // Report once for each group, including the list of affected modules.
    for (List<SyncIssue> entry : groupedIssues.values()) {
      if (entry.isEmpty()) {
        continue;
      }
      SyncIssue issue = entry.get(0);
      Module module = moduleMap.get(issue);
      if (module == null) {
        continue;
      }

      List<Module> affectedModules =
        entry.stream().map(moduleMap::get).filter(Objects::nonNull).distinct().sorted(Comparator.comparing(Module::getName))
             .collect(Collectors.toList());
      boolean isError = entry.stream().anyMatch(i -> i.getSeverity() == SEVERITY_ERROR);
      createNotificationDataAndReport(module.getProject(), issue, affectedModules, buildFileMap, isError);
    }
  }

  private static void createNotificationDataAndReport(@NotNull Project project,
                                                      @NotNull SyncIssue syncIssue,
                                                      @NotNull List<Module> affectedModules,
                                                      @NotNull Map<Module, VirtualFile> buildFileMap,
                                                      boolean isError) {
    GradleSyncMessages messages = GradleSyncMessages.getInstance(project);
    MessageType type = isError ? ERROR : WARNING;

    String message = syncIssue.getMessage();
    NotificationData notification = messages.createNotification(DEFAULT_GROUP, message, type.convertToCategory(), null);

    // Add links to each of the affected modules
    StringBuilder builder = new StringBuilder();
    builder.append("<br>Affected Modules: ");
    for (Iterator<Module> it = affectedModules.iterator(); it.hasNext(); ) {
      Module m = it.next();
      if (m != null) {
        createModuleLink(project, notification, builder, m, buildFileMap.get(m));
        if (it.hasNext()) {
          builder.append(", ");
        }
      }
    }

    message += builder.toString();

    notification.setMessage(message);
    messages.report(notification);
  }

  private static void createModuleLink(@NotNull Project project,
                                       @NotNull NotificationData notification,
                                       @NotNull StringBuilder builder,
                                       @NotNull Module module,
                                       @Nullable VirtualFile buildFile) {
    if (buildFile == null) {
      // No build file found, just include the name of the module.
      builder.append(module.getName());
    }
    else {
      OpenFileHyperlink link = new OpenFileHyperlink(buildFile.getPath(), module.getName(), -1, -1);
      builder.append(link.toHtml());
      notification.setListener(link.getUrl(), new QuickFixNotificationListener(project, link));
    }
  }

  @Nullable
  protected abstract Object getDeduplicationKey(@NotNull SyncIssue issue);
}
