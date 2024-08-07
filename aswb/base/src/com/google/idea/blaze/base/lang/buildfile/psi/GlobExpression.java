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

import com.google.idea.blaze.base.lang.buildfile.references.GlobReference;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import javax.annotation.Nullable;

/** PSI element for a glob expression. */
public class GlobExpression extends BuildElementImpl implements Expression {

  public GlobExpression(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitGlobExpression(this);
  }

  @Nullable
  public ArgumentList getArgList() {
    return findChildByType(BuildElementTypes.ARGUMENT_LIST);
  }

  public Argument[] getArguments() {
    ArgumentList argList = getArgList();
    return argList != null ? argList.getArguments() : Argument.EMPTY_ARRAY;
  }

  @Nullable
  public Argument.Keyword getKeywordArgument(String name) {
    ArgumentList list = getArgList();
    return list != null ? list.getKeywordArgument(name) : null;
  }

  @Nullable
  public Expression getIncludes() {
    Argument arg = getKeywordArgument("include");
    if (arg == null) {
      Argument[] allArgs = getArguments();
      if (allArgs.length != 0 && allArgs[0] instanceof Argument.Positional) {
        arg = allArgs[0];
      }
    }
    return getArgValue(arg);
  }

  @Nullable
  public Expression getExcludes() {
    return getArgValue(getKeywordArgument("exclude"));
  }

  @Nullable
  private static Expression getArgValue(@Nullable Argument arg) {
    return arg != null ? arg.getValue() : null;
  }

  public boolean areDirectoriesExcluded() {
    Argument.Keyword arg = getKeywordArgument("exclude_directories");
    if (arg != null) {
      // '0' and '1' are the only accepted values
      Expression value = arg.getValue();
      return value == null || !value.getText().equals("0");
    }
    return true;
  }

  @Nullable
  public ASTNode getGlobFuncallElement() {
    return getNode().findChildByType(BuildElementTypes.REFERENCE_EXPRESSION);
  }

  private volatile GlobReference reference = null;

  @Override
  public GlobReference getReference() {
    GlobReference ref = reference;
    if (ref != null) {
      return ref;
    }
    synchronized (this) {
      if (reference == null) {
        reference = new GlobReference(this);
      }
      return reference;
    }
  }

  /**
   * The text range within the glob expression used for references. This is the text the user needs
   * to click on for navigation support, and also the destination when finding usages in a glob.
   */
  public TextRange getReferenceTextRange() {
    // Ideally, this would be either the full range of the expression,
    // or the range of the specific pattern matching
    // a given file. However, that leads to conflicts with the individual string references,
    // causing unnecessary and expensive de-globbing.
    // e.g. while typing the glob patterns, IJ will be looking for code-completion possibilities,
    // and need to de-glob to do this
    // (due to a lack of communication between the different code-completion components).

    return new TextRange(0, 4);
  }

  public boolean matches(String packageRelativePath, boolean isDirectory) {
    return getReference().matches(packageRelativePath, isDirectory);
  }
}
