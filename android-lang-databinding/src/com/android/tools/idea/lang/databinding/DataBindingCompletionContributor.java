/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lang.databinding;

import android.databinding.tool.expr.ExprModel;
import android.databinding.tool.reflection.ModelField;
import android.databinding.tool.reflection.ModelMethod;
import com.android.annotations.NonNull;
import com.android.ide.common.resources.DataBindingResourceType;
import com.android.tools.idea.databinding.BrUtil;
import com.android.tools.idea.lang.databinding.DataBindingXmlReferenceContributor.ResolvesToModelClass;
import com.android.tools.idea.lang.databinding.model.PsiModelClass;
import com.android.tools.idea.lang.databinding.model.PsiModelField;
import com.android.tools.idea.lang.databinding.model.PsiModelMethod;
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * This handles completion in the data binding expressions (inside {@code @{}}).
 * <p/>
 * Completion for everything under {@code <data>} tag is in
 * {@link org.jetbrains.android.AndroidCompletionContributor#completeDataBindingTypeAttr}.
 */
public class DataBindingCompletionContributor extends CompletionContributor {
  public DataBindingCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        // During first invocation, only suggest valid options. During subsequent invocations, also suggest invalid options
        // such as private members or instance methods on class objects.
        boolean onlyValidCompletions = parameters.getInvocationCount() <= 1;

        PsiElement position = parameters.getOriginalPosition();
        if (position == null) {
          position = parameters.getPosition();
        }

        PsiElement parent = position.getParent();
        if (parent.getReferences().length == 0) {
          // try to replace parent
          PsiElement grandParent = parent.getParent();
          PsiDbExpr ownerExpr;
          if (grandParent instanceof PsiDbCallExpr) {
            // TODO(b/122895499): add tests for this branch
            ownerExpr = ((PsiDbCallExpr)grandParent).getRefExpr();
            result.addAllElements(populateFieldReferenceCompletions(ownerExpr, onlyValidCompletions));
            result.addAllElements(populateMethodReferenceCompletions(ownerExpr, onlyValidCompletions));
          }
          else if (grandParent instanceof PsiDbRefExpr) {
            ownerExpr = ((PsiDbRefExpr)grandParent).getExpr();
            if (ownerExpr == null) {
              autoCompleteVariablesAndUnqualifiedFunctions(getFile(grandParent), result);
              return;
            }
            result.addAllElements(populateFieldReferenceCompletions(ownerExpr, onlyValidCompletions));
            result.addAllElements(populateMethodReferenceCompletions(ownerExpr, onlyValidCompletions));
          }
          else if (grandParent instanceof DbFile) {
            // TODO(b/122895499): add tests for this branch
            autoCompleteVariablesAndUnqualifiedFunctions((DbFile)grandParent, result);
            // TODO: add completions for packages and java.lang classes.
          }
          else if (grandParent instanceof PsiDbFunctionRefExpr) {
            result.addAllElements(
              populateMethodReferenceCompletionsForMethodBinding(((PsiDbFunctionRefExpr)grandParent).getExpr(), onlyValidCompletions));
          }
        }
        else {
          // TODO(b/122895499): add tests for this branch
          result.addAllElements(populateFieldReferenceCompletions(parent, onlyValidCompletions));
          result.addAllElements(populateMethodReferenceCompletions(parent, onlyValidCompletions));
        }
      }
    });
  }

  @NotNull
  private static DbFile getFile(@NonNull PsiElement element) {
    while (!(element instanceof DbFile)) {
      PsiElement parent = element.getParent();
      if (parent == null) {
        throw new IllegalArgumentException();
      }
      element = parent;
    }
    return (DbFile)element;
  }

  private static void autoCompleteVariablesAndUnqualifiedFunctions(@NonNull DbFile file, @NonNull CompletionResultSet result) {
    autoCompleteUnqualifiedFunctions(result);

    DataBindingInfo dataBindingInfo = DataBindingLangUtil.getDataBindingInfo(file);
    if (dataBindingInfo == null) {
      return;
    }
    for (PsiDataBindingResourceItem resourceItem : dataBindingInfo.getItems(DataBindingResourceType.VARIABLE).values()) {
      result.addElement(LookupElementBuilder.create(resourceItem.getXmlTag(), resourceItem.getName()));
    }
  }

  private static void autoCompleteUnqualifiedFunctions(@NonNull CompletionResultSet result) {
    final LookupElement item = LookupElementBuilder.create(ExprModel.SAFE_UNBOX_METHOD_NAME);
    result.addElement(item);
  }

  /**
   * Given a data binding expression, return a list of {@link LookupElement} which are the field references of the given expression.
   * If onlyValidCompletions is false, private and mismatched context fields are also suggested.
   */
  private List<LookupElement> populateFieldReferenceCompletions(@NotNull PsiElement referenceExpression, Boolean onlyValidCompletions) {
    ImmutableList.Builder<LookupElement> resultBuilder = new ImmutableList.Builder<>();
    PsiReference[] childReferences = referenceExpression.getReferences();
    for (PsiReference reference : childReferences) {
      ResolvesToModelClass ref = (ResolvesToModelClass)reference;
      PsiModelClass resolvedType = ref.getResolvedType();
      if (resolvedType != null) {
        for (ModelField modelField : resolvedType.getDeclaredFields()) {
          PsiModelField psiModelField = (PsiModelField)modelField;
          if (onlyValidCompletions) {
            if (!psiModelField.isPublic() || ref.isStatic() && !psiModelField.isStatic()) {
              continue;
            }
          }
          resultBuilder.add(LookupElementBuilder.create(psiModelField.getPsiField()));
        }
      }
    }
    return resultBuilder.build();
  }

  private List<LookupElement> populateMethodReferenceCompletions(@NotNull PsiElement referenceExpression, Boolean onlyValidCompletion) {
    return populateMethodReferenceCompletions(referenceExpression, onlyValidCompletion, true);
  }

  private List<LookupElement> populateMethodReferenceCompletionsForMethodBinding(@NotNull PsiElement referenceExpression,
                                                                                 Boolean onlyValidCompletion) {
    return populateMethodReferenceCompletions(referenceExpression, onlyValidCompletion, false);
  }

  /**
   * Given a data binding expression, return a list of {@link LookupElement} which are method references of the given expression.
   * If onlyValidCompletions is false, private and mismatched context fields are also suggested.
   */
  private List<LookupElement> populateMethodReferenceCompletions(@NotNull PsiElement referenceExpression,
                                                                 Boolean onlyValidCompletions,
                                                                 Boolean completeBrackets) {
    ImmutableList.Builder<LookupElement> resultBuilder = new ImmutableList.Builder<>();
    PsiReference[] childReferences = referenceExpression.getReferences();
    for (PsiReference reference : childReferences) {
      if (reference instanceof ResolvesToModelClass) {
        ResolvesToModelClass ref = (ResolvesToModelClass)reference;
        PsiModelClass resolvedType = ref.getResolvedType();
        if (resolvedType == null) {
          continue;
        }
        for (ModelMethod modelMethod : resolvedType.getDeclaredMethods()) {
          PsiModelMethod psiModelMethod = (PsiModelMethod)modelMethod;
          PsiMethod psiMethod = psiModelMethod.getPsiMethod();
          if (psiMethod.isConstructor()) {
            continue;
          }
          if (onlyValidCompletions) {
            if (ref.isStatic() != psiModelMethod.isStatic() || !psiModelMethod.isPublic()) {
              continue;
            }
          }
          String name = psiModelMethod.getName();
          if (completeBrackets) {
            name += "()";
            if (BrUtil.isGetter(psiMethod)) {
              name = StringUtil.decapitalize(psiModelMethod.getName().substring(3));
            }
            else if (BrUtil.isBooleanGetter(psiMethod)) {
              name = StringUtil.decapitalize(psiModelMethod.getName().substring(2));
            }
          }
          resultBuilder.add(LookupElementBuilder.create(psiMethod, name));
        }
      }
    }
    return resultBuilder.build();
  }
}
