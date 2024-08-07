/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.intellij.formatting.FormattingMode;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.FormattingModeAwareIndentAdjuster;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import java.util.Collection;
import javax.annotation.Nullable;

/** A delegating {@link CodeStyleManager}. */
abstract class DelegatingCodeStyleManager extends CodeStyleManager
    implements FormattingModeAwareIndentAdjuster {

  protected final CodeStyleManager delegate;

  protected DelegatingCodeStyleManager(CodeStyleManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public void scheduleIndentAdjustment(Document document, int offset) {
    delegate.scheduleIndentAdjustment(document, offset);
  }

  @Override
  public void reformatTextWithContext(PsiFile file, ChangedRangesInfo info)
      throws IncorrectOperationException {
    delegate.reformatTextWithContext(file, info);
  }

  @Override
  public void reformatTextWithContext(PsiFile file, Collection<? extends TextRange> ranges)
      throws IncorrectOperationException {
    delegate.reformatTextWithContext(file, ranges);
  }

  @Override
  public int getSpacing(PsiFile file, int offset) {
    return delegate.getSpacing(file, offset);
  }

  @Override
  public int getMinLineFeeds(PsiFile file, int offset) {
    return delegate.getMinLineFeeds(file, offset);
  }

  /** Uses same fallback as {@link CodeStyleManager#getCurrentFormattingMode}. */
  @Override
  public FormattingMode getCurrentFormattingMode() {
    if (delegate instanceof FormattingModeAwareIndentAdjuster) {
      return ((FormattingModeAwareIndentAdjuster) delegate).getCurrentFormattingMode();
    }
    return FormattingMode.REFORMAT;
  }

  @Override
  public int adjustLineIndent(final Document document, final int offset, FormattingMode mode)
      throws IncorrectOperationException {
    if (delegate instanceof FormattingModeAwareIndentAdjuster) {
      return ((FormattingModeAwareIndentAdjuster) delegate)
          .adjustLineIndent(document, offset, mode);
    }
    return offset;
  }

  @Override
  public Project getProject() {
    return delegate.getProject();
  }

  @Override
  public PsiElement reformat(PsiElement element) throws IncorrectOperationException {
    return delegate.reformat(element);
  }

  @Override
  public PsiElement reformat(PsiElement element, boolean canChangeWhiteSpacesOnly)
      throws IncorrectOperationException {
    return delegate.reformat(element, canChangeWhiteSpacesOnly);
  }

  @Override
  public PsiElement reformatRange(PsiElement element, int startOffset, int endOffset)
      throws IncorrectOperationException {
    return delegate.reformatRange(element, startOffset, endOffset);
  }

  @Override
  public PsiElement reformatRange(
      PsiElement element, int startOffset, int endOffset, boolean canChangeWhiteSpacesOnly)
      throws IncorrectOperationException {
    return delegate.reformatRange(element, startOffset, endOffset, canChangeWhiteSpacesOnly);
  }

  @Override
  public void reformatText(PsiFile file, int startOffset, int endOffset)
      throws IncorrectOperationException {
    delegate.reformatText(file, startOffset, endOffset);
  }

  @Override
  public void reformatText(PsiFile file, Collection<? extends TextRange> ranges)
      throws IncorrectOperationException {
    delegate.reformatText(file, ranges);
  }

  @Override
  public void adjustLineIndent(PsiFile file, TextRange rangeToAdjust)
      throws IncorrectOperationException {
    delegate.adjustLineIndent(file, rangeToAdjust);
  }

  @Override
  public int adjustLineIndent(PsiFile file, int offset) throws IncorrectOperationException {
    return delegate.adjustLineIndent(file, offset);
  }

  @Override
  public int adjustLineIndent(Document document, int offset) {
    return delegate.adjustLineIndent(document, offset);
  }

  @Override
  public boolean isLineToBeIndented(PsiFile file, int offset) {
    return delegate.isLineToBeIndented(file, offset);
  }

  @Override
  @Nullable
  public String getLineIndent(PsiFile file, int offset) {
    return delegate.getLineIndent(file, offset);
  }

  @Override
  @Nullable
  public String getLineIndent(Document document, int offset) {
    return delegate.getLineIndent(document, offset);
  }

  @Override
  public Indent getIndent(String text, FileType fileType) {
    return delegate.getIndent(text, fileType);
  }

  @Override
  public String fillIndent(Indent indent, FileType fileType) {
    return delegate.fillIndent(indent, fileType);
  }

  @Override
  public Indent zeroIndent() {
    return delegate.zeroIndent();
  }

  @Override
  public void reformatNewlyAddedElement(ASTNode block, ASTNode addedElement)
      throws IncorrectOperationException {
    delegate.reformatNewlyAddedElement(block, addedElement);
  }

  @Override
  public boolean isSequentialProcessingAllowed() {
    return delegate.isSequentialProcessingAllowed();
  }

  @Override
  public void performActionWithFormatterDisabled(Runnable r) {
    delegate.performActionWithFormatterDisabled(r);
  }

  @Override
  public <T extends Throwable> void performActionWithFormatterDisabled(ThrowableRunnable<T> r)
      throws T {
    delegate.performActionWithFormatterDisabled(r);
  }

  @Override
  public <T> T performActionWithFormatterDisabled(Computable<T> r) {
    return delegate.performActionWithFormatterDisabled(r);
  }

  @Override
  public void scheduleReformatWhenSettingsComputed(PsiFile file) {
    delegate.scheduleReformatWhenSettingsComputed(file);
  }
}
