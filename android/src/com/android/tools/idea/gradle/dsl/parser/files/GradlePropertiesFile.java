/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;

import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Properties;

public final class GradlePropertiesFile extends GradleDslFile {
  @NotNull
  private final Properties myProperties;

  public GradlePropertiesFile(@NotNull Properties properties,
                              @NotNull VirtualFile file,
                              @NotNull Project project,
                              @NotNull String moduleName) {
    super(file, project, moduleName);
    myProperties = properties;
  }

  @Override
  public void parse() {
    // There is nothing to parse in a properties file as it's just a java properties file.
  }

  @Override
  @Nullable
  public GradleDslExpression getPropertyElement(@NotNull String property) {
    String value = myProperties.getProperty(property);
    if (value == null) {
      return null;
    }

    GradlePropertyElement propertyElement = new GradlePropertyElement(this, property);
    propertyElement.setValue(value);
    return propertyElement;
  }

  @Override
  @NotNull
  public GradleDslWriter getWriter() {
    return new GradleDslWriter.Adapter();
  }

  private static class GradlePropertyElement extends GradleDslExpression {
    @Nullable private Object myValue;

    private GradlePropertyElement(@Nullable GradleDslElement parent, @NotNull String name) {
      super(parent, null, name, null);
    }

    @Override
    @Nullable
    public Object getValue() {
      return myValue;
    }

    @Override
    @Nullable
    public Object getUnresolvedValue() {
      return getValue();
    }

    @Override
    @Nullable
    public <T> T getValue(@NotNull Class<T> clazz) {
      Object value = getValue();
      if (clazz.isInstance(value)) {
        return clazz.cast(value);
      }
      return null;
    }

    @Override
    @Nullable
    public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
      return getValue(clazz);
    }

    @Override
    public void setValue(@NotNull Object value) {
      myValue = value;
      valueChanged();
    }

    @Override
    @NotNull
    public Collection<GradleDslElement> getChildren() { return ImmutableList.of(); }

    @Override
    protected void apply() {
      // There is nothing to apply here as this is just a dummy dsl element to represent a property.
    }

    @Override
    protected void reset() {
      // There is nothing to reset here as this is just a dummy dsl element to represent a property.
    }
  }
}