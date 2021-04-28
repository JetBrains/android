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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.COMPOSE;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.DATA_BINDING;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.ML_MODEL_BINDING;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.VIEW_BINDING;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.SurfaceSyntaxDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class BuildFeaturesDslElement extends GradleDslBlockElement {
  public static final ImmutableMap<SurfaceSyntaxDescription, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"compose", property, COMPOSE, VAR},
    {"dataBinding", property, DATA_BINDING, VAR},
    {"mlModelBinding", property, ML_MODEL_BINDING, VAR},
    {"viewBinding", property, VIEW_BINDING, VAR},
  }).collect(toModelMap());

  public static final ImmutableMap<SurfaceSyntaxDescription, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"compose", property, COMPOSE, VAR},
    {"compose", exactly(1), COMPOSE, SET},
    {"dataBinding", property, DATA_BINDING, VAR},
    {"dataBinding", exactly(1), DATA_BINDING, SET},
    {"mlModelBinding", property, ML_MODEL_BINDING, VAR},
    {"mlModelBinding", exactly(1), ML_MODEL_BINDING, SET},
    {"viewBinding", property, VIEW_BINDING, VAR},
    {"viewBinding", exactly(1), VIEW_BINDING, SET},
  }).collect(toModelMap());
  public static final PropertiesElementDescription<BuildFeaturesDslElement> BUILD_FEATURES =
    new PropertiesElementDescription<>("buildFeatures", BuildFeaturesDslElement.class, BuildFeaturesDslElement::new);

  @Override
  public @NotNull ImmutableMap<SurfaceSyntaxDescription, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter instanceof KotlinDslNameConverter) {
      return ktsToModelNameMap;
    }
    else if (converter instanceof GroovyDslNameConverter) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  public BuildFeaturesDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
