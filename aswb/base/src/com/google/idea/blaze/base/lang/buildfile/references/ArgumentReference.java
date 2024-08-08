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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.completion.NamedBuildLookupElement;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Only keyword arguments resolve, but we include this class for code completion purposes. As the
 * user is typing a keyword arg, they'll start with a positional arg element.
 */
public class ArgumentReference<T extends Argument> extends PsiReferenceBase<T> {

  public ArgumentReference(T element, TextRange rangeInElement, boolean soft) {
    super(element, rangeInElement, false);
  }

  @Nullable
  protected FunctionStatement resolveFunction() {
    FuncallExpression call = PsiTreeUtil.getParentOfType(myElement, FuncallExpression.class);
    if (call == null) {
      return null;
    }
    PsiElement callee = call.getReferencedElement();
    return callee instanceof FunctionStatement ? (FunctionStatement) callee : null;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return null;
  }

  @Override
  public Object[] getVariants() {
    FunctionStatement function = resolveFunction();
    if (function == null) {
      return EMPTY_ARRAY;
    }
    List<LookupElement> params = Lists.newArrayList();
    for (Parameter param : function.getParameters()) {
      params.add(new NamedBuildLookupElement(param, QuoteType.NoQuotes));
    }
    return params.toArray();
  }
}
