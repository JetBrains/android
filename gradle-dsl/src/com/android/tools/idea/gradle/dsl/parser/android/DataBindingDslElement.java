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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.DataBindingModelImpl.ADD_DEFAULT_ADAPTERS;
import static com.android.tools.idea.gradle.dsl.model.android.DataBindingModelImpl.ENABLED;
import static com.android.tools.idea.gradle.dsl.model.android.DataBindingModelImpl.VERSION;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
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

public class DataBindingDslElement extends GradleDslBlockElement {
  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"addDefaultAdapters", property, ADD_DEFAULT_ADAPTERS, VAR},
    {"isEnabled", property, ENABLED, VAR},
    {"version", property, VERSION, VAR},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"addDefaultAdapters", property, ADD_DEFAULT_ADAPTERS, VAR},
    {"addDefaultAdapters", exactly(1), ADD_DEFAULT_ADAPTERS, SET},
    {"enabled", property, ENABLED, VAR},
    {"enabled", exactly(1), ENABLED, SET},
    {"version", property, VERSION, VAR},
    {"version", exactly(1), VERSION, SET},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"addDefaultAdapters", property, ADD_DEFAULT_ADAPTERS, VAR},
    {"isEnabled", property, ENABLED, VAR},
    {"version", property, VERSION, VAR},
  }).collect(toModelMap());

  public static final PropertiesElementDescription<DataBindingDslElement> DATA_BINDING =
    new PropertiesElementDescription<>("dataBinding",
                                       DataBindingDslElement.class,
                                       DataBindingDslElement::new,
                                       DataBindingDslElementSchema::new);

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public DataBindingDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class DataBindingDslElementSchema extends GradlePropertiesDslElementSchema {
    @NotNull
    @Override
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
    }

    @NotNull
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.BuildFeatures";
    }
  }
}
