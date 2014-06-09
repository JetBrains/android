/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.inspections.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.detector.api.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;

import java.util.Map;

public class IntellijGradleDetector extends GradleDetector {
  static final Implementation IMPLEMENTATION = new Implementation(
    IntellijGradleDetector.class,
    Scope.GRADLE_SCOPE);

  @Nullable
  protected String getClosureName(@NonNull GrClosableBlock closure) {
    if (closure.getParent() instanceof GrMethodCall) {
      GrMethodCall parent = (GrMethodCall)closure.getParent();
      if (parent.getInvokedExpression() instanceof GrReferenceExpression) {
        GrReferenceExpression invokedExpression = (GrReferenceExpression)(parent.getInvokedExpression());
        if (invokedExpression.getDotToken() == null) {
          return invokedExpression.getReferenceName();
        }
      }
    }

    return null;
  }

  @Override
  public void visitBuildScript(@NonNull final Context context, Map<String, Object> sharedData) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final PsiFile psiFile = IntellijLintUtils.getPsiFile(context);
        if (!(psiFile instanceof GroovyFile)) {
          return;
        }
        GroovyFile groovyFile = (GroovyFile)psiFile;
        groovyFile.accept(new GroovyRecursiveElementVisitor() {
          @Override
          public void visitClosure(GrClosableBlock closure) {
            String parentName = getClosureName(closure);
            String parentParentName = null;
            if (parentName != null) {
              GrClosableBlock block = PsiTreeUtil.getParentOfType(closure, GrClosableBlock.class, true);
              if (block != null) {
                parentParentName = getClosureName(block);
              }
            }
            if (parentName != null && isInterestingBlock(parentName, parentParentName)) {
              for (PsiElement element : closure.getChildren()) {
                if (element instanceof GrApplicationStatement) {
                  GrApplicationStatement call = (GrApplicationStatement)element;
                  GrExpression propertyExpression = call.getInvokedExpression();
                  GrCommandArgumentList argumentList = call.getArgumentList();
                  if (propertyExpression instanceof GrReferenceExpression) {
                    GrReferenceExpression propertyRef = (GrReferenceExpression)propertyExpression;
                    String property = propertyRef.getReferenceName();
                    if (property != null && isInterestingProperty(property, parentName, parentParentName)) {
                      String value = argumentList.getText();
                      checkDslPropertyAssignment(context, property, value, parentName, parentParentName, argumentList);
                    }
                  }
                } else if (element instanceof GrAssignmentExpression) {
                  GrAssignmentExpression assignment = (GrAssignmentExpression)element;
                  GrExpression lValue = assignment.getLValue();
                  if (lValue instanceof GrReferenceExpression) {
                    GrReferenceExpression propertyRef = (GrReferenceExpression)lValue;
                    String property = propertyRef.getReferenceName();
                    if (property != null && isInterestingProperty(property, parentName, parentParentName)) {
                      GrExpression rValue = assignment.getRValue();
                      if (rValue != null) {
                        String value = rValue.getText();
                        checkDslPropertyAssignment(context, property, value, parentName, parentParentName, rValue);
                      }
                    }
                  }
                }
              }
            }
            super.visitClosure(closure);
          }
        });
      }
    });
  }

  @Override
  protected int getStartOffset(@NonNull Context context, @NonNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    TextRange textRange = element.getTextRange();
    return textRange.getStartOffset();
  }

  @NonNull
  @Override
  protected Object getPropertyPairCookie(@NonNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    return element.getParent();
  }

  @NonNull
  @Override
  protected Object getPropertyKeyCookie(@NonNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    PsiElement parent = element.getParent();
    if (parent instanceof GrApplicationStatement) {
      GrApplicationStatement call = (GrApplicationStatement)parent;
      return call.getInvokedExpression();
    } else if (parent instanceof GrAssignmentExpression) {
      GrAssignmentExpression assignment = (GrAssignmentExpression)parent;
      return assignment.getLValue();
    }

    return super.getPropertyKeyCookie(cookie);
  }

  @Override
  protected Location createLocation(@NonNull Context context, @NonNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    TextRange textRange = element.getTextRange();
    int start = textRange.getStartOffset();
    int end = textRange.getEndOffset();
    return Location.create(context.file, new DefaultPosition(-1, -1, start), new DefaultPosition(-1, -1, end));
  }
}
