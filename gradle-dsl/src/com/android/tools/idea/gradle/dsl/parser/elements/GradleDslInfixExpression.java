/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

import com.intellij.psi.PsiElement;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Not for generic infix (e.g. arithmetic) but for builder-pattern-style property specification e.g.
//  id 'foo' version '3.2' apply false
// TODO(xof): maybe rename?  GradleDslChainedCalls?
public class GradleDslInfixExpression extends GradlePropertiesDslElement implements GradleDslExpression {

  public GradleDslInfixExpression(
    @Nullable GradleDslElement parent,
    @Nullable PsiElement psiElement
  ) {
    super(parent, psiElement, GradleNameElement.empty());
  }

  @Override
  public @Nullable PsiElement getExpression() {
    return getPsiElement();
  }

  @Override
  public @NotNull GradleNameElement getNameElement() {
    List<GradleDslExpression> expressions = getPropertyElements(GradleDslExpression.class);
    if (expressions.size() > 0) {
      return expressions.get(0).getNameElement();
    }
    return super.getNameElement();
  }

  @Override
  public @NotNull GradleDslExpression copy() {
    // TODO(xof): copy the properties
    return new GradleDslInfixExpression(myParent, null);
  }

  @Override
  public @NotNull GradleDslLiteral setNewLiteral(@NotNull String property, @NotNull Object value) {
    GradleDslLiteral literal = super.setNewLiteral(property, value);
    literal.setElementType(REGULAR);
    return literal;
  }

  @Override
  public @Nullable GradleDslElement requestAnchor(@NotNull GradleDslElement element) {
    GradleDslElement anchor = super.requestAnchor(element);
    if (anchor == null) {
      // This is a special-case for creating the first literal in the infix expression,
      // in which case we want to have the same anchor that the infix expression would have
      // had in its parent.
      // TODO(xof): think harder about this: will it carry on working for other callers of requestAnchor()
      //  (e.g. moveDslElement)?
      return myParent.requestAnchor(this);
    }
    return anchor;
  }
}
