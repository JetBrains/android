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
package com.android.tools.idea.gradle.project.sync.messages.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessageReporter;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.IntObjectLinkedMap;
import com.intellij.util.containers.IntObjectLinkedMap.MapEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

public class SyncIssuesMessageReporter {
  @NotNull private final IntObjectLinkedMap<MapEntry<BaseSyncIssueMessageReporter>> myStrategies = new IntObjectLinkedMap<>(3);
  @NotNull private final BaseSyncIssueMessageReporter myDefaultMessageFactory;

  public SyncIssuesMessageReporter(@NotNull SyncMessageReporter messageReporter) {
    this(messageReporter, new UnhandledIssueMessageReporter(messageReporter), new ExternalNativeBuildMessageReporter(messageReporter),
         new UnsupportedGradleMessageReporter(messageReporter));
  }

  @VisibleForTesting
  SyncIssuesMessageReporter(@NotNull SyncMessageReporter messageReporter, @NotNull BaseSyncIssueMessageReporter... strategies) {
    for (BaseSyncIssueMessageReporter strategy : strategies) {
      MapEntry<BaseSyncIssueMessageReporter> entry = new MapEntry<>(strategy.getSupportedIssueType(), strategy);
      myStrategies.putEntry(entry);
    }
    myDefaultMessageFactory = new UnhandledIssueMessageReporter(messageReporter);
  }

  public void reportSyncIssues(@NotNull Collection<SyncIssue> syncIssues, @NotNull Module module) {
    Project project = module.getProject();
    if (syncIssues.isEmpty()) {
      return;
    }

    boolean hasSyncErrors = false;

    VirtualFile buildFile = getGradleBuildFile(module);
    for (SyncIssue syncIssue : syncIssues) {
      if (syncIssue.getSeverity() == SEVERITY_ERROR) {
        hasSyncErrors = true;
      }
      report(syncIssue, module, buildFile);
    }

    if (hasSyncErrors) {
      GradleSyncState.getInstance(project).getSummary().setSyncErrorsFound(true);
    }
  }

  private void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    int type = syncIssue.getType();
    MapEntry<BaseSyncIssueMessageReporter> entry = myStrategies.getEntry(type);

    BaseSyncIssueMessageReporter strategy = entry != null ? entry.value : myDefaultMessageFactory;
    strategy.report(syncIssue, module, buildFile);
  }
}
