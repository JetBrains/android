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

import android.databinding.tool.reflection.ModelField;
import android.databinding.tool.reflection.ModelMethod;
import com.android.ide.common.res2.DataBindingResourceType;
import com.android.tools.idea.databinding.BrUtil;
import com.android.tools.idea.lang.databinding.DataBindingXmlReferenceContributor.ResolvesToModelClass;
import com.android.tools.idea.lang.databinding.model.PsiModelClass;
import com.android.tools.idea.lang.databinding.model.PsiModelField;
import com.android.tools.idea.lang.databinding.model.PsiModelMethod;
import com.android.tools.idea.lang.databinding.psi.PsiDbDotExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbMethodExpr;
import com.android.tools.idea.rendering.DataBindingInfo;
import com.android.tools.idea.rendering.PsiDataBindingResourceItem;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
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
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getOriginalPosition();
        // During first invocation, only suggest valid options. During subsequent invocations, also suggest invalid options
        // such as private members or instance methods on class objects.
        boolean fullCompletion = parameters.getInvocationCount() > 1;
        if (position == null) {
          position = parameters.getPosition();
        }
        PsiElement parent = position.getParent();
        PsiReference[] references = parent.getReferences();
        boolean usingGrandparent = false;
        if (references.length == 0) {
          // try to replace parent
          PsiElement grandParent = parent.getParent();
          if (grandParent instanceof PsiDbDotExpr) {
            parent = ((PsiDbDotExpr) grandParent).getExpr();
          } else if (grandParent instanceof PsiDbMethodExpr) {
            parent = ((PsiDbMethodExpr) grandParent).getExpr();
          } else if (grandParent instanceof DbFile) {
            autoCompleteVariables((DbFile)grandParent, result);
            // TODO: add completions for packages and java.lang classes.
            return;
          } else {
            // grandparent not recognized. don't know how to provide completions.
            return;
          }
          references = parent.getReferences();
          usingGrandparent = true;
        }
        for (PsiReference reference : references) {
          if (reference instanceof ResolvesToModelClass) {
            ResolvesToModelClass ref = (ResolvesToModelClass)reference;
            boolean staticRef = ref.isStatic();
            PsiModelClass resolvedType = ref.getResolvedType();
            if (resolvedType == null) {
              return;
            }
            for (ModelField modelField : resolvedType.getDeclaredFields()) {
              PsiModelField psiModelField = (PsiModelField)modelField;
              if (!fullCompletion && (!psiModelField.isPublic() || staticRef && !psiModelField.isStatic())) {
                continue;
              }
              if (usingGrandparent) {
                result.addElement(LookupElementBuilder.create(psiModelField.getPsiField()));
              } else {
                result.addElement(LookupElementBuilder.create(psiModelField.getPsiField(),
                                                              parent.getText() + "." + psiModelField.getName()));
              }
            }
            for (ModelMethod modelMethod : resolvedType.getDeclaredMethods()) {
              PsiModelMethod psiModelMethod = (PsiModelMethod)modelMethod;
              if (!fullCompletion && !psiModelMethod.isPublic()) {
                continue;
              }
              if (psiModelMethod.isVoid()) {
                continue;
              }
              PsiMethod psiMethod = psiModelMethod.getPsiMethod();
              if (psiMethod.isConstructor()) {
                continue;
              }
              if (!fullCompletion && staticRef && !psiModelMethod.isStatic()) {
                continue;
              }
              String name = psiModelMethod.getName() + "()";
              if (BrUtil.isGetter(psiMethod)) {
                name = StringUtil.decapitalize(psiModelMethod.getName().substring(3));
              } else if (BrUtil.isBooleanGetter(psiMethod)) {
                name = StringUtil.decapitalize(psiModelMethod.getName().substring(2));
              }
              if (usingGrandparent) {
                result.addElement(LookupElementBuilder.create(psiMethod, name));
              } else {
                result.addElement(LookupElementBuilder.create(psiMethod, parent.getText() + "." + name));
              }
            }
          }
        }
      }
    });
  }

  private static void autoCompleteVariables(DbFile file, CompletionResultSet result) {
    DataBindingInfo dataBindingInfo = DataBindingXmlReferenceContributor.getDataBindingInfo(file);
    if (dataBindingInfo == null) {
      return;
    }
    for (PsiDataBindingResourceItem resourceItem : dataBindingInfo.getItems(DataBindingResourceType.VARIABLE)) {
      result.addElement(LookupElementBuilder.create(resourceItem.getXmlTag(), resourceItem.getName()));
    }
  }
}
