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

import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.google.idea.blaze.base.lang.buildfile.psi.ParameterList;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.util.ProcessingContext;

/** {@link CompletionContributor} for starred function parameters. */
public class ParameterCompletionContributor extends CompletionContributor {

  public ParameterCompletionContributor() {
    extend(
        CompletionType.BASIC,
        psiElement().inside(ParameterList.class).afterLeaf("*"),
        new ParameterCompletionProvider("args"));
    extend(
        CompletionType.BASIC,
        psiElement().inside(ParameterList.class).afterLeaf("**"),
        new ParameterCompletionProvider("kwargs"));
  }

  private static class ParameterCompletionProvider
      extends CompletionProvider<CompletionParameters> {
    private String myName;

    private ParameterCompletionProvider(String name) {
      myName = name;
    }

    @Override
    protected void addCompletions(
        CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
      result.addElement(LookupElementBuilder.create(myName).withIcon(AllIcons.Nodes.Parameter));
    }
  }
}
