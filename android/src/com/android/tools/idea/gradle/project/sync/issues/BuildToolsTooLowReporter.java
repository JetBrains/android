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
import com.android.tools.idea.gradle.project.sync.errors.SdkBuildToolsTooLowErrorHandler;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.android.builder.model.SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW;

class BuildToolsTooLowReporter extends SimpleDeduplicatingSyncIssueReporter {
  @NotNull private final SdkBuildToolsTooLowErrorHandler myErrorHandler;

  BuildToolsTooLowReporter() {
    this(SdkBuildToolsTooLowErrorHandler.getInstance());
  }

  @VisibleForTesting
  BuildToolsTooLowReporter(@NotNull SdkBuildToolsTooLowErrorHandler errorHandler) {
    myErrorHandler = errorHandler;
  }

  @Override
  int getSupportedIssueType() {
    return TYPE_BUILD_TOOLS_TOO_LOW;
  }

  @Nullable
  @Override
  protected Object getDeduplicationKey(@NotNull SyncIssue issue) {
    return issue;
  }

  @NotNull
  @Override
  protected List<NotificationHyperlink> getCustomLinks(@NotNull Project project,
                                                       @NotNull List<SyncIssue> syncIssues,
                                                       @NotNull List<Module> affectedModules,
                                                       @NotNull Map<Module, VirtualFile> buildFileMap) {
    assert !syncIssues.isEmpty() && !affectedModules.isEmpty();
    String minimumVersion = syncIssues.get(0).getData();
    if (minimumVersion == null) {
      return ImmutableList.of();
    }


    return myErrorHandler.getQuickFixHyperlinks(minimumVersion, affectedModules, buildFileMap);
  }
}
