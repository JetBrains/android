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

import static com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.DATA_BINDING_CONTEXT_LAMBDA;
import static com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.DATA_BINDING_CONTEXT_METHOD_REFERENCE;
import static com.google.wireless.android.sdk.stats.DataBindingEvent.DataBindingContext.UNKNOWN_CONTEXT;
import static com.google.wireless.android.sdk.stats.DataBindingEvent.EventType.DATA_BINDING_COMPLETION_ACCEPTED;
import static com.google.wireless.android.sdk.stats.DataBindingEvent.EventType.DATA_BINDING_COMPLETION_SUGGESTED;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.DataBindingResourceType;
import com.android.tools.idea.databinding.BrUtil;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.databinding.analytics.api.DataBindingTracker;
import com.android.tools.idea.lang.databinding.model.ModelClassResolvable;
import com.android.tools.idea.lang.databinding.model.PsiModelClass;
import com.android.tools.idea.lang.databinding.model.PsiModelField;
import com.android.tools.idea.lang.databinding.model.PsiModelMethod;
import com.android.tools.idea.lang.databinding.psi.PsiDbExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr;
import com.android.tools.idea.res.DataBindingLayoutInfo;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.ProcessingContext;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * This handles completion in the data binding expressions (inside {@code @{}}).
 * <p/>
 * Completion for everything under {@code <data>} tag is in
 * {@link org.jetbrains.android.AndroidXmlCompletionContributor#completeDataBindingTypeAttr}.
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

        DataBindingTracker tracker = DataBindingTracker.getInstance(parameters.getEditor().getProject());

        PsiElement position = parameters.getOriginalPosition();
        if (position == null) {
          position = parameters.getPosition();
        }

        // The position is a PsiElement identifier. Its parent is a PsiDbId, which is a child of the overall expression, whose type we use
        // to choose what kind of completion logic to carry out.
        // For example user types @{model::g<caret>}. Position is the LeafPsiElement "g". Parent is the PsiDbId "g". Grandparent is the
        // whole expression "model:g".
        PsiElement parent = position.getParent();
        if (parent.getReferences().length == 0) {
          // try to replace parent
          PsiElement grandParent = parent.getParent();
          PsiDbExpr ownerExpr;
          if (grandParent instanceof PsiDbRefExpr) {
            ownerExpr = ((PsiDbRefExpr)grandParent).getExpr();
            if (ownerExpr == null) {
              autoCompleteVariablesAndUnqualifiedFunctions(getFile(grandParent), result);
              return;
            }
            result.addAllElements(populateFieldReferenceCompletions(ownerExpr, onlyValidCompletions));
            result.addAllElements(populateMethodReferenceCompletions(ownerExpr, onlyValidCompletions));
            tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, DATA_BINDING_CONTEXT_LAMBDA);
          }
          else if (grandParent instanceof PsiDbFunctionRefExpr) {
            result.addAllElements(
              populateMethodReferenceCompletionsForMethodBinding(((PsiDbFunctionRefExpr)grandParent).getExpr(), onlyValidCompletions));
            tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, DATA_BINDING_CONTEXT_METHOD_REFERENCE);
          }
        }
        else {
          //TODO(b/129497876): improve completion experience for variables and static functions
          result.addAllElements(populateFieldReferenceCompletions(parent, onlyValidCompletions));
          result.addAllElements(populateMethodReferenceCompletions(parent, onlyValidCompletions));
          tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, UNKNOWN_CONTEXT);
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

    DataBindingLayoutInfo dataBindingLayoutInfo = DataBindingLangUtil.getDataBindingLayoutInfo(file);
    if (dataBindingLayoutInfo == null) {
      return;
    }
    for (PsiDataBindingResourceItem resourceItem : dataBindingLayoutInfo.getItems(DataBindingResourceType.VARIABLE).values()) {
      LookupElementBuilder elementBuilder = LookupElementBuilder.create(resourceItem.getXmlTag(),
                                                                        DataBindingUtil.convertToJavaFieldName(resourceItem.getName()));
      result.addElement(attachTracker(elementBuilder));
    }
  }

  private static void autoCompleteUnqualifiedFunctions(@NonNull CompletionResultSet result) {
    final LookupElement item = attachTracker(LookupElementBuilder.create("safeUnbox"));
    result.addElement(item);
  }

  /**
   * Given a data binding expression, return a list of {@link LookupElement} which are the field references of the given expression.
   * If onlyValidCompletions is false, private and mismatched context fields are also suggested.
   */
  private static List<LookupElement> populateFieldReferenceCompletions(@NotNull PsiElement referenceExpression,
                                                                       boolean onlyValidCompletions) {
    ImmutableList.Builder<LookupElement> resultBuilder = new ImmutableList.Builder<>();
    PsiReference[] childReferences = referenceExpression.getReferences();
    for (PsiReference reference : childReferences) {
      ModelClassResolvable ref = (ModelClassResolvable)reference;
      PsiModelClass resolvedType = ref.getResolvedType();
      if (resolvedType != null) {
        resolvedType = resolvedType.getUnwrapped();
        for (PsiModelField psiModelField : resolvedType.getAllFields()) {
          if (onlyValidCompletions) {
            if (!psiModelField.isPublic() || ref.isStatic() && !psiModelField.isStatic()) {
              continue;
            }
          }
          LookupElementBuilder lookupBuilder = JavaLookupElementBuilder
            .forField(psiModelField.getPsiField(), StringUtil.notNullize(psiModelField.getPsiField().getName()),
                      psiModelField.getPsiField().getContainingClass())
            .withTypeText(
              PsiFormatUtil.formatVariable(psiModelField.getPsiField(), PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY));
          resultBuilder.add(attachTracker(lookupBuilder));
        }
      }
    }
    return resultBuilder.build();
  }

  private static List<LookupElement> populateMethodReferenceCompletions(@NotNull PsiElement referenceExpression,
                                                                        boolean onlyValidCompletion) {
    return populateMethodReferenceCompletions(referenceExpression, onlyValidCompletion, true);
  }

  private static List<LookupElement> populateMethodReferenceCompletionsForMethodBinding(@NotNull PsiElement referenceExpression,
                                                                                        boolean onlyValidCompletion) {
    return populateMethodReferenceCompletions(referenceExpression, onlyValidCompletion, false);
  }

  /**
   * Given a data binding expression, return a list of {@link LookupElement} which are method references of the given expression.
   * If onlyValidCompletions is false, private and mismatched context fields are also suggested.
   */
  private static List<LookupElement> populateMethodReferenceCompletions(@NotNull PsiElement referenceExpression,
                                                                        boolean onlyValidCompletions,
                                                                        boolean completeBrackets) {
    ImmutableList.Builder<LookupElement> resultBuilder = new ImmutableList.Builder<>();
    PsiReference[] childReferences = referenceExpression.getReferences();
    for (PsiReference reference : childReferences) {
      if (reference instanceof ModelClassResolvable) {
        ModelClassResolvable ref = (ModelClassResolvable)reference;
        PsiModelClass resolvedType = ref.getResolvedType();
        if (resolvedType == null) {
          continue;
        }
        resolvedType = resolvedType.getUnwrapped();
        for (PsiModelMethod psiModelMethod : resolvedType.getAllMethods()) {
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
          LookupElementBuilder lookupBuilder =
            JavaLookupElementBuilder.forMethod(psiMethod, name, PsiSubstitutor.EMPTY, psiMethod.getContainingClass());
          resultBuilder.add(attachTracker(lookupBuilder));
        }
      }
    }
    return resultBuilder.build();
  }

  @VisibleForTesting
  static LookupElement attachTracker(@NotNull LookupElementBuilder element) {
    // Attach a completion handler to each look up element so we can track when user accepts a suggestion.
    return element.withInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
        DataBindingTracker tracker = DataBindingTracker.getInstance(context.getProject());

        PsiElement childElement = context.getFile().findElementAt(context.getStartOffset());
        assert childElement != null;
        PsiElement grandParent = childElement.getParent().getParent();
        if (grandParent instanceof PsiDbFunctionRefExpr) {
          tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED, DATA_BINDING_CONTEXT_METHOD_REFERENCE);
        }
        else if (grandParent instanceof PsiDbRefExpr) {
          tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED, DATA_BINDING_CONTEXT_LAMBDA);
        }
        else {
          tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_ACCEPTED, UNKNOWN_CONTEXT);
        }
      }
    });
  }
}
