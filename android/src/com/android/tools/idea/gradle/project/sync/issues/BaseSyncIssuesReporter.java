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
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.project.messages.MessageType.WARNING;

abstract class BaseSyncIssuesReporter {
  @NotNull
  GradleSyncMessages getSyncMessages(@NotNull Module module) {
    return GradleSyncMessages.getInstance(module.getProject());
  }

  @MagicConstant(valuesFromClass = SyncIssue.class)
  abstract int getSupportedIssueType();

  abstract void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile);

  @NotNull
  static MessageType getMessageType(@NotNull SyncIssue syncIssue) {
    return syncIssue.getSeverity() == SEVERITY_ERROR ? ERROR : WARNING;
  }
}
