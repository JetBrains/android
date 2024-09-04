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
import java.util.StringJoiner;
import javax.annotation.Nullable;

/** Parameter list in a function declaration */
public class ParameterList extends BuildListType<Parameter> {

  public ParameterList(ASTNode astNode) {
    super(astNode, Parameter.class);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitFunctionParameterList(this);
  }

  @Nullable
  public Parameter findParameterByName(String name) {
    ASTNode node = getNode().getFirstChildNode();
    while (node != null) {
      if (node.getElementType() == BuildElementTypes.PARAM_OPTIONAL
          || node.getElementType() == BuildElementTypes.PARAM_MANDATORY) {
        Parameter param = (Parameter) node.getPsi();
        if (name.equals(param.getName())) {
          return param;
        }
      }
      node = node.getTreeNext();
    }
    return null;
  }

  public boolean hasStarStar() {
    return !findChildrenByType(BuildElementTypes.PARAM_STAR_STAR).isEmpty();
  }

  public boolean hasStar() {
    return !findChildrenByType(BuildElementTypes.PARAM_STAR).isEmpty();
  }

  @Override
  public String getPresentableText() {
    StringJoiner joiner = new StringJoiner(", ", "(", ")");
    for (Parameter param : getElements()) {
      joiner.add(param.getPresentableName());
    }
    return joiner.toString();
  }

  @Override
  public ImmutableList<Character> getEndChars() {
    return ImmutableList.of(')');
  }
}
