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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.formatter.FileBasedFormattingSynchronizer.Formatter;
import com.google.idea.blaze.base.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.blaze.base.formatter.FormatUtils.Replacements;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A CodeStyleManager that handles only the methods that can be processed by an external formatting
 * tool.
 */
class ExternalFormatterCodeStyleManager extends DelegatingCodeStyleManager {

  static class Installer implements StartupActivity {
    @Override
    public void runActivity(Project project) {
      FormatterInstaller.replaceFormatter(project, ExternalFormatterCodeStyleManager::new);
    }
  }

  private ExternalFormatterCodeStyleManager(CodeStyleManager delegate) {
    super(delegate);
  }

  @Nullable
  protected CustomFormatter getCustomFormatterForFile(PsiFile file) {
    return CustomFormatter.EP_NAME
        .extensions()
        .filter(e -> e.appliesToFile(getProject(), file))
        .findFirst()
        .orElse(null);
  }

  @Override
  public void reformatText(PsiFile file, int startOffset, int endOffset)
      throws IncorrectOperationException {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter != null) {
      formatInternal(formatter, file, ImmutableList.of(new TextRange(startOffset, endOffset)));
    } else {
      super.reformatText(file, startOffset, endOffset);
    }
  }

  @Override
  public void reformatText(PsiFile file, Collection<? extends TextRange> ranges)
      throws IncorrectOperationException {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter != null) {
      formatInternal(formatter, file, ranges);
    } else {
      super.reformatText(file, ranges);
    }
  }

  @Override
  public void reformatTextWithContext(PsiFile file, Collection<? extends TextRange> ranges)
      throws IncorrectOperationException {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter != null) {
      formatInternal(formatter, file, ranges);
    } else {
      super.reformatTextWithContext(file, ranges);
    }
  }

  @Override
  public void reformatTextWithContext(PsiFile file, ChangedRangesInfo info) {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter == null) {
      super.reformatTextWithContext(file, info);
      return;
    }

    if (formatter.alwaysFormatEntireFile()) {
      this.reformatText(file, 0, file.getTextLength());
    } else {
      formatInternal(formatter, file, info);
    }
  }

  private void formatInternal(CustomFormatter formatter, PsiFile file, ChangedRangesInfo info) {
    List<TextRange> ranges = new ArrayList<>();
    if (info.insertedRanges != null) {
      ranges.addAll(info.insertedRanges);
    }
    ranges.addAll(info.allChangedRanges);
    formatInternal(formatter, file, ranges);
  }

  private void formatInternal(
      CustomFormatter formatter, PsiFile file, Collection<? extends TextRange> ranges) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    documentManager.commitAllDocuments();
    CheckUtil.checkWritable(file);

    Document document = documentManager.getDocument(file);

    if (document == null) {
      return;
    }
    // If there are postponed PSI changes (e.g., during a refactoring), just abort.
    // If we apply them now, then the incoming text ranges may no longer be valid.
    if (documentManager.isDocumentBlockedByPsi(document)) {
      return;
    }
    FileContentsProvider fileContents = FileContentsProvider.fromPsiFile(file);
    if (fileContents == null) {
      return;
    }
    ListenableFuture<Void> future =
        FileBasedFormattingSynchronizer.applyReplacements(
            file,
            f -> {
              Replacements replacements =
                  formatter.getReplacements(
                      getProject(), fileContents, Collections.unmodifiableCollection(ranges));
              return new Formatter.Result<>(null, replacements);
            });
    FormatUtils.formatWithProgressDialog(file.getProject(), formatter.progressMessage(), future);
  }
}
