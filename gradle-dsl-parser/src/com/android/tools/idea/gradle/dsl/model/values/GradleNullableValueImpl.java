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

import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a {@link GradleValueImpl} where {@link #value()} may return a {@code null} value.
 *
 * @param <T> the type of the returned value.
 */
public class GradleNullableValueImpl<T> extends GradleValueImpl<T> implements GradleNullableValue<T> {
  public GradleNullableValueImpl(@NotNull GradleDslElement dslElement, @Nullable T value) {
    super(dslElement, value);
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    VirtualFile file = super.getFile();
    assert file != null;
    return file;
  }

  @Override
  @NotNull
  public String getPropertyName() {
    String propertyName = super.getPropertyName();
    assert propertyName != null;
    return propertyName;
  }
}
