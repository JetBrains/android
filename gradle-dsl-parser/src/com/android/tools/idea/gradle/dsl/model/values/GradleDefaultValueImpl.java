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
package com.android.tools.idea.gradle.dsl.model.values;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.dsl.api.values.GradleDefaultValue;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a {@link GradleValueImpl} which may be not be present in a Gradle file.
 *
 * <p>It represents either a value present in the Gradle file or the default value needs to be used in it's absence.</p>
 *
 * @param <T> the type of the returned value.
 */
public class GradleDefaultValueImpl<T> extends GradleValueImpl<T> implements GradleDefaultValue<T> {
  public GradleDefaultValueImpl(@Nullable GradleDslElement dslElement, @NotNull T value) {
    super(dslElement, value);
  }

  @Override
  @NotNull
  public T value() {
    T value = super.value();
    assert value != null;
    return value;
  }
}
