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

import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.references.ArgumentReference;
import com.google.idea.blaze.base.lang.buildfile.references.KeywordArgumentReference;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import javax.annotation.Nullable;

/** PSI element for an argument, passed via a function call. */
public abstract class Argument extends BuildElementImpl {

  public static final Argument[] EMPTY_ARRAY = new Argument[0];

  public Argument(ASTNode node) {
    super(node);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitArgument(this);
  }

  /** The value passed by this argument */
  @Nullable
  public Expression getValue() {
    // for *args, **kwargs, this should be 'args' or 'kwargs' identifiers.
    // otherwise the expression after the (optional) '='
    ASTNode node = getNode().getLastChildNode();
    while (node != null) {
      IElementType type = node.getElementType();
      if (BuildElementTypes.EXPRESSIONS.contains(type)) {
        return (Expression) node.getPsi();
      }
      if (type == BuildToken.fromKind(TokenKind.EQUALS)
          || type == BuildToken.fromKind(TokenKind.STAR)
          || type == BuildToken.fromKind(TokenKind.STAR_STAR)) {
        break;
      }
      node = node.getTreePrev();
    }
    return null;
  }

  /** Keyword AST node */
  public static class Keyword extends Argument {
    public Keyword(ASTNode node) {
      super(node);
    }

    @Override
    protected void acceptVisitor(BuildElementVisitor visitor) {
      visitor.visitKeywordArgument(this);
    }

    @Nullable
    public ASTNode getNameNode() {
      return getNode().findChildByType(BuildToken.IDENTIFIER);
    }

    @Override
    @Nullable
    public String getName() {
      ASTNode node = getNameNode();
      return node != null ? node.getText() : null;
    }

    @Override
    public KeywordArgumentReference getReference() {
      ASTNode keywordNode = getNameNode();
      if (keywordNode != null) {
        TextRange range = PsiUtils.childRangeInParent(getTextRange(), keywordNode.getTextRange());
        return new KeywordArgumentReference(this, range);
      }
      return null;
    }
  }

  /** A positional argument */
  public static class Positional extends Argument {
    public Positional(ASTNode node) {
      super(node);
    }

    @Override
    public PsiReference getReference() {
      return new ArgumentReference<>(this, getTextRange(), true);
    }
  }

  /** Variable number of positional arguments: *args */
  static class Star extends Argument {
    public Star(ASTNode node) {
      super(node);
    }
  }

  /** Variable number of keyword arguments: **kwargs */
  public static class StarStar extends Argument {
    public StarStar(ASTNode node) {
      super(node);
    }
  }
}
