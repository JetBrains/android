/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.android.productFlavors;

import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.VectorDrawablesOptionsModelImpl.GENERATED_DENSITIES;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.VectorDrawablesOptionsModelImpl.USE_SUPPORT_LIBRARY;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class VectorDrawablesOptionsDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<VectorDrawablesOptionsDslElement> VECTOR_DRAWABLES_OPTIONS =
    new PropertiesElementDescription<>("vectorDrawables",
                                       VectorDrawablesOptionsDslElement.class,
                                       VectorDrawablesOptionsDslElement::new,
                                       VectorDrawablesOptionsDslElementSchema::new);

  private static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"generatedDensities", property, GENERATED_DENSITIES, VAL},
    {"generatedDensities", atLeast(0), GENERATED_DENSITIES, OTHER},
    {"setGeneratedDensities", exactly(1), GENERATED_DENSITIES, SET},
    {"useSupportLibrary", property, USE_SUPPORT_LIBRARY, VAR}
  }).collect(toModelMap());

  private static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"generatedDensities", property, GENERATED_DENSITIES, VAR},
    {"generatedDensities", atLeast(0), GENERATED_DENSITIES, OTHER},
    {"useSupportLibrary", property, USE_SUPPORT_LIBRARY, VAR},
    {"useSupportLibrary", exactly(1), USE_SUPPORT_LIBRARY, SET}
  }).collect(toModelMap());

  private static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"generatedDensities", property, GENERATED_DENSITIES, VAR},
    {"useSupportLibrary", property, USE_SUPPORT_LIBRARY, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public VectorDrawablesOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class VectorDrawablesOptionsDslElementSchema extends GradlePropertiesDslElementSchema {
    @NotNull
    @Override
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
    }

    @NotNull
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.VectorDrawables";
    }
  }
}
