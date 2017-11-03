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
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_GRADLE_TOO_OLD;
import static com.android.tools.idea.gradle.project.sync.errors.UnsupportedGradleVersionErrorHandler.getQuickFixHyperlinksWithGradleVersion;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

class UnsupportedGradleReporter extends BaseSyncIssuesReporter {
  @Override
  int getSupportedIssueType() {
    return TYPE_GRADLE_TOO_OLD;
  }

  @Override
  void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    String text = syncIssue.getMessage();
    MessageType type = getMessageType(syncIssue);
    SyncMessage message = new SyncMessage(DEFAULT_GROUP, type, NonNavigatable.INSTANCE, text);

    String gradleVersion = syncIssue.getData();
    List<NotificationHyperlink> quickFixes = getQuickFixHyperlinksWithGradleVersion(module.getProject(), gradleVersion);
    message.add(quickFixes);

    getSyncMessages(module).report(message);
  }
}
