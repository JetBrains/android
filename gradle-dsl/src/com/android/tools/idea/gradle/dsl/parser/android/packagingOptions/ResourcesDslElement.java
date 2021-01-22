/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.android.packagingOptions;

import static com.android.tools.idea.gradle.dsl.model.android.packagingOptions.ResourcesModelImpl.EXCLUDES;
import static com.android.tools.idea.gradle.dsl.model.android.packagingOptions.ResourcesModelImpl.MERGES;
import static com.android.tools.idea.gradle.dsl.model.android.packagingOptions.ResourcesModelImpl.PICK_FIRSTS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public class ResourcesDslElement extends GradleDslBlockElement {
  public static PropertiesElementDescription<ResourcesDslElement> RESOURCES =
    new PropertiesElementDescription<>("resources", ResourcesDslElement.class, ResourcesDslElement::new);

  public ResourcesDslElement(GradleDslElement parent, GradleNameElement name) {
    super(parent, name);
  }

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAL},
    {"merges", property, MERGES, VAL},
    {"pickFirsts", property, PICK_FIRSTS, VAL},
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAL},
    {"merges", property, MERGES, VAL},
    {"pickFirsts", property, PICK_FIRSTS, VAL},
  }).collect(toModelMap());

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
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
}
