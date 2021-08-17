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
package com.android.tools.idea.gradle.dsl.model.ext.transforms;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createBasicExpression;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InfixPropertyTransform extends PropertyTransform {
  @NotNull String myPropertyName;

  public InfixPropertyTransform(@NotNull String propertyName) {
    super();
    myPropertyName = propertyName;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    return e instanceof GradleDslInfixExpression;
  }

  @Override
  public @Nullable GradleDslElement transform(@Nullable GradleDslElement e) {
    if (e == null) return null;
    GradleDslInfixExpression infixExpression = (GradleDslInfixExpression) e;
    return infixExpression.getPropertyElement(myPropertyName);
  }

  @Override
  public @NotNull GradleDslExpression bind(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull Object value,
                                           @NotNull String name) {
    return createBasicExpression(holder, value, GradleNameElement.create(myPropertyName));
  }

  @Override
  public @NotNull GradleDslElement replace(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull GradleDslExpression newElement,
                                           @NotNull String name) {
    GradleDslInfixExpression infixExpression = (GradleDslInfixExpression) oldElement;
    GradleDslLiteral existing = (GradleDslLiteral) infixExpression.getPropertyElement(myPropertyName);
    if (existing == null) {
      infixExpression.setNewElement(newElement);
    }
    else {
      // TODO(xof): though it might be cleaner if we remove/add, at the moment remove of an infix expression doesn't correctly
      //  delete the operator text because of Psi tree representation issues (there isn't a single Psi node containing operator and
      //  value of an infix expression).
      Object value = ((GradleDslLiteral) newElement).getValue();
      if (value != null) {
        existing.setValue(value);
      }
    }
    return infixExpression;
  }
}
