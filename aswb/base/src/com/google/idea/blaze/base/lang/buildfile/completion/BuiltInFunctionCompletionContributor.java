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

import static com.intellij.patterns.PlatformPatterns.psiComment;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuiltInNamesProvider;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.livetemplates.RulesTemplates;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFileWithCustomCompletion;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StatementList;
import com.intellij.codeInsight.completion.AutoCompletionContext;
import com.intellij.codeInsight.completion.AutoCompletionDecision;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ProcessingContext;
import icons.BlazeIcons;
import javax.annotation.Nullable;

/** Completes built-in blaze function names. */
public class BuiltInFunctionCompletionContributor extends CompletionContributor {

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    // auto-insert the obvious only case; else show other cases.
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      return AutoCompletionDecision.insertItem(items[0]);
    }
    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  public BuiltInFunctionCompletionContributor() {
    extend(
        CompletionType.BASIC,
        psiElement()
            .withLanguage(BuildFileLanguage.INSTANCE)
            .andNot(psiElement().afterLeaf(psiElement(BuildToken.fromKind(TokenKind.INT))))
            .andNot(psiComment())
            .andOr(
                // Handles only top-level rules, and rules inside a function statement.
                // There are several other possibilities (e.g. inside top-level list comprehension),
                // but leaving out less common cases to avoid cluttering the autocomplete
                // suggestions when it's not valid to enter a rule.
                psiElement()
                    .withParents(
                        ReferenceExpression.class,
                        BuildFile.class), // leaf node => BuildReference => BuildFile
                psiElement()
                    .inside(
                        psiElement(StatementList.class).inside(psiElement(FunctionStatement.class)))
                    .afterLeaf(
                        psiElement().withText(".").afterLeaf(psiElement().withText("native"))))
            .andNot(psiElement().inFile(psiFile(BuildFileWithCustomCompletion.class))),
        new CompletionProvider<CompletionParameters>() {
          @Override
          protected void addCompletions(
              CompletionParameters parameters,
              ProcessingContext context,
              CompletionResultSet result) {
            Project project = parameters.getPosition().getProject();
            ImmutableSet<String> builtInNames =
                BuiltInNamesProvider.getBuiltInFunctionNames(project);
            BuildLanguageSpec spec =
                BuildLanguageSpecProvider.getInstance(project).getLanguageSpec();
            for (String ruleName : builtInNames) {
              result.addElement(
                  LookupElementBuilder.create(ruleName)
                      .withIcon(BlazeIcons.BuildRule)
                      .withInsertHandler(getInsertHandler(ruleName, spec)));
            }
          }
        });
  }

  private static InsertHandler<LookupElement> getInsertHandler(
      String ruleName, @Nullable BuildLanguageSpec spec) {
    if (spec == null) {
      return ParenthesesInsertHandler.getInstance(true);
    }
    return RulesTemplates.templateForRule(ruleName, spec)
        .map(BuiltInFunctionCompletionContributor::createTemplateInsertHandler)
        .orElse(ParenthesesInsertHandler.getInstance(true));
  }

  private static InsertHandler<LookupElement> createTemplateInsertHandler(Template t) {
    return (context, item) ->
        TemplateManager.getInstance(context.getProject()).startTemplate(context.getEditor(), t);
  }
}
