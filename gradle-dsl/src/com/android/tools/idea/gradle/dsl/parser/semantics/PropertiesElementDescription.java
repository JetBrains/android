/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.semantics;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement.EmptyGradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementConstructor;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchemaConstructor;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertiesElementDescription<T extends GradlePropertiesDslElement> {
  @Nullable public final String name;
  @NotNull public final Class<T> clazz;
  @NotNull public final GradlePropertiesDslElementConstructor<T> constructor;
  @NotNull public final GradlePropertiesDslElementSchemaConstructor schemaConstructor;
  @NotNull public final Predicate<Kind> isValidForKind;

  public static final Predicate<Kind> NOT_FOR_DECLARATIVE = (kind) -> !kind.equals(Kind.DECLARATIVE_TOML);

  public static final Predicate<Kind> FOR_ALL = (kind) -> true;


  /**
   * Creates PropertiesElementDescription instance with empty property schema constructor
   */
  public PropertiesElementDescription(
    @Nullable String name,
    @NotNull Class<T> clazz,
    @NotNull GradlePropertiesDslElementConstructor<T> constructor
  ) {
    this(name, clazz, constructor, EmptyGradlePropertiesDslElementSchema::new, FOR_ALL);
  }

  public PropertiesElementDescription(
    @Nullable String name,
    @NotNull Class<T> clazz,
    @NotNull GradlePropertiesDslElementConstructor<T> constructor,
    @NotNull GradlePropertiesDslElementSchemaConstructor schemaConstructor
  ) {
    this(name, clazz, constructor, schemaConstructor, FOR_ALL);
  }

  public PropertiesElementDescription(
    @Nullable String name,
    @NotNull Class<T> clazz,
    @NotNull GradlePropertiesDslElementConstructor<T> constructor,
    @NotNull Predicate<Kind> isValidForKind
  ) {
    this(name, clazz, constructor, EmptyGradlePropertiesDslElementSchema::new, isValidForKind);
  }

  public PropertiesElementDescription(
    @Nullable String name,
    @NotNull Class<T> clazz,
    @NotNull GradlePropertiesDslElementConstructor<T> constructor,
    @NotNull GradlePropertiesDslElementSchemaConstructor schemaConstructor,
    @NotNull Predicate<Kind> isValidForKind
  ) {
    this.name = name;
    this.clazz = clazz;
    this.constructor = constructor;
    this.schemaConstructor = schemaConstructor;
    this.isValidForKind = isValidForKind;
  }

  public PropertiesElementDescription<T> copyWithName(@NotNull String name) {
    return new PropertiesElementDescription<>(name, clazz, constructor, schemaConstructor);
  }
}
