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

import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.output.parser.BuildOutputParser;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.util.PositionInFile;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.FilePosition;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueChecker;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.ide.DataManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker;
import org.jetbrains.plugins.gradle.issue.GradleIssueData;

class ExternalNdkBuildIssuesReporter extends BaseSyncIssuesReporter {
  @NotNull private final BuildOutputParser myBuildOutputParser;

  ExternalNdkBuildIssuesReporter() {
    this(createBuildOutputParser());
  }

  @NotNull
  private static BuildOutputParser createBuildOutputParser() {
    return new BuildOutputParser(JpsServiceManager.getInstance().getExtensions(PatternAwareOutputParser.class));
  }

  @VisibleForTesting
  ExternalNdkBuildIssuesReporter(@NotNull BuildOutputParser buildOutputParser) {
    myBuildOutputParser = buildOutputParser;
  }

  @Override
  int getSupportedIssueType() {
    return IdeSyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION;
  }

  @Override
  void report(@NotNull IdeSyncIssue IdeSyncIssue,
              @NotNull Module module,
              @Nullable VirtualFile buildFile,
              @NotNull SyncIssueUsageReporter usageReporter) {
    String group = "External Native Build Issues";

    String nativeToolOutput = IdeSyncIssue.getData();
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
          NotificationData notificationData = messages.createNotification(group, text, category, position);

          GradleIssueData issueData =
            new GradleIssueData(
              project.getBasePath(),
              new Throwable(text),
              null,
              new FilePosition(new File(position.file.getPath()), position.line, position.column));
          List<GradleIssueChecker> knownIssuesCheckList = GradleIssueChecker.getKnownIssuesCheckList();
          List<BuildIssueQuickFix> quickFixes = new ArrayList<>();
          for (BuildIssueChecker<GradleIssueData> checker : knownIssuesCheckList) {
            BuildIssue buildIssue = checker.check(issueData);
            if (buildIssue != null) {
              for (BuildIssueQuickFix quickFix : buildIssue.getQuickFixes()) {
                notificationData.setListener(quickFix.getId(), (notification, event) -> {
                  if (event.getSource() instanceof JComponent) {
                    quickFix.runQuickFix(project, DataManager.getDataProvider((JComponent)event.getSource()));
                  }
                });
                quickFixes.add(quickFix);
              }
            }
          }
          messages.report(notificationData, quickFixes);
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
