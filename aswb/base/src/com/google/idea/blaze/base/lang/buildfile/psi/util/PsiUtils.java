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
package com.google.idea.blaze.base.lang.buildfile.psi.util;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.psi.AssignmentStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.ParenthesizedExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import java.util.List;
import javax.annotation.Nullable;

/** Utility methods for working with PSI elements */
public class PsiUtils {

  public static ASTNode createNewName(Project project, String name) {
    return BuildElementGenerator.getInstance(project).createNameIdentifier(name);
  }

  public static ASTNode createNewLabel(Project project, String labelString) {
    return BuildElementGenerator.getInstance(project).createStringNode(labelString);
  }

  @Nullable
  public static PsiElement getPreviousNodeInTree(PsiElement element) {
    PsiElement prevSibling = null;
    while (element != null && (prevSibling = element.getPrevSibling()) == null) {
      element = element.getParent();
    }
    return prevSibling != null ? lastElementInSubtree(prevSibling) : null;
  }

  /** The last element in the tree rooted at the given element. */
  public static PsiElement lastElementInSubtree(PsiElement element) {
    PsiElement lastChild;
    while ((lastChild = element.getLastChild()) != null) {
      element = lastChild;
    }
    return element;
  }

  /**
   * Walks up PSI tree, looking for a parent of the specified class. Stops searching when it reaches
   * a parent of type PsiDirectory.
   */
  @Nullable
  public static <T extends PsiElement> T getParentOfType(
      PsiElement element, Class<T> psiClass, boolean strict) {
    element = strict ? element.getParent() : element;
    while (element != null && !(element instanceof PsiDirectory)) {
      if (psiClass.isInstance(element)) {
        return psiClass.cast(element);
      }
      element = element.getParent();
    }
    return null;
  }

  @Nullable
  public static <T extends PsiElement> T findFirstChildOfClassRecursive(
      PsiElement parent, Class<T> psiClass) {
    List<T> holder = Lists.newArrayListWithExpectedSize(1);
    Processor<T> getFirst =
        t -> {
          holder.add(t);
          return false;
        };
    processChildrenOfType(parent, getFirst, psiClass);
    return holder.isEmpty() ? null : holder.get(0);
  }

  @Nullable
  public static <T extends PsiElement> T findLastChildOfClassRecursive(
      PsiElement parent, Class<T> psiClass) {
    List<T> holder = Lists.newArrayListWithExpectedSize(1);
    Processor<T> getFirst =
        t -> {
          holder.add(t);
          return false;
        };
    processChildrenOfType(parent, getFirst, psiClass, true);
    return holder.isEmpty() ? null : holder.get(0);
  }

  public static <T extends PsiElement> List<T> findAllChildrenOfClassRecursive(
      PsiElement parent, Class<T> psiClass) {
    List<T> result = Lists.newArrayList();
    processChildrenOfType(parent, new CommonProcessors.CollectProcessor<>(result), psiClass);
    return result;
  }

  /**
   * Walk through entire PSI tree rooted at 'element', processing all children of the given type.
   *
   * @return true if processing was stopped by the processor
   */
  public static <T extends PsiElement> boolean processChildrenOfType(
      PsiElement element, Processor<T> processor, Class<T> psiClass) {
    return processChildrenOfType(element, processor, psiClass, false);
  }

  /**
   * Walk through entire PSI tree rooted at 'element', processing all children of the given type.
   *
   * @return true if processing was stopped by the processor
   */
  private static <T extends PsiElement> boolean processChildrenOfType(
      PsiElement element, Processor<T> processor, Class<T> psiClass, boolean reverseOrder) {
    PsiElement child = reverseOrder ? element.getLastChild() : element.getFirstChild();
    while (child != null) {
      if (psiClass.isInstance(child)) {
        if (!processor.process(psiClass.cast(child))) {
          return true;
        }
      }
      if (processChildrenOfType(child, processor, psiClass, reverseOrder)) {
        return true;
      }
      child = reverseOrder ? child.getPrevSibling() : child.getNextSibling();
    }
    return false;
  }

  public static TextRange childRangeInParent(TextRange parentRange, TextRange childRange) {
    return childRange.shiftRight(-parentRange.getStartOffset());
  }

  @Nullable
  public static String getFilePath(@Nullable PsiFile file) {
    VirtualFile virtualFile = file != null ? file.getVirtualFile() : null;
    return virtualFile != null ? virtualFile.getPath() : null;
  }

  /**
   * For ReferenceExpressions, follows the chain of references until it hits a
   * non-ReferenceExpression.<br>
   * Unwraps ParenthesizedExpression.<br>
   * For other types, returns the input expression.
   */
  private static PsiElement getReferencedTarget(Expression expr) {
    PsiElement element = expr;
    while (true) {
      PsiElement unwrapped = unwrap(element);
      if (unwrapped == null || unwrapped == element) {
        return element;
      }
      element = unwrapped;
    }
  }

  @Nullable
  private static PsiElement unwrap(PsiElement expr) {
    if (expr instanceof ParenthesizedExpression) {
      return ((ParenthesizedExpression) expr).getContainedExpression();
    }
    if (expr instanceof ReferenceExpression) {
      return ((ReferenceExpression) expr).getReferencedElement();
    }
    return expr;
  }

  /**
   * For ReferenceExpressions, follows the chain of references until it hits a
   * non-ReferenceExpression, then evaluates the value of that target. For other types, returns the
   * input expression.
   */
  public static PsiElement getReferencedTargetValue(Expression expr) {
    PsiElement element = getReferencedTarget(expr);
    if (element instanceof TargetExpression) {
      PsiElement parent = element.getParent();
      if (parent instanceof AssignmentStatement) {
        return ((AssignmentStatement) parent).getAssignedValue();
      }
    }
    return element;
  }
}
