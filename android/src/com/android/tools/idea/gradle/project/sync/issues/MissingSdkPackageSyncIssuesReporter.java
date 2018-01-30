/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallSdkPackageHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.project.messages.MessageType.findFromSyncIssue;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

/**
 * Reporter for dealing with {@link SyncIssue#TYPE_MISSING_SDK_PACKAGE} issues.
 */
public class MissingSdkPackageSyncIssuesReporter extends BaseSyncIssuesReporter {
  @Override
  int getSupportedIssueType() {
    return SyncIssue.TYPE_MISSING_SDK_PACKAGE;
  }

  @Override
  void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    SyncMessage message = new SyncMessage(DEFAULT_GROUP, findFromSyncIssue(syncIssue), syncIssue.getMessage());
    if (syncIssue.getData() != null) {
      message.add(new InstallSdkPackageHyperlink(syncIssue.getData()));
    }

    getSyncMessages(module).report(message);
  }
}
