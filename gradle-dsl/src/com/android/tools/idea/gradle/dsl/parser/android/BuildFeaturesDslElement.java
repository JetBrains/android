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

import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.AIDL;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.BUILD_CONFIG;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.COMPOSE;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.DATA_BINDING;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.ML_MODEL_BINDING;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.PREFAB;
import static com.android.tools.idea.gradle.dsl.model.android.BuildFeaturesModelImpl.RENDER_SCRIPT;
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
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class BuildFeaturesDslElement extends GradleDslBlockElement {
  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"compose", property, COMPOSE, VAR},
    {"dataBinding", property, DATA_BINDING, VAR},
    {"mlModelBinding", property, ML_MODEL_BINDING, VAR},
    {"viewBinding", property, VIEW_BINDING, VAR},
    {"prefab", property, PREFAB, VAR},
    {"renderScript", property, RENDER_SCRIPT, VAR},
    {"buildConfig", property, BUILD_CONFIG, VAR},
    {"aidl", property, AIDL, VAR},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"compose", property, COMPOSE, VAR},
    {"compose", exactly(1), COMPOSE, SET},
    {"dataBinding", property, DATA_BINDING, VAR},
    {"dataBinding", exactly(1), DATA_BINDING, SET},
    {"mlModelBinding", property, ML_MODEL_BINDING, VAR},
    {"mlModelBinding", exactly(1), ML_MODEL_BINDING, SET},
    {"viewBinding", property, VIEW_BINDING, VAR},
    {"viewBinding", exactly(1), VIEW_BINDING, SET},
    {"prefab", property, PREFAB, VAR},
    {"prefab", exactly(1), PREFAB, SET},
    {"renderScript", property, RENDER_SCRIPT, VAR},
    {"renderScript", exactly(1), RENDER_SCRIPT, SET},
    {"buildConfig", property, BUILD_CONFIG, VAR},
    {"buildConfig", exactly(1), BUILD_CONFIG, SET},
    {"aidl", property, AIDL, VAR},
    {"aidl", exactly(1), AIDL, SET},
  }).collect(toModelMap());
  public static final PropertiesElementDescription<BuildFeaturesDslElement> BUILD_FEATURES =
    new PropertiesElementDescription<>("buildFeatures", BuildFeaturesDslElement.class, BuildFeaturesDslElement::new);

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap);
  }

  public BuildFeaturesDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
