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
package com.google.idea.blaze.base.lang.buildfile.search;

import com.google.idea.blaze.base.lang.buildfile.psi.AssignmentStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.ForStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadedSymbol;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.StatementList;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.Processor;
import javax.annotation.Nullable;

/** Utilities methods for resolving references */
public class ResolveUtil {

  /** Walks up PSI tree of local file, checking PsiNamedElements */
  public static void searchInScope(PsiElement originalElement, Processor<BuildElement> processor) {
    // TODO: Handle list comprehension (where variable is defined *later* in the code)
    boolean topLevelScope = true;
    PsiElement element = originalElement;
    while (!(element instanceof PsiFileSystemItem)) {
      PsiElement parent = element.getParent();
      if (parent instanceof BuildFile) {
        if (!((BuildFile) parent).searchSymbolsInScope(processor, topLevelScope ? element : null)) {
          return;
        }
      } else if (parent instanceof FunctionStatement) {
        topLevelScope = false;
        for (Parameter param : ((FunctionStatement) parent).getParameters()) {
          if (!processor.process(param)) {
            return;
          }
        }
      } else if (parent instanceof ForStatement) {
        for (Expression expr : ((ForStatement) parent).getForLoopVariables()) {
          if (expr instanceof TargetExpression && !processor.process(expr)) {
            return;
          }
        }
      } else if (parent instanceof StatementList) {
        if (!visitChildAssignmentStatements((BuildElement) parent, processor)) {
          return;
        }
      }
      element = parent;
    }
  }

  /** Walks up PSI tree of local file, checking PsiNamedElements */
  @Nullable
  public static PsiNamedElement findInScope(PsiElement element, String name) {
    PsiNamedElement[] resultHolder = new PsiNamedElement[1];
    Processor<BuildElement> processor =
        buildElement -> {
          if (buildElement == element) {
            return true;
          }
          if (buildElement instanceof PsiNamedElement && name.equals(buildElement.getName())) {
            resultHolder[0] = (PsiNamedElement) buildElement;
            return false;
          } else if (buildElement instanceof LoadedSymbol) {
            LoadedSymbol loadedSymbol = (LoadedSymbol) buildElement;
            if (name.equals(loadedSymbol.getSymbolString())) {
              PsiElement referencedElement = loadedSymbol.getVisibleElement();
              if (referencedElement instanceof PsiNamedElement) {
                resultHolder[0] = (PsiNamedElement) referencedElement;
                return false;
              }
            }
          }
          return true;
        };
    searchInScope(element, processor);
    return resultHolder[0];
  }

  /** @return false if processing was stopped */
  public static boolean visitChildAssignmentStatements(
      BuildElement parent, Processor<BuildElement> processor) {
    for (AssignmentStatement stmt : parent.childrenOfClass(AssignmentStatement.class)) {
      TargetExpression target = stmt.getLeftHandSideExpression();
      if (target != null && !processor.process(target)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if the element we're searching for is represented by a file or directory.<br>
   * e.g. a java class PSI element, or an actual PsiFile element.
   */
  @Nullable
  public static PsiFileSystemItem asFileSystemItemSearch(PsiElement elementToSearch) {
    if (elementToSearch instanceof PsiFileSystemItem) {
      return (PsiFileSystemItem) elementToSearch;
    }
    return asFileSearch(elementToSearch);
  }

  /**
   * Checks if the element we're searching for is represented by a file.<br>
   * e.g. a java class PSI element, or an actual PsiFile element.
   */
  @Nullable
  public static PsiFile asFileSearch(PsiElement elementToSearch) {
    if (elementToSearch instanceof PsiFile) {
      return (PsiFile) elementToSearch;
    }
    for (PsiFileProvider provider : PsiFileProvider.EP_NAME.getExtensions()) {
      PsiFile file = provider.asFileSearch(elementToSearch);
      if (file != null) {
        return file;
      }
    }
    return null;
  }
}
