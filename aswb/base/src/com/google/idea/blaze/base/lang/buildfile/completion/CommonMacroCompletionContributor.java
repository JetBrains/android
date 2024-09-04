/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import static com.intellij.patterns.PlatformPatterns.psiComment;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.lang.buildfile.completion.CommonMacroContributor.CommonMacros;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFileWithCustomCompletion;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadedSymbol;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.codeInsight.completion.AutoCompletionContext;
import com.intellij.codeInsight.completion.AutoCompletionDecision;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import icons.BlazeIcons;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/** Completion support for very common macros which aren't yet loaded in the current file. */
public class CommonMacroCompletionContributor extends CompletionContributor {

  private static final BoolExperiment enabled =
      new BoolExperiment("build.macro.contributor.enabled", true);

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    // auto-insert the obvious only case; else show other cases.
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      return AutoCompletionDecision.insertItem(items[0]);
    }
    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  public CommonMacroCompletionContributor() {
    extend(
        CompletionType.BASIC,
        psiElement()
            .withLanguage(BuildFileLanguage.INSTANCE)
            .andNot(psiElement().afterLeaf(psiElement(BuildToken.fromKind(TokenKind.INT))))
            .andNot(psiComment())
            .and(
                // Handles only top-level rules.
                // There are several other possibilities (e.g. inside macros),
                // but leaving out less common cases to avoid cluttering the autocomplete
                // suggestions when it's not valid to enter a rule.
                psiElement()
                    .withParents(
                        ReferenceExpression.class,
                        BuildFile.class)) // leaf node => BuildReference => BuildFile
            .andNot(psiElement().inFile(psiFile(BuildFileWithCustomCompletion.class))),
        new CompletionProvider<CompletionParameters>() {
          @Override
          protected void addCompletions(
              CompletionParameters parameters,
              ProcessingContext context,
              CompletionResultSet result) {
            if (!enabled.getValue()) {
              return;
            }
            BuildFile file = getBuildFile(parameters.getPosition());
            if (file == null) {
              return;
            }
            ImmutableSet<String> existingSymbols = symbolsInScope(file);
            for (CommonMacros macro : CommonMacroContributor.getAllMacros()) {
              String packageLocation = macro.location();
              for (String symbol : macro.functionNames()) {
                if (existingSymbols.contains(symbol)) {
                  continue;
                }
                result.addElement(
                    LookupElementBuilder.create(symbol)
                        .withIcon(BlazeIcons.BuildRule)
                        .withInsertHandler(getInsertHandler(file, packageLocation, symbol)));
              }
            }
          }
        });
  }

  @Nullable
  private static BuildFile getBuildFile(PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file instanceof BuildFile ? ((BuildFile) file) : null;
  }

  /**
   * Returns all top-level assignment statements, function definitions and loaded symbols (ignoring
   * aliases).
   */
  private static ImmutableSet<String> symbolsInScope(BuildFile file) {
    Set<String> symbols = new HashSet<>();
    Processor<BuildElement> processor =
        buildElement -> {
          if (buildElement instanceof LoadedSymbol) {
            StringLiteral s = ((LoadedSymbol) buildElement).getImport();
            if (s != null) {
              symbols.add(s.getStringContents());
            }
          } else if (buildElement instanceof PsiNamedElement) {
            String name = buildElement.getName();
            if (name != null) {
              symbols.add(name);
            }
          }
          return true;
        };
    file.searchSymbolsInScope(processor, /* stopAtElement= */ null);
    return ImmutableSet.copyOf(symbols);
  }

  private static InsertHandler<LookupElement> getInsertHandler(
      BuildFile file, String packageLocation, String symbol) {
    ParenthesesInsertHandler<LookupElement> base =
        ParenthesesInsertHandler.getInstance(/* hasParameters= */ true);
    return (context, item) -> {
      base.handleInsert(context, item);
      insertLoadStatement(context, file, packageLocation, symbol);
    };
  }

  /**
   * Rough insertion of load statement somewhere near top of file, preferentially near other load
   * statements.
   *
   * <p>Doesn't try to combine with existing load statements with the same package (we've already
   * checked whether the symbol is already available).
   *
   * <p>TODO(brendandouglas): use buildozer instead.
   */
  private static void insertLoadStatement(
      InsertionContext context, BuildFile file, String packageLocation, String symbol) {
    EventLoggingService.getInstance()
        .logEvent(
            CommonMacroCompletionContributor.class,
            "completed",
            ImmutableMap.of(symbol, packageLocation));

    String text = String.format("load(\"%s\", \"%s\")\n", packageLocation, symbol);

    Document doc = context.getEditor().getDocument();
    PsiElement anchor = findAnchorElement(file);
    int lineNumber = anchor == null ? 0 : doc.getLineNumber(anchor.getTextRange().getStartOffset());
    int offset = doc.getLineStartOffset(lineNumber);
    doc.insertString(offset, text);
  }

  /**
   * Returns the element before which a new load statement should be placed, or null if it belongs
   * at the top of the file.
   */
  @Nullable
  private static PsiElement findAnchorElement(BuildFile file) {
    for (PsiElement child : file.getChildren()) {
      if (child instanceof LoadStatement || child instanceof PsiComment || isWhiteSpace(child)) {
        continue;
      }
      return child;
    }
    return null;
  }

  private static boolean isWhiteSpace(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      return true;
    }
    return BuildToken.WHITESPACE_AND_NEWLINE.contains(element.getNode().getElementType());
  }
}
