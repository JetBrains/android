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
package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * Handles some boilerplate, and allows lazy calculation of some expensive components, which aren't
 * required if the element is filtered out by IJ.
 */
public abstract class BuildLookupElement extends LookupElement {

  public static final BuildLookupElement[] EMPTY_ARRAY = new BuildLookupElement[0];

  protected final String baseName;
  protected final QuoteType quoteWrapping;
  protected final boolean wrapWithQuotes;

  public BuildLookupElement(String baseName, QuoteType quoteWrapping) {
    this.baseName = baseName;
    this.quoteWrapping = quoteWrapping;
    this.wrapWithQuotes = quoteWrapping != QuoteType.NoQuotes;
  }

  private static boolean insertClosingQuotes() {
    return CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE;
  }

  @Override
  public String getLookupString() {
    return insertClosingQuotes()
        ? quoteWrapping.wrap(baseName)
        : quoteWrapping.quoteString + baseName;
  }

  @Nullable
  public abstract Icon getIcon();

  protected String getItemText() {
    return baseName;
  }

  @Nullable
  protected String getTypeText() {
    return null;
  }

  @Nullable
  protected String getTailText() {
    return null;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(getItemText());
    presentation.setTailText(getTailText());
    presentation.setTypeText(getTypeText());
    presentation.setIcon(getIcon());
  }

  /**
   * If we're wrapping with quotes, handle the (very common) case where we have a closing quote
   * after the caret -- we want to remove this quote.
   */
  @Override
  public void handleInsert(InsertionContext context) {
    if (!wrapWithQuotes || !insertClosingQuotes()) {
      super.handleInsert(context);
      return;
    }
    Document document = context.getDocument();
    context.commitDocument();
    PsiElement suffix = context.getFile().findElementAt(context.getTailOffset());
    if (suffix != null && suffix.getText().startsWith(quoteWrapping.quoteString)) {
      int offset = suffix.getTextOffset();
      document.deleteString(offset, offset + 1);
      context.commitDocument();
    }
    if (caretInsideQuotes()) {
      context.getEditor().getCaretModel().moveCaretRelatively(-1, 0, false, false, true);
    }
  }

  /**
   * If true, and we're wrapping with quotes, the caret is moved inside the closing quote after the
   * insert operation is performed.
   */
  protected boolean caretInsideQuotes() {
    return false;
  }
}
