/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.formatter;

import static java.util.Comparator.comparing;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/** Helper class for applying formatting changes to a {@link PsiFile}. */
public final class FormatUtils {

  private FormatUtils() {}

  /**
   * A set of replacements to apply. Keeps the replacements sorted and enforces dropping
   * replacements that don't actually change anything, avoiding unnecessary write actions and
   * documents committals.
   */
  public static final class Replacements {
    public static final Replacements EMPTY = new Replacements();

    public final TreeMap<TextRange, String> replacements =
        new TreeMap<>(comparing(TextRange::getStartOffset));

    public void addReplacement(TextRange range, String before, String after) {
      if (!before.equals(after)) {
        replacements.put(range, after);
      }
    }
  }

  /**
   * Provides file contents asynchronously, providing information about whether there have been any
   * changes.
   */
  public static final class FileContentsProvider {
    @Nullable
    public static FileContentsProvider fromPsiFile(PsiFile file) {
      String text = getCurrentText(file);
      return text == null ? null : new FileContentsProvider(file, text);
    }

    public final PsiFile file;
    private final String initialFileContents;

    private FileContentsProvider(PsiFile file, String initialFileContents) {
      this.initialFileContents = initialFileContents;
      this.file = file;
    }

    public boolean hasFileContentsChanged() {
      return getFileContentsIfUnchanged() == null;
    }

    @Nullable
    public String getFileContentsIfUnchanged() {
      String text = getCurrentText(file);
      return initialFileContents.equals(text) ? text : null;
    }

    public String getFileName() {
      return file.getName();
    }

    public String getInitialFileContents() {
      return initialFileContents;
    }

    public Project getProject() {
      return file.getProject();
    }
  }

  /** Checks whether the {@link Document} is still writable. */
  static boolean canApplyChanges(Project project, @Nullable Document document) {
    return document != null
        && !ReadAction.compute(
            () -> PsiDocumentManager.getInstance(project).isDocumentBlockedByPsi(document));
  }

  /** Calls the runnable inside a write action iff the document's text hasn't changed. */
  static void runWriteActionIfUnchanged(
      Project project, Document document, String inputText, Runnable action) {
    WriteCommandAction.runWriteCommandAction(
        project,
        () -> {
          if (inputText.equals(document.getText())) {
            action.run();
          }
        });
  }

  /**
   * Performs the given replacements synchronously, under a write action. If the file contents have
   * changed by the time the write lock is taken, no changes are applied.
   */
  static void performReplacements(FileContentsProvider fileContents, Replacements replacements) {
    if (replacements.replacements.isEmpty()) {
      return;
    }
    Project project = fileContents.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(fileContents.file);
    if (!canApplyChanges(project, document)) {
      return;
    }
    String text = fileContents.getFileContentsIfUnchanged();
    if (text == null) {
      return;
    }
    runWriteActionIfUnchanged(
        fileContents.getProject(),
        document,
        text,
        () -> {
          for (Map.Entry<TextRange, String> entry :
              replacements.replacements.descendingMap().entrySet()) {
            document.replaceString(
                entry.getKey().getStartOffset(), entry.getKey().getEndOffset(), entry.getValue());
          }
          PsiDocumentManager.getInstance(fileContents.getProject()).commitDocument(document);
        });
  }

  @Nullable
  private static String getCurrentText(PsiFile file) {
    return ReadAction.compute(
        () -> {
          PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
          Document document = documentManager.getDocument(file);
          return document == null ? null : document.getText();
        });
  }

  /** Runs a format future under a progress dialog. */
  public static void formatWithProgressDialog(
      Project project, String title, ListenableFuture<?> future) {
    ProgressWindow progressWindow =
        new BackgroundableProcessIndicator(
            project, title, PerformInBackgroundOption.DEAF, "Cancel", "Cancel", true);
    progressWindow.setIndeterminate(true);
    progressWindow.start();
    progressWindow.addStateDelegate(
        new AbstractProgressIndicatorExBase() {
          @Override
          public void cancel() {
            super.cancel();
            future.cancel(true);
          }
        });
    future.addListener(
        () -> {
          if (progressWindow.isRunning()) {
            progressWindow.stop();
            progressWindow.processFinish();
          }
        },
        MoreExecutors.directExecutor());
  }
}
