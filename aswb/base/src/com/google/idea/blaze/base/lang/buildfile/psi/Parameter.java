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
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** PSI nodes for parameters in a function declaration */
public abstract class Parameter extends NamedBuildElement {

  public static final Parameter[] EMPTY_ARRAY = new Parameter[0];

  public Parameter(ASTNode node) {
    super(node);
  }

  public boolean hasDefaultValue() {
    return false;
  }

  @Nullable
  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Nodes.Parameter;
  }

  /** Includes stars where relevant. */
  public String getPresentableName() {
    return getName();
  }

  static class Optional extends Parameter {
    public Optional(ASTNode node) {
      super(node);
    }

    @Override
    protected void acceptVisitor(BuildElementVisitor visitor) {
      visitor.visitParameter(this);
    }

    public Expression getDefaultValue() {
      ASTNode node = getNode().getLastChildNode();
      while (node != null) {
        if (BuildElementTypes.EXPRESSIONS.contains(node.getElementType())) {
          return (Expression) node.getPsi();
        }
        if (node.getElementType() == BuildToken.fromKind(TokenKind.EQUALS)) {
          break;
        }
        node = node.getTreePrev();
      }
      return null;
    }

    @Override
    public boolean hasDefaultValue() {
      return true;
    }
  }

  static class Mandatory extends Parameter {
    public Mandatory(ASTNode node) {
      super(node);
    }

    @Override
    protected void acceptVisitor(BuildElementVisitor visitor) {
      visitor.visitParameter(this);
    }
  }

  static class Star extends Parameter {
    public Star(ASTNode node) {
      super(node);
    }

    @Override
    protected void acceptVisitor(BuildElementVisitor visitor) {
      visitor.visitParameter(this);
    }

    @Override
    public String getPresentableName() {
      return "*" + getName();
    }
  }

  /** */
  public static class StarStar extends Parameter {
    public StarStar(ASTNode node) {
      super(node);
    }

    @Override
    protected void acceptVisitor(BuildElementVisitor visitor) {
      visitor.visitParameter(this);
    }

    @Override
    public String getPresentableName() {
      return "**" + getName();
    }
  }
}
