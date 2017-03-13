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
import com.android.tools.idea.project.messages.SyncMessage;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW;
import static com.android.tools.idea.project.messages.MessageType.ERROR;

class BuildToolsTooLowReporter extends BaseSyncIssuesReporter {
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

  @Override
  void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    String minimumVersion = syncIssue.getData();
    assert minimumVersion != null;

    SyncMessage message = new SyncMessage(SyncMessage.DEFAULT_GROUP, ERROR, syncIssue.getMessage());
    List<NotificationHyperlink> quickFixes = myErrorHandler.getQuickFixHyperlinks(minimumVersion, module.getProject(), module);
    message.add(quickFixes);

    getSyncMessages(module).report(message);
  }
}
