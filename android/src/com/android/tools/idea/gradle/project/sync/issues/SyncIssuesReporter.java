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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

public class SyncIssuesReporter {
  @NotNull private final Map<Integer, BaseSyncIssuesReporter> myStrategies = new HashMap<>(3);
  @NotNull private final BaseSyncIssuesReporter myDefaultMessageFactory;

  @NotNull
  public static SyncIssuesReporter getInstance() {
    return ServiceManager.getService(SyncIssuesReporter.class);
  }

  @SuppressWarnings("unused") // Instantiated by IDEA
  public SyncIssuesReporter(@NotNull UnresolvedDependenciesReporter unresolvedDependenciesReporter) {
    this(unresolvedDependenciesReporter, new ExternalNdkBuildIssuesReporter(), new UnsupportedGradleReporter(),
         new BuildToolsTooLowReporter(), new MissingSdkPackageSyncIssuesReporter());
  }

  @VisibleForTesting
  SyncIssuesReporter(@NotNull BaseSyncIssuesReporter... strategies) {
    for (BaseSyncIssuesReporter strategy : strategies) {
      int issueType = strategy.getSupportedIssueType();
      myStrategies.put(issueType, strategy);
    }
    myDefaultMessageFactory = new UnhandledIssuesReporter();
  }

  public void report(@NotNull Collection<SyncIssue> syncIssues, @NotNull Module module) {
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
      Project project = module.getProject();
      GradleSyncState.getInstance(project).getSummary().setSyncErrorsFound(true);
    }
  }

  private void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    int type = syncIssue.getType();
    BaseSyncIssuesReporter strategy = myStrategies.get(type);
    if (strategy == null) {
      strategy = myDefaultMessageFactory;
    }
    strategy.report(syncIssue, module, buildFile);
  }

  @VisibleForTesting
  @NotNull
  Map<Integer, BaseSyncIssuesReporter> getStrategies() {
    return myStrategies;
  }

  @VisibleForTesting
  @NotNull
  BaseSyncIssuesReporter getDefaultMessageFactory() {
    return myDefaultMessageFactory;
  }
}
