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
package com.android.tools.idea.gradle.dsl.parser.elements;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Goal is to have light schema for GradlePropertiesDslElement.
 * Without creating element itself, we can get possible element children - blocks and properties
 * Blocks and properties are in use to restrict auto-complete
 */
public abstract class GradlePropertiesDslElementSchema {

  /**
   * Method supposed to be override in children and returns all block element description for particular Dsl Element
   */
  protected ImmutableMap<String, PropertiesElementDescription<?>> getAllBlockElementDescriptions(GradleDslNameConverter.Kind kind) {
    return ImmutableMap.of();
  }

  @NotNull
  public ImmutableMap<String, PropertiesElementDescription<?>> getBlockElementDescriptions(GradleDslNameConverter.Kind kind) {
    return getAllBlockElementDescriptions(kind).entrySet().stream()
      .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Nullable
  public PropertiesElementDescription<?> getBlockElementDescription(GradleDslNameConverter.Kind kind, String name) {
    return getBlockElementDescriptions(kind).get(name);
  }

  /**
   * Returns properties of some Dsl element that are valid
   * @param kind
   * @return
   */
  @NotNull
  public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind){
    return ExternalToModelMap.empty;
  }

  /**
   * Returns full qualified class name for AGP DSL element.
   * Usually it's an interface that declared at Android plugin side to support gradle build
   * file syntax.
   */
  @Nullable
  public String getAgpDocClass() {
    return null;
  }
}
