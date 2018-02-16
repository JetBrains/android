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

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.isQuotedString;
import static com.intellij.openapi.util.text.StringUtil.unquoteString;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOLON;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMMA;

public final class GroovyDslUtil {

  static GroovyPsiElement ensureGroovyPsi(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    if (element instanceof GroovyPsiElement) {
      return (GroovyPsiElement)element;
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

  static GroovyPsiElementFactory getPsiElementFactory(@NotNull GradleDslElement element) {
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

    if (!element.isBlockElement() || !element.isInsignificantIfEmpty()) {
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
    if (element == null) {
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
      }
      catch (AssertionError e) {
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
    else if (element instanceof GrNamedArgument) {
      GrNamedArgument namedArgument = (GrNamedArgument)element;
      if (namedArgument.getExpression() == null) {
        namedArgument.delete();
      }
    }
    else if (element instanceof GrVariableDeclaration) {
      GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)element;
      for (GrVariable grVariable : variableDeclaration.getVariables()) {
        if (grVariable.getInitializerGroovy() == null) {
          grVariable.delete();
        }
      }
      // If we have no more variables, delete the declaration.
      if (variableDeclaration.getVariables().length == 0) {
        variableDeclaration.delete();
      }
    }
    else if (element instanceof GrVariable) {
      GrVariable variable = (GrVariable)element;
      if (variable.getInitializerGroovy() == null) {
        variable.delete();
      }
    }

    if (!element.isValid()) {
      // Give the parent a chance to adapt to the missing child.
      handleElementRemoved(parent, element);
      // If this element is deleted, also delete the parent if it is empty.
      deleteIfEmpty(parent);
    }
  }

  static void removePsiIfInvalid(@Nullable GradleDslElement element) {
    if (element == null) {
      return;
    }

    if (element.getPsiElement() != null && !element.getPsiElement().isValid()) {
      element.setPsiElement(null);
    }

    if (element.getParent() != null) {
      removePsiIfInvalid(element.getParent());
    }
  }

  /**
   * This method is used to edit the PsiTree once an element has been deleted.
   *
   * It currently only looks at GrListOrMap to insert a ":" into a map. This is needed because once we delete
   * the final element in a map we are left with [], which is a list.
   */
  static void handleElementRemoved(@Nullable PsiElement psiElement, @Nullable PsiElement removed) {
    if (psiElement == null) {
      return;
    }

    if (psiElement instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)psiElement;
      // Make sure it was being used as a map
      if (removed instanceof GrNamedArgument) {
        if (listOrMap.isEmpty()) {
          final ASTNode node = listOrMap.getNode();
          node.addLeaf(mCOLON, ":", listOrMap.getRBrack().getNode());
        }
      }
    }
  }

  @Nullable
  static GrExpression extractUnsavedExpression(@NotNull GradleDslSettableExpression literal) {
    GroovyPsiElement newElement = ensureGroovyPsi(literal.getUnsavedValue());
    if (!(newElement instanceof GrExpression)) {
      return null;
    }

    return (GrExpression)newElement;
  }

  @Nullable
  static PsiElement createLiteral(@NotNull GradleDslElement context, @NotNull Object unsavedValue) {
    CharSequence unsavedValueText = null;
    if (unsavedValue instanceof String) {
      String stringValue = (String)unsavedValue;
      if (stringValue.startsWith(GrStringUtil.DOUBLE_QUOTES) && stringValue.endsWith(GrStringUtil.DOUBLE_QUOTES)) {
        unsavedValueText = (String)unsavedValue;
      }
      else {
        unsavedValueText = GrStringUtil.getLiteralTextByValue((String)unsavedValue);
      }
    }
    else if (unsavedValue instanceof Integer || unsavedValue instanceof Boolean) {
      unsavedValueText = unsavedValue.toString();
    }
    else if (unsavedValue instanceof ReferenceTo) {
      unsavedValueText = ((ReferenceTo)unsavedValue).getText();
    }

    if (unsavedValueText == null) {
      return null;
    }

    GroovyPsiElementFactory factory = getPsiElementFactory(context);
    if (factory == null) {
      return null;
    }

    return factory.createExpressionFromText(unsavedValueText);
  }

  /**
   * Creates a literal expression map enclosed with brackets "[]" from the given {@link GradleDslExpressionMap}.
   */
  static PsiElement createDerivedMap(@NotNull GradleDslExpressionMap expressionMap) {
    PsiElement parentPsiElement = getParentPsi(expressionMap);
    if (parentPsiElement == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parentPsiElement.getProject());
    GrExpression emptyMap = factory.createExpressionFromText("[:]");
    GrNamedArgument namedArgument = factory.createNamedArgument(expressionMap.getName(), emptyMap);
    PsiElement addedElement = addToMap((GrListOrMap)parentPsiElement, namedArgument);
    assert addedElement instanceof GrNamedArgument;

    PsiElement added = ((GrNamedArgument)addedElement).getExpression();
    expressionMap.setPsiElement(added);
    return added;
  }

  static PsiElement addToMap(@NotNull GrListOrMap map, @NotNull GrNamedArgument newValue) {
    if (map.getNamedArguments().length != 0) {
      map.addAfter(GroovyPsiElementFactory.getInstance(map.getProject()).createWhiteSpace(), map.getLBrack());
      final ASTNode astNode = map.getNode();
      astNode.addLeaf(mCOMMA, ",", map.getLBrack().getNextSibling().getNode());
      CodeStyleManager.getInstance(map.getProject()).reformat(map);
    }
    else {
      // Empty maps are defined by [:], we need to delete the colon before adding the first element.
      while (map.getLBrack().getNextSibling() != map.getRBrack()) {
        map.getLBrack().getNextSibling().delete();
      }
    }
    // GrMapOrListImpl ignores anchor, this will place at start of list after '['
    return map.addAfter(newValue, map.getLBrack());
  }

  @Nullable
  static PsiElement processMapElement(@NotNull GradleDslSettableExpression expression) {
    GradleDslElement parent = expression.getParent();
    assert parent != null;

    GroovyPsiElement parentPsiElement = ensureGroovyPsi(parent.create());
    if (parentPsiElement == null) {
      return null;
    }

    expression.setPsiElement(parentPsiElement);
    GrExpression newLiteral = extractUnsavedExpression(expression);
    if (newLiteral == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(newLiteral.getProject());
    GrNamedArgument namedArgument = factory.createNamedArgument(expression.getName(), newLiteral);
    PsiElement added;
    if (parentPsiElement instanceof GrArgumentList) {
      added = ((GrArgumentList)parentPsiElement).addNamedArgument(namedArgument);
    }
    else if (parentPsiElement instanceof GrListOrMap) {
      GrListOrMap grListOrMap = (GrListOrMap)parentPsiElement;
      added = addToMap(grListOrMap, namedArgument);
    }
    else {
      added = parentPsiElement.addBefore(namedArgument, parentPsiElement.getLastChild());
    }
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      GrExpression grExpression = getChildOfType(addedNameArgument, GrExpression.class);
      if (grExpression != null) {
        expression.setExpression(grExpression);
        expression.setModified(false);
        expression.reset();
        return expression.getPsiElement();
      }
      else {
        return null;
      }
    }
    else {
      throw new IllegalStateException("Unexpected element type added to Mpa: " + added);
    }
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
    else if (parentPsiElement instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)parentPsiElement;
      // For list and maps we need to add the element delimiter "," after the added element if there is more than one.
      if (!listOrMap.isEmpty()) {
        final ASTNode node = listOrMap.getNode();
        node.addLeaf(mCOMMA, ",", listOrMap.getLBrack().getNextSibling().getNode());
      }
      added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
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

  @Nullable
  static String getInjectionName(@NotNull GrStringInjection injection) {
    String variableName = null;

    GrClosableBlock closableBlock = injection.getClosableBlock();
    if (closableBlock != null) {
      String blockText = closableBlock.getText();
      variableName = blockText.substring(1, blockText.length() - 1);
    }
    else {
      GrExpression expression = injection.getExpression();
      if (expression != null) {
        variableName = expression.getText();
      }
    }

    return variableName;
  }

  @NotNull
  static String ensureUnquotedText(@NotNull String str) {
    if (isQuotedString(str)) {
      str = unquoteString(str);
    }
    return str;
  }

  @Nullable
  static PsiElement getParentPsi(@NotNull GradleDslElement element) {
    GradleDslElement parent = element.getParent();
    if (parent == null) {
      return null;
    }

    GroovyPsiElement parentPsiElement = ensureGroovyPsi(parent.create());
    if (parentPsiElement == null) {
      return null;
    }
    return parentPsiElement;
  }

  /**
   * This method is required to work out whether a GradleDslReference or GradleDslLiteral is an internal value in a map.
   * This allows us to add the PsiElement into the correct position, note: due to the PsiElements Api we have to add the
   * ASTNodes directly in {@link #emplaceElementIntoList(PsiElement, PsiElement, PsiElement)}. This method checks the specific
   * conditions where we need to add an element to the inside of a literal list. The reason we have to do it this way
   * is that when we are applying a GradleDslReference or GradleDslLiteral we don't know whether (1) we are actually in a list and (2)
   * whether the list actually needs us to add a comma. Ideally we would have the apply/create/delete methods of GradleDslExpressionList
   * position the arguments. This is a workaround for now.
   *
   * Note: In order to get the position of where to insert the item, we set the PsiElement of the literal/reference to be the previous
   * item in the list (this is done in GradleDslExpressionList) and then set it back once we have called apply.
   */
  static boolean shouldAddToListInternal(@NotNull GradleDslElement element) {
    GradleDslElement parent = element.getParent();
    if (!(parent instanceof GradleDslExpressionList)) {
      return false;
    }
    PsiElement parentPsi = parent.getPsiElement();
    return ((parentPsi instanceof GrListOrMap && ((GrListOrMap)parentPsi).getInitializers().length > 0) ||
            (parentPsi instanceof GrArgumentList && ((GrArgumentList)parentPsi).getAllArguments().length > 0));
  }

  static void emplaceElementIntoList(@NotNull PsiElement anchorBefore, @NotNull PsiElement list, @NotNull PsiElement newElement) {
    final ASTNode node = list.getNode();
    final ASTNode anchor = anchorBefore.getNode().getTreeNext();
    node.addChild(newElement.getNode(), anchor);
    node.addLeaf(mCOMMA, ",", newElement.getNode());
  }

  static void emplaceElementToFrontOfList(@NotNull PsiElement listElement, @NotNull PsiElement newElement) {
    assert listElement instanceof GrListOrMap || listElement instanceof GrArgumentList;
    final ASTNode node = listElement.getNode();
    if (listElement instanceof GrListOrMap) {
      GrListOrMap list = (GrListOrMap)listElement;
      final ASTNode anchor = list.getLBrack().getNode().getTreeNext();
      if (!list.isEmpty()) {
        node.addLeaf(mCOMMA, ",", anchor);
      }
      // We want to anchor this off the added mCOMMA node.
      node.addChild(newElement.getNode(), list.getLBrack().getNode().getTreeNext());
    }
    else {
      ASTNode anchor = getFirstASTNode(listElement);
      if (anchor != null) {
        node.addLeaf(mCOMMA, ",", anchor);
      }
      // We want to anchor this off the added mCOMMA node
      node.addChild(newElement.getNode(), getFirstASTNode(listElement));
    }
  }

  @Nullable
  static ASTNode getFirstASTNode(@NotNull PsiElement parent) {
    final PsiElement firstChild = parent.getFirstChild();
    if (firstChild == null) {
      return null;
    }
    return firstChild.getNode();
  }

  @NotNull
  static PsiElement createPsiElementInsideList(@NotNull GradleDslElement dslElement,
                                               @NotNull PsiElement parentPsiElement,
                                               @NotNull PsiElement newElement) {
    PsiElement added;
    if ((parentPsiElement instanceof GrListOrMap || parentPsiElement instanceof GrArgumentList) &&
        dslElement.getParent() instanceof GradleDslExpressionList) {
      // Add to the front when we are inserting the element into a list
      emplaceElementToFrontOfList(parentPsiElement, newElement);
      added = newElement;
    }
    else if (parentPsiElement instanceof GrListOrMap || // Entries in [].
             (parentPsiElement instanceof GrArgumentList &&
              !(parentPsiElement instanceof GrCommandArgumentList))) { // Method call arguments in ().
      added = parentPsiElement.addBefore(newElement, parentPsiElement.getLastChild()); // add before ) or ]
    }
    else if (shouldAddToListInternal(dslElement)) {
      emplaceElementIntoList(parentPsiElement, parentPsiElement.getParent(), newElement);
      added = newElement;
    }
    else {
      added = parentPsiElement.addAfter(newElement, parentPsiElement.getLastChild());
    }

    return added;
  }

  @Nullable
  static PsiElement createNameElement(@NotNull GradleDslElement context, @NotNull String name) {
    GroovyPsiElementFactory factory = getPsiElementFactory(context);
    if (factory == null) {
      return null;
    }

    String str = name + " = 1";
    GrExpression expression = factory.createExpressionFromText(str);
    assert expression instanceof GrAssignmentExpression;
    return ((GrAssignmentExpression)expression).getLValue();
  }

  static void maybeUpdateName(@NotNull GradleDslElement element) {
    PsiElement oldName = element.getNameElement().getNamedPsiElement();
    String newName = element.getNameElement().getUnsavedName();
    PsiElement newElement;
    if (newName == null || oldName == null) {
      return;
    }
    if (oldName instanceof PsiNamedElement) {
      PsiNamedElement namedElement = (PsiNamedElement)oldName;
      namedElement.setName(newName);
      newElement = namedElement;
    }
    else {
      PsiElement psiElement = createNameElement(element, newName);
      if (psiElement == null) {
        throw new IllegalStateException("Can't create new GrExpression for name element");
      }
      newElement = oldName.replace(psiElement);
    }
    element.getNameElement().commitNameChange(newElement);
  }
}
