/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.android;

import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FakeSdkElement extends FakeElement {
  private final GradleDslSimpleExpression expression;

  public FakeSdkElement(@Nullable GradleDslElement parent,
                        @NotNull GradleNameElement name,
                        @NotNull GradleDslSimpleExpression expression,
                        boolean canDelete) {
    super(parent, name, expression, canDelete);
    this.expression = expression;
  }

  @Override
  protected @Nullable Object produceRawValue() {
    // maybe?  Who knows?
    return getUnresolvedValue();
  }

  @Override
  protected @Nullable Object extractValue() {
    Object value = expression.getValue();
    if (value instanceof String) {
      return "android-" + value;
    }
    else {
      return value;
    }
  }

  @Override
  protected void consumeValue(@Nullable Object value) {
    // should never be called: handled by SdkOrPreviewTransform
  }

  @Override
  public @NotNull GradleDslSimpleExpression copy() {
    return new FakeSdkElement(getParent(), GradleNameElement.copy(getNameElement()), expression, myCanDelete);
  }
}
