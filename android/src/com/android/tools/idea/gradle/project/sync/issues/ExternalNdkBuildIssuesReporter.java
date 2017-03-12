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
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.tools.idea.gradle.output.parser.BuildOutputParser;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

class ExternalNdkBuildIssuesReporter extends BaseSyncIssuesReporter {
  @NotNull private final BuildOutputParser myBuildOutputParser;
  @NotNull private final SyncErrorHandler[] myErrorHandlers;

  ExternalNdkBuildIssuesReporter() {
    this(createBuildOutputParser(), SyncErrorHandler.getExtensions());
  }

  @NotNull
  private static BuildOutputParser createBuildOutputParser() {
    return new BuildOutputParser(JpsServiceManager.getInstance().getExtensions(PatternAwareOutputParser.class));
  }

  @VisibleForTesting
  ExternalNdkBuildIssuesReporter(@NotNull BuildOutputParser buildOutputParser, @NotNull SyncErrorHandler[] errorHandlers) {
    myBuildOutputParser = buildOutputParser;
    myErrorHandlers = errorHandlers;
  }

  @Override
  int getSupportedIssueType() {
    return TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION;
  }

  @Override
  void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    String group = "External Native Build Issues";

    String nativeToolOutput = syncIssue.getData();
    if (nativeToolOutput != null) {
      GradleSyncMessages messages = getSyncMessages(module);

      // Parse the native build tool output with the list of existing parsers.
      List<Message> compilerMessages = myBuildOutputParser.parseGradleOutput(nativeToolOutput);
      for (Message compilerMessage : compilerMessages) {
        MessageType type = MessageType.findMatching(compilerMessage.getKind());
        PositionInFile position = createPosition(compilerMessage.getSourceFilePositions());
        String text = compilerMessage.getText();

        Project project = module.getProject();

        if (type == ERROR) {
          // TODO make error handlers work with SyncMessage, instead of NotificationData.
          NotificationCategory category = type.convertToCategory();
          NotificationData notification = messages.createNotification(group, text, category, position);

          // Try to parse the error messages using the list of existing error handlers to find any potential quick-fixes.
          for (SyncErrorHandler handler : myErrorHandlers) {
            if (handler.handleError(new ExternalSystemException(text), notification, project)) {
              break;
            }
          }
          messages.report(notification);
          continue;
        }

        SyncMessage message;
        if (position != null) {
          message = new SyncMessage(project, group, type, position, text);
        }
        else {
          message = new SyncMessage(group, type, text);
        }
        messages.report(message);
      }
    }
  }

  @Nullable
  private static PositionInFile createPosition(@NotNull List<SourceFilePosition> sourceFilePositions) {
    assert !sourceFilePositions.isEmpty();

    VirtualFile sourceFile = null;
    SourceFile source = sourceFilePositions.get(0).getFile();
    if (source.getSourceFile() != null) {
      sourceFile = findFileByIoFile(source.getSourceFile(), true);
    }
    if (sourceFile != null) {
      SourcePosition sourcePosition = sourceFilePositions.get(0).getPosition();
      return new PositionInFile(sourceFile, sourcePosition.getStartLine(), sourcePosition.getStartColumn());
    }
    return null;
  }
}
