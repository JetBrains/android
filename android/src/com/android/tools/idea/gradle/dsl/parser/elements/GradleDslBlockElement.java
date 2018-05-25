/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;

/**
 * Base class for all the {@link GradleDslElement}s that represent blocks like android, productFlavors, buildTypes etc.
 */
public class GradleDslBlockElement extends GradlePropertiesDslElement {
  protected GradleDslBlockElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (APPLY_BLOCK_NAME.equals(element.getFullName()) && element instanceof GradleDslExpressionMap) {
      ApplyDslElement applyDslElement = getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
      if (applyDslElement == null) {
        applyDslElement = new ApplyDslElement(this);
        super.addParsedElement(applyDslElement);
      }
      applyDslElement.addParsedElement(element);
      return;
    }
    super.addParsedElement(element);
  }
}
