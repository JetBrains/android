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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.references.FuncallReference;
import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import icons.BlazeIcons;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * PSI element for an function call.<br>
 * Could be a top-level rule, Skylark function reference, or general some other python function call
 */
public class FuncallExpression extends BuildElementImpl
    implements Expression, PsiNameIdentifierOwner {

  public FuncallExpression(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitFuncallExpression(this);
  }

  /** The name of the function being called. */
  @Nullable
  public String getFunctionName() {
    ASTNode node = getFunctionNameNode();
    return node != null ? node.getText() : null;
  }

  @Nullable
  public Kind getRuleKind() {
    String functionName = getFunctionName();
    return functionName != null ? Kind.fromRuleName(functionName) : null;
  }

  @Override
  @Nullable
  public String getName() {
    return getNameArgumentValue();
  }

  @Nullable
  @Override
  public PsiElement getNameIdentifier() {
    Argument.Keyword name = getNameArgument();
    return name != null ? name.getValue() : null;
  }

  @CanIgnoreReturnValue
  @Override
  public PsiElement setName(String name) throws IncorrectOperationException {
    StringLiteral nameNode = getNameArgumentValueNode();
    if (nameNode == null) {
      return this;
    }
    ASTNode newChild = PsiUtils.createNewLabel(getProject(), name);
    nameNode.getNode().replaceChild(nameNode.getNode().getFirstChildNode(), newChild);
    return this;
  }

  /** The function name */
  @Nullable
  public ASTNode getFunctionNameNode() {
    PsiElement argList = getArgList();
    if (argList != null) {
      // We want the reference expr directly prior to the open parenthesis.
      // This accounts for Skylark native.rule calls.
      PsiElement prev = argList.getPrevSibling();
      if (prev instanceof ReferenceExpression) {
        return prev.getNode();
      }
    }
    return getNode().findChildByType(BuildElementTypes.REFERENCE_EXPRESSION);
  }

  /** Top-level funcalls are almost always BUILD rules. */
  public boolean isTopLevel() {
    ASTNode parent = getNode().getTreeParent();
    return parent == null || parent.getElementType() == BuildElementTypes.BUILD_FILE;
  }

  public boolean mightBeBuildRule() {
    return isTopLevel() || getNameArgument() != null;
  }

  @Nullable
  public Label resolveBuildLabel() {
    BuildFile containingFile = getContainingFile();
    if (containingFile == null) {
      return null;
    }
    if (containingFile.getBlazeFileType() != BlazeFileType.BuildPackage) {
      return null;
    }
    return LabelUtils.createLabelFromRuleName(getBlazePackage(), getNameArgumentValue());
  }

  @Nullable
  public ArgumentList getArgList() {
    return findChildByType(BuildElementTypes.ARGUMENT_LIST);
  }

  public Argument[] getArguments() {
    ArgumentList argList = getArgList();
    return argList != null ? argList.getArguments() : Argument.EMPTY_ARRAY;
  }

  public Set<String> getKeywordArgumentNames() {
    ArgumentList argList = getArgList();
    return argList != null ? argList.getKeywordArgNames() : ImmutableSet.of();
  }

  /** Keyword argument with name "name", if one is present. */
  @Nullable
  public Argument.Keyword getNameArgument() {
    return getKeywordArgument("name");
  }

  @Nullable
  public Argument.Keyword getKeywordArgument(String name) {
    ArgumentList argList = getArgList();
    return argList != null ? argList.getKeywordArgument(name) : null;
  }

  /** StringLiteral value of keyword argument with name "name", if one is present. */
  @Nullable
  public StringLiteral getNameArgumentValueNode() {
    Argument.Keyword name = getNameArgument();
    Expression expr = name != null ? name.getValue() : null;
    if (expr instanceof StringLiteral) {
      return ((StringLiteral) expr);
    }
    return null;
  }

  /** Value of keyword argument with name "name", if one is present. */
  @Nullable
  public String getNameArgumentValue() {
    StringLiteral node = getNameArgumentValueNode();
    return node != null ? node.getStringContents() : null;
  }

  @Override
  public Icon getIcon(int flags) {
    return mightBeBuildRule() ? BlazeIcons.BuildRule : null;
  }

  @Override
  public String getPresentableText() {
    String functionName = getFunctionName();
    if (functionName == null) {
      return super.getPresentableText();
    }
    String targetName = getNameArgumentValue();
    if (targetName == null) {
      return functionName;
    }
    return String.format("%s : %s", targetName, functionName);
  }

  @Override
  @Nullable
  public FuncallReference getReference() {
    ASTNode nameNode = getFunctionNameNode();
    if (nameNode == null) {
      return null;
    }
    TextRange range = PsiUtils.childRangeInParent(getTextRange(), nameNode.getTextRange());
    return new FuncallReference(this, range);
  }

  /**
   * Searches all StringLiteral children of this element, for one which references the desired
   * target expression.
   */
  @Nullable
  public StringLiteral findChildReferenceToTarget(final FuncallExpression targetRule) {
    final StringLiteral[] child = new StringLiteral[1];
    Processor<StringLiteral> processor =
        stringLiteral -> {
          PsiElement ref = stringLiteral.getReferencedElement();
          if (targetRule.equals(ref)) {
            child[0] = stringLiteral;
            return false;
          }
          return true;
        };
    PsiUtils.processChildrenOfType(this, processor, StringLiteral.class);
    return child[0];
  }
}
