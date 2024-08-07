/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.refactor;

import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/** Allows direct text substitution in {@link StringLiteral}'s by IntelliJ's refactoring tools. */
public class StringLiteralElementManipulator extends AbstractElementManipulator<StringLiteral> {

  @Override
  public StringLiteral handleContentChange(
      StringLiteral element, TextRange range, String newContent)
      throws IncorrectOperationException {
    ASTNode node = element.getNode();
    node.replaceChild(
        node.getFirstChildNode(), PsiUtils.createNewLabel(element.getProject(), newContent));
    return element;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull StringLiteral element) {
    return StringLiteral.textRangeInElement(element.getText());
  }
}
