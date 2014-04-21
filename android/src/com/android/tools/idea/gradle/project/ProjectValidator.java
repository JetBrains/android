/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Processor;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
import org.jetbrains.android.inspections.lint.IntellijLintIssueRegistry;
import org.jetbrains.android.inspections.lint.IntellijLintRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Set;

public class ProjectValidator {
  public static final Key<List<Message>> VALIDATION_MESSAGES = Key.create("gradle.validation.messages");
  private static final Set<String> FILES_TO_PROCESS =
    ImmutableSet.of(SdkConstants.FN_SETTINGS_GRADLE, SdkConstants.FN_BUILD_GRADLE, SdkConstants.FN_LOCAL_PROPERTIES,
                    SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES);

  private ProjectValidator() { }

  /**
   * Runs lint checks on all the Gradle files found underneath the given root directory and reports messages to the
   * {@link ProjectSyncMessages} message window. Returns true if there are no significant problems, or false if there are critical errors.
   * Any errors or warnings that are generated are saved, and are send to the Messages window upon a subsequent call to the
   * {@link #mergeQueuedMessages(com.intellij.openapi.project.Project)} method.
   */
  public static boolean validate(@NotNull Project project, @NotNull File rootDir) {
    VirtualFile file = VfsUtil.findFileByIoFile(rootDir, true);
    if (file == null) {
      return false;
    }
    VirtualFile rootDirectory = file.isDirectory() ? file : file.getParent();

    final List<File> files = Lists.newArrayList();
    VfsUtil.processFileRecursivelyWithoutIgnored(rootDirectory, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        if (FILES_TO_PROCESS.contains(virtualFile.getName().toLowerCase())) {
          files.add(VfsUtilCore.virtualToIoFile(virtualFile));
        }
        return true;
      }
    });

    MyLintClient lintClient = new MyLintClient(project);
    ImmutableList<Module> modules = ImmutableList.of();
    LintRequest request = new IntellijLintRequest(lintClient, project, null, modules, false) {
      @NonNull
      @Override
      public List<File> getFiles() {
        return files;
      }
    };
    LintDriver lintDriver = new LintDriver(new IntellijLintIssueRegistry(), lintClient);
    lintDriver.analyze(request);
    return !lintClient.hasFatalError();
  }

  /**
   * Takes any accumulated validation errors that have been stored with the project and shows them in the Messages tool window.
   */
  public static void mergeQueuedMessages(@NotNull Project project) {
    List<Message> messages = project.getUserData(VALIDATION_MESSAGES);
    if (messages == null) {
      return;
    }
    ProjectSyncMessages projectSyncMessages = ProjectSyncMessages.getInstance(project);
    for (Message message : messages) {
      projectSyncMessages.add(message);
    }
    project.putUserData(VALIDATION_MESSAGES, null);
  }

  private static class MyLintClient extends IntellijLintClient {
    private static final String GROUP_NAME = "Project import";
    private boolean myFatalError = false;
    private final List<Message> myMessages = Lists.newArrayList();

    protected MyLintClient(@NonNull Project project) {
      super(project);
      project.putUserData(VALIDATION_MESSAGES, myMessages);
    }

    @Override
    public void report(@NonNull Context context,
                       @NonNull Issue issue,
                       @NonNull Severity severity,
                       @Nullable Location location,
                       @NonNull String message,
                       @Nullable Object data) {
      myFatalError |= severity.compareTo(Severity.ERROR) >= 0;

      File file = location != null ? location.getFile() : null;
      VirtualFile virtualFile = file != null ? LocalFileSystem.getInstance().findFileByIoFile(file) : null;
      Position start = location != null ? location.getStart() : null;
      if (virtualFile != null && start != null) {
        int line = start.getLine();
        int column = start.getColumn();
        int offset = start.getOffset();
        if (line == -1 && offset >= 0) {
          PsiManager psiManager = PsiManager.getInstance(myProject);
          PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
          PsiFile psiFile = psiManager.findFile(virtualFile);
          if (psiFile != null) {
            Document document = psiDocumentManager.getDocument(psiFile);
            if (document != null) {
              line = document.getLineNumber(offset);
              column = offset - document.getLineStartOffset(line);
            }
          }
        }
        myMessages.add(new Message(GROUP_NAME, convertSeverity(severity), virtualFile, line, column, message));
      } else {
        myMessages.add(new Message(GROUP_NAME, convertSeverity(severity), message));
      }
    }

    @Override
    public void log(@NonNull Severity severity, @Nullable Throwable exception, @Nullable String format, @Nullable Object... args) {
      myMessages.add(new Message(GROUP_NAME, convertSeverity(severity), format != null ? String.format(format, args) : ""));
      if (exception != null) {
        LOG.warn("Exception occurred during validation of project", exception);
      }
    }

    @NonNull
    private static Message.Type convertSeverity(@NonNull Severity severity) {
      switch(severity) {
        case ERROR:
        case FATAL:
          return Message.Type.ERROR;
        case IGNORE:
          return Message.Type.INFO;
        default:
        case INFORMATIONAL:
          return Message.Type.INFO;
        case WARNING:
          return Message.Type.WARNING;
      }
    }

    public boolean hasFatalError() {
      return myFatalError;
    }

    @NonNull
    @Override
    protected List<Issue> getIssues() {
      return new IntellijLintIssueRegistry().getIssues();
    }

    @Nullable
    @Override
    protected Module getModule() {
      return null;
    }

    @Override
    public boolean isProjectDirectory(@NonNull File dir) {
      return new File(dir, SdkConstants.FN_BUILD_GRADLE).exists();
    }
  }
}
