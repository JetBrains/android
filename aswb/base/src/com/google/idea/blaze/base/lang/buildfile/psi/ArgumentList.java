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

import com.google.common.collect.ImmutableList;
import com.intellij.lang.ASTNode;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/** Argument list of a function call */
public class ArgumentList extends BuildListType<Argument> {

  public ArgumentList(ASTNode astNode) {
    super(astNode, Argument.class);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitFuncallArgList(this);
  }

  public Argument[] getArguments() {
    return getElements();
  }

  public Set<String> getKeywordArgNames() {
    Set<String> set = new HashSet<>();
    ASTNode node = getNode().getFirstChildNode();
    while (node != null) {
      if (node.getElementType() == BuildElementTypes.KEYWORD) {
        Argument.Keyword arg = (Argument.Keyword) node.getPsi();
        String keyword = arg.getName();
        if (keyword != null) {
          set.add(keyword);
        }
      }
      node = node.getTreeNext();
    }
    return set;
  }

  @Nullable
  public Argument.Keyword getKeywordArgument(String name) {
    ASTNode node = getNode().getFirstChildNode();
    while (node != null) {
      if (node.getElementType() == BuildElementTypes.KEYWORD) {
        Argument.Keyword arg = (Argument.Keyword) node.getPsi();
        String keyword = arg.getName();
        if (keyword != null && keyword.equals(name)) {
          return arg;
        }
      }
      node = node.getTreeNext();
    }
    return null;
  }

  @Nullable
  public Expression getKeywordArgumentValue(String name) {
    Argument.Keyword keyword = getKeywordArgument(name);
    return keyword != null ? keyword.getValue() : null;
  }

  @Override
  public ImmutableList<Character> getEndChars() {
    return ImmutableList.of(')');
  }
}
