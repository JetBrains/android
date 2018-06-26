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
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.pixelprobe.util.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    Map<Object, List<SyncIssue>> groupedIssues = Maps.newHashMap();
    for (SyncIssue issue : syncIssues) {
      Object key = getDeduplicationKey(issue);
      if (key != null) {
        groupedIssues.computeIfAbsent(key, (config) -> Lists.newArrayList()).add(issue);
      }
    }

    // Report once for each group, including the list of affected modules.
    for (Map.Entry<Object, List<SyncIssue>> entry : groupedIssues.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      SyncIssue issue = entry.getValue().get(0);
      Module module = moduleMap.get(issue);
      if (module == null) {
        continue;
      }

      Set<String> affectedModules = entry.getValue().stream().map(moduleMap::get).filter(Objects::nonNull).map(Module::getName).collect(
        Collectors.toSet());
      boolean isError = entry.getValue().stream().anyMatch(i -> i.getSeverity() == SEVERITY_ERROR);
      String messageText = issue.getMessage() + "\nAffected Modules: " + Strings.join(affectedModules, ", ");
      SyncMessage message = new SyncMessage(DEFAULT_GROUP, isError ? ERROR : WARNING, messageText);
      GradleSyncMessages.getInstance(module.getProject()).report(message);
    }
  }

  @Nullable
  protected abstract Object getDeduplicationKey(@NotNull SyncIssue issue);
}
