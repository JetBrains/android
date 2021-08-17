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
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Smooths the path between Groovy (KotlinScript)
 *   id 'foo' (resp. id("foo"))
 * represented as a GradleDslLiteral, and
 *   id 'foo' version '3.4' (resp. id("foo") version "3.4")
 * represented as a GradleDslInfixExpression
 */

public class LiteralToInfixTransform extends PropertyTransform {
  @NotNull String myName;

  public LiteralToInfixTransform(@NotNull String name) {
    super();
    myName = name;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e,
                      @NotNull GradleDslElement holder) {
    // TODO(xof): this test only works for plugins.
    return e instanceof GradleDslLiteral && "id".equals(e.getName());
  }

  @Override
  public @Nullable GradleDslElement transform(@Nullable GradleDslElement e) {
    return null;
  }

  @Override
  public @NotNull GradleDslExpression bind(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull Object value,
                                           @NotNull String name) {
    return createBasicExpression(holder, value, GradleNameElement.create(myName));
  }

  @Override
  public @NotNull GradleDslElement replace(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull GradleDslExpression newElement,
                                           @NotNull String name) {
    GradleDslLiteral oldLiteral = ((GradleDslLiteral) oldElement).copy();
    GradlePropertiesDslElement propertiesHolder = (GradlePropertiesDslElement) holder;
    propertiesHolder.removeProperty(oldElement);
    GradleDslInfixExpression infixExpression = new GradleDslInfixExpression(holder, null);
    propertiesHolder.setNewElement(infixExpression);
    infixExpression.setNewElement(oldLiteral);
    infixExpression.setNewElement(newElement);

    return newElement;
  }
}
