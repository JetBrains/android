/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.groovy;

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.Collection;

public final class GroovyDslUtil {

  static GroovyPsiElement ensureGroovyPsi(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    if (element instanceof GroovyPsiElement) {
      return (GroovyPsiElement) element;
    }
    throw new IllegalArgumentException("Wrong PsiElement type for writer! Must be of type GoovyPsiElement");
  }

  static void addConfigBlock(@NotNull GradleDslLiteral literal) {
    PsiElement unsavedConfigBlock = literal.getUnsavedConfigBlock();
    if (unsavedConfigBlock == null) {
      return;
    }

    GroovyPsiElement psiElement = ensureGroovyPsi(literal.getPsiElement());
    if (psiElement == null) {
      return;
    }

    GroovyPsiElementFactory factory = getPsiElementFactory(literal);
    if (factory == null) {
      return;
    }

    // For now, this is only reachable for newly added dependencies, which means psiElement is an application statement with three children:
    // the configuration name, whitespace, dependency in compact notation. Let's add some more: comma, whitespace and finally the config
    // block.
    GrApplicationStatement methodCallStatement = (GrApplicationStatement)factory.createStatementFromText("foo 1, 2");
    PsiElement comma = methodCallStatement.getArgumentList().getFirstChild().getNextSibling();

    psiElement.addAfter(comma, psiElement.getLastChild());
    psiElement.addAfter(factory.createWhiteSpace(), psiElement.getLastChild());
    psiElement.addAfter(unsavedConfigBlock, psiElement.getLastChild());
    literal.setUnsavedConfigBlock(null);
  }

  @Nullable
  static GrClosableBlock getClosableBlock(@NotNull PsiElement element) {
    if (!(element instanceof GrMethodCallExpression)) {
      return null;
    }

    GrClosableBlock[] closureArguments = ((GrMethodCallExpression)element).getClosureArguments();
    if (closureArguments.length > 0) {
      return closureArguments[0];
    }

    return null;
  }

  private static GroovyPsiElementFactory getPsiElementFactory(@NotNull GradleDslElement element) {
    GroovyPsiElement psiElement = ensureGroovyPsi(element.getPsiElement());
    if (psiElement == null) {
      return null;
    }

    Project project = psiElement.getProject();
    return GroovyPsiElementFactory.getInstance(project);
  }

  static boolean isNewEmptyBlockElement(@NotNull GradleDslElement element) {
    if (element.getPsiElement() != null) {
      return false;
    }

    if (!element.isBlockElement()) {
      return false;
    }

    Collection<GradleDslElement> children = element.getChildren();
    if (children.isEmpty()) {
      return true;
    }

    for (GradleDslElement child : children) {
      if (!isNewEmptyBlockElement(child)) {
        return false;
      }
    }

    return true;
  }

  static void deleteIfEmpty(@Nullable PsiElement element) {
    if (element == null || !element.isValid()) {
      return;
    }

    PsiElement parent = element.getParent();

    if (element instanceof GrAssignmentExpression) {
      if (((GrAssignmentExpression)element).getRValue() == null) {
        element.delete();
      }
    }
    else if (element instanceof GrApplicationStatement) {
      if (((GrApplicationStatement)element).getArgumentList() == null) {
        element.delete();
      }
    }
    else if (element instanceof GrClosableBlock) {
      final Boolean[] isEmpty = new Boolean[]{true};
      ((GrClosableBlock)element).acceptChildren(new GroovyElementVisitor() {
        @Override
        public void visitElement(@NotNull GroovyPsiElement child) {
          if (child instanceof GrParameterList) {
            if (((GrParameterList)child).getParameters().length == 0) {
              return; // Ignore the empty parameter list.
            }
          }
          isEmpty[0] = false;
        }
      });
      if (isEmpty[0]) {
        element.delete();
      }
    }
    else if (element instanceof GrMethodCallExpression) {
      GrMethodCallExpression call = ((GrMethodCallExpression)element);
      GrArgumentList argumentList = null;
      try {
        for (PsiElement curr = call.getFirstChild(); curr != null; curr = curr.getNextSibling()) {
          if (curr instanceof GrArgumentList) {
            argumentList = (GrArgumentList)curr;
            break;
          }
        }
      } catch (AssertionError e) {
        // We will get this exception if the argument list is already deleted.
        argumentList = null;
      }
      GrClosableBlock[] closureArguments = call.getClosureArguments();
      if ((argumentList == null || argumentList.getAllArguments().length == 0)
          && closureArguments.length == 0) {
        element.delete();
      }
    }
    else if (element instanceof GrCommandArgumentList) {
      GrCommandArgumentList commandArgumentList = (GrCommandArgumentList)element;
      if (commandArgumentList.getAllArguments().length == 0) {
        commandArgumentList.delete();
      }
    }
    else if (element instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)element;
      if ((listOrMap.isMap() && listOrMap.getNamedArguments().length == 0)
          || (!listOrMap.isMap() && listOrMap.getInitializers().length == 0)) {
        listOrMap.delete();
      }
    }

    if (!element.isValid()) { // If this element is deleted, also delete the parent if it is empty.
      deleteIfEmpty(parent);
    }
  }

  static GrLiteral createLiteral(@NotNull GradleDslLiteral literal) {
    Object unsavedValue = literal.getUnsavedValue();
    if (unsavedValue == null) {
      return null;
    }

    CharSequence unsavedValueText = null;
    if (unsavedValue instanceof String) {
      unsavedValueText = GrStringUtil.getLiteralTextByValue((String)unsavedValue);
    }
    else if (unsavedValue instanceof Integer || unsavedValue instanceof Boolean) {
      unsavedValueText = unsavedValue.toString();
    }
    literal.reset();

    if (unsavedValueText == null) {
      return null;
    }

    GroovyPsiElementFactory factory = getPsiElementFactory(literal);
    if (factory == null) {
      return null;
    }

    GrExpression newExpression = factory.createExpressionFromText(unsavedValueText);

    if (!(newExpression instanceof GrLiteral)) {
      return null;
    }
    return (GrLiteral)newExpression;
  }

  @Nullable
  static PsiElement createNamedArgumentList(@NotNull GradleDslExpressionList expressionList) {
    GradleDslElement parent = expressionList.getParent();
    assert parent instanceof GradleDslExpressionMap;

    PsiElement parentPsiElement = parent.create();
    if (parentPsiElement == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    GrExpression expressionFromText = factory.createExpressionFromText("[]");
    if (expressionFromText instanceof GrListOrMap) {
      // Elements need to be added to the list before adding the list to the named argument.
      GrListOrMap list = (GrListOrMap)expressionFromText;
      expressionList.commitExpressions(list);
    }
    GrNamedArgument namedArgument = factory.createNamedArgument(expressionList.getName(), expressionFromText);
    PsiElement added;
    if (parentPsiElement instanceof GrArgumentList) {
      added = ((GrArgumentList)parentPsiElement).addNamedArgument(namedArgument);
    }
    else {
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    }
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      expressionList.setPsiElement(addedNameArgument.getExpression());
      return expressionList.getPsiElement();
    }
    return null;
  }
}
