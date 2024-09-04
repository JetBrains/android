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
package com.google.idea.blaze.base.lang.projectview.completion;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.lang.projectview.language.ProjectViewLanguage;
import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewTokenType;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.Sections;
import com.intellij.codeInsight.completion.AutoCompletionContext;
import com.intellij.codeInsight.completion.AutoCompletionDecision;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import java.util.List;
import javax.annotation.Nullable;

/** Completes project view section names. */
public class ProjectViewKeywordCompletionContributor extends CompletionContributor {

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    // auto-insert the obvious only case; else show other cases.
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      return AutoCompletionDecision.insertItem(items[0]);
    }
    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  public ProjectViewKeywordCompletionContributor() {
    extend(
        CompletionType.BASIC,
        psiElement()
            .withLanguage(ProjectViewLanguage.INSTANCE)
            .withElementType(ProjectViewTokenType.IDENTIFIERS)
            .andOr(psiElement().afterLeaf("\n"), psiElement().afterLeaf(psiElement().isNull())),
        new CompletionProvider<CompletionParameters>() {
          @Override
          protected void addCompletions(
              CompletionParameters parameters,
              ProcessingContext context,
              CompletionResultSet result) {
            result.addAllElements(keywordLookups);
          }
        });
  }

  private static final List<LookupElement> keywordLookups = getLookups();

  private static List<LookupElement> getLookups() {
    ImmutableList.Builder<LookupElement> list = ImmutableList.builder();
    for (SectionParser parser : Sections.getUndeprecatedParsers()) {
      if (handledSectionType(parser)) {
        list.add(forSectionParser(parser));
      }
    }
    return list.build();
  }

  @VisibleForTesting
  public static boolean handledSectionType(SectionParser parser) {
    return parser instanceof ListSectionParser || parser instanceof ScalarSectionParser;
  }

  private static LookupElement forSectionParser(SectionParser parser) {
    return LookupElementBuilder.create(parser.getName()).withInsertHandler(insertDivider(parser));
  }

  private static InsertHandler<LookupElement> insertDivider(SectionParser parser) {
    return (context, item) -> {
      Editor editor = context.getEditor();
      Document document = editor.getDocument();
      context.commitDocument();

      String nextTokenText = findNextTokenText(context);
      if (nextTokenText == null || nextTokenText.equals("\n")) {
        document.insertString(context.getTailOffset(), getDivider(parser));
        editor.getCaretModel().moveToOffset(context.getTailOffset());
      }
    };
  }

  private static String getDivider(SectionParser parser) {
    if (parser instanceof ListSectionParser) {
      return ":\n  ";
    }
    char div = ((ScalarSectionParser) parser).getDivider();
    return div == ' ' ? String.valueOf(div) : (div + " ");
  }

  @Nullable
  private static String findNextTokenText(final InsertionContext context) {
    final PsiFile file = context.getFile();
    PsiElement element = file.findElementAt(context.getTailOffset());
    while (element != null && element.getTextLength() == 0) {
      ASTNode next = element.getNode().getTreeNext();
      element = next != null ? next.getPsi() : null;
    }
    return element != null ? element.getText() : null;
  }
}
