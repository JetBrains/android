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
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class ResourcesDslElement extends GradleDslBlockElement {
  public static PropertiesElementDescription<ResourcesDslElement> RESOURCES =
    new PropertiesElementDescription<>("resources",
                                       ResourcesDslElement.class,
                                       ResourcesDslElement::new,
                                       ResourcesDslElementSchema::new);

  public ResourcesDslElement(GradleDslElement parent, GradleNameElement name) {
    super(parent, name);
  }

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAL},
    {"merges", property, MERGES, VAL},
    {"pickFirsts", property, PICK_FIRSTS, VAL},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAL},
    {"merges", property, MERGES, VAL},
    {"pickFirsts", property, PICK_FIRSTS, VAL},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"excludes", property, EXCLUDES, VAL},
    {"merges", property, MERGES, VAL},
    {"pickFirsts", property, PICK_FIRSTS, VAL},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public static final class ResourcesDslElementSchema extends GradlePropertiesDslElementSchema {
    @NotNull
    @Override
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
    }

    @NotNull
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.ResourcesPackaging";
    }
  }
}
