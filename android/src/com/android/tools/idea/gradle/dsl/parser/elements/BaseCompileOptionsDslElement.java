/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.parser.java.JavaVersionDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for representing compileOptions block or others blocks which have sourceCompatibility / targetCompatibility fields.
 */
public abstract class BaseCompileOptionsDslElement extends GradlePropertiesDslElement {
  @NonNls public static final String COMPILE_OPTIONS_BLOCK_NAME = "compileOptions";

  @NonNls public static final String SOURCE_COMPATIBILITY_ATTRIBUTE_NAME = "sourceCompatibility";
  @NonNls public static final String TARGET_COMPATIBILITY_ATTRIBUTE_NAME = "targetCompatibility";

  protected BaseCompileOptionsDslElement(@Nullable GradleDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  public BaseCompileOptionsDslElement(@Nullable GradleDslElement parent) {
    super(parent, null, COMPILE_OPTIONS_BLOCK_NAME);
  }

  @Override
  protected boolean isBlockElement() {
    return true;
  }

  @Override
  public void setParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if ((SOURCE_COMPATIBILITY_ATTRIBUTE_NAME.equals(property) || TARGET_COMPATIBILITY_ATTRIBUTE_NAME.equals(property)) &&
        (element instanceof GradleDslLiteral || element instanceof GradleDslReference)) {

      JavaVersionDslElement versionDslElement = new JavaVersionDslElement(this, (GradleDslExpression)element, property);
      super.setParsedElement(property, versionDslElement);
      return;
    }
    super.setParsedElement(property, element);
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    setParsedElement(property, element);
  }
}
