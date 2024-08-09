/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.kotlin;

import static com.android.tools.idea.gradle.dsl.model.kotlin.KotlinModelImpl.JVM_TOOLCHAIN;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.model.kotlin.KotlinSourceSetsDslElement;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KotlinDslElement extends GradleDslBlockElement {

  public static final ImmutableMap<String,PropertiesElementDescription<?>> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"sourceSets", KotlinSourceSetsDslElement.KOTLIN_SOURCE_SETS}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"jvmToolchain", exactly(1), JVM_TOOLCHAIN, SET},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"jvmToolchain", exactly(1), JVM_TOOLCHAIN, SET},
  }).collect(toModelMap());

  public static final PropertiesElementDescription<KotlinDslElement> KOTLIN =
    new PropertiesElementDescription<>("kotlin", KotlinDslElement.class, KotlinDslElement::new, KotlinGradlePropertiesDslElementSchema::new);

  @Override
  @NotNull
  public ImmutableMap<String,PropertiesElementDescription<?>> getChildPropertiesElementsDescriptionMap(
    GradleDslNameConverter.Kind kind
  ) {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  public KotlinDslElement(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap);
  }

  public static final class KotlinGradlePropertiesDslElementSchema extends GradlePropertiesDslElementSchema {
    @Override
    protected ImmutableMap<String, PropertiesElementDescription<?>> getAllBlockElementDescriptions(GradleDslNameConverter.Kind kind) {
      return CHILD_PROPERTIES_ELEMENTS_MAP;
    }

    @NotNull
    @Override
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelNameMap, ktsToModelNameMap);
    }
  }
}