/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This transform provides read-only access to properties of models which are inexpressible for some syntactic
 * forms of those models.  For example, plugin models support "apply" and "version" properties, but those properties are only
 * syntactically valid in the form
 * <pre>
 *   plugins {
 *     id "name" version "..." apply false
 *   }
 * </pre>
 * We do not provide a way for the user to test whether the operations to set the value are allowed for a particular syntactic form,
 * but logging an exception and failing (as here) is better than altering the build files with invalid build code (as would happen if
 * we fell through to {@link DefaultTransform}).
 */
public class InexpressiblePropertyTransform extends PropertyTransform {
  public static final Logger LOG = Logger.getInstance(InexpressiblePropertyTransform.class);

  public InexpressiblePropertyTransform() { super(); }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    return true;
  }

  @Override
  public @Nullable GradleDslElement transform(@Nullable GradleDslElement e) {
    return null;
  }

  @Override
  public @Nullable GradleDslExpression bind(@NotNull GradleDslElement holder,
                                            @Nullable GradleDslElement oldElement,
                                            @NotNull Object value,
                                            @NotNull String name) {
    LOG.warn(new UnsupportedOperationException("Cannot bind a value to property " + name + " in holder " + holder.getName()));
    return null;
  }

  @Override
  public @Nullable GradleDslExpression bind(@NotNull GradleDslElement holder,
                                            @Nullable GradleDslElement oldElement,
                                            @NotNull Object value,
                                            @NotNull ModelPropertyDescription propertyDescription) {
    String name = propertyDescription.name;
    LOG.warn(new UnsupportedOperationException("Cannot bind a value to property " + name + " in holder " + holder.getName()));
    return null;
  }

  @Override
  public @Nullable GradleDslElement replace(@NotNull GradleDslElement holder,
                                            @Nullable GradleDslElement oldElement,
                                            @NotNull GradleDslExpression newElement,
                                            @NotNull String name) {
    LOG.warn(new UnsupportedOperationException("Cannot replace element for property " + name + " in holder " + holder.getName()));
    return null;
  }
}
