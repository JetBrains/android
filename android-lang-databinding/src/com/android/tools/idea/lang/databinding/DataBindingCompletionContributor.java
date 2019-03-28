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

import android.databinding.tool.expr.ExprModel;
import android.databinding.tool.reflection.ModelField;
import android.databinding.tool.reflection.ModelMethod;
import com.android.annotations.NonNull;
import com.android.ide.common.resources.DataBindingResourceType;
import com.android.tools.idea.databinding.BrUtil;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.databinding.analytics.api.DataBindingTracker;
import com.android.tools.idea.lang.databinding.model.ModelClassResolvable;
import com.android.tools.idea.lang.databinding.model.PsiModelClass;
import com.android.tools.idea.lang.databinding.model.PsiModelField;
import com.android.tools.idea.lang.databinding.model.PsiModelMethod;
import com.android.tools.idea.lang.databinding.psi.PsiDbCallExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbFunctionRefExpr;
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr;
import com.android.tools.idea.res.DataBindingLayoutInfo;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.DataBindingEvent;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import java.util.HashSet;
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
        DataBindingEvent.DataBindingContext dataBindingContext = UNKNOWN_CONTEXT;

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
            dataBindingContext = DATA_BINDING_CONTEXT_LAMBDA;
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
            dataBindingContext = DATA_BINDING_CONTEXT_METHOD_REFERENCE;
            result.addAllElements(
              populateMethodReferenceCompletionsForMethodBinding(((PsiDbFunctionRefExpr)grandParent).getExpr(), onlyValidCompletions));
          }
        }
        else {
          // TODO(b/122895499): add tests for this branch
          result.addAllElements(populateFieldReferenceCompletions(parent, onlyValidCompletions));
          result.addAllElements(populateMethodReferenceCompletions(parent, onlyValidCompletions));
          tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, UNKNOWN_CONTEXT);
        }
        tracker.trackDataBindingCompletion(DATA_BINDING_COMPLETION_SUGGESTED, dataBindingContext);
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
      result.addElement(
        createTrackedLookupElement(resourceItem.getXmlTag(),
                                   DataBindingUtil.convertToJavaFieldName(resourceItem.getName())));
    }
  }

  private static void autoCompleteUnqualifiedFunctions(@NonNull CompletionResultSet result) {
    final LookupElement item = createTrackedLookupElement(ExprModel.SAFE_UNBOX_METHOD_NAME, ExprModel.SAFE_UNBOX_METHOD_NAME);
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
        for (ModelField modelField : resolvedType.getAllFields()) {
          PsiModelField psiModelField = (PsiModelField)modelField;
          if (onlyValidCompletions) {
            if (!psiModelField.isPublic() || ref.isStatic() && !psiModelField.isStatic()) {
              continue;
            }
          }
          resultBuilder
            .add(createTrackedLookupElement(psiModelField.getPsiField(), StringUtil.notNullize(psiModelField.getPsiField().getName())));
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
    HashSet<String> uniqueNames = new HashSet<>();
    for (PsiReference reference : childReferences) {
      if (reference instanceof ModelClassResolvable) {
        ModelClassResolvable ref = (ModelClassResolvable)reference;
        PsiModelClass resolvedType = ref.getResolvedType();
        if (resolvedType == null) {
          continue;
        }
        for (ModelMethod modelMethod : resolvedType.getAllMethods()) {
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

          // TODO(128618360): add parameters to code completion.
          if (uniqueNames.contains(name)) {
            continue;
          }
          uniqueNames.add(name);
          resultBuilder.add(createTrackedLookupElement(psiMethod, name));
        }
      }
    }
    return resultBuilder.build();
  }

  private static LookupElement createTrackedLookupElement(@NotNull Object lookupObject, @NotNull String lookupString) {
    // Attach a completion handler to each look up element so we can track when user accepts a suggestion.
    return LookupElementBuilder.create(lookupObject, lookupString).withInsertHandler(new InsertHandler<LookupElement>() {
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
