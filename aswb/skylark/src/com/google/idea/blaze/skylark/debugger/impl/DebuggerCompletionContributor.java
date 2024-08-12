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
package com.google.idea.blaze.skylark.debugger.impl;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;

import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.Value;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.intellij.codeInsight.completion.AutoCompletionContext;
import com.intellij.codeInsight.completion.AutoCompletionDecision;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import java.util.List;

/**
 * A completion contributor specific to the skylark debugger's evaluation dialog. In this context,
 * we have complete knowledge of the available bindings, so don't need to rely on heuristics or
 * imperfect plugin-side parsing of the file.
 */
class DebuggerCompletionContributor extends CompletionContributor {

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    // auto-insert iff there's only one suggestion
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      return AutoCompletionDecision.insertItem(items[0]);
    }
    return AutoCompletionDecision.SHOW_LOOKUP;
  }

  DebuggerCompletionContributor() {
    extend(
        CompletionType.BASIC,
        psiElement()
            .withLanguage(BuildFileLanguage.INSTANCE)
            .inFile(psiFile(SkylarkExpressionCodeFragment.class)),
        new EvaluationContextCompletionProvider());
  }

  private static class EvaluationContextCompletionProvider
      extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(
        CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
      PsiFile file = parameters.getOriginalFile();
      SkylarkSourcePosition debugContext = getDebugContext(file);
      if (debugContext == null) {
        return;
      }
      String text = file.getText();
      List<Value> suggestions =
          SkylarkDebugCompletionSuggestions.create(debugContext).getCompletionValues(text);
      if (suggestions.isEmpty()) {
        return;
      }
      suggestions.forEach(
          value ->
              result.addElement(
                  LookupElementBuilder.create(value.getLabel())
                      .withIcon(SkylarkDebugValue.getIcon(value))));
    }
  }

  private static SkylarkSourcePosition getDebugContext(PsiFile file) {
    return file instanceof SkylarkExpressionCodeFragment
        ? ((SkylarkExpressionCodeFragment) file).debugEvaluationContext
        : null;
  }
}
