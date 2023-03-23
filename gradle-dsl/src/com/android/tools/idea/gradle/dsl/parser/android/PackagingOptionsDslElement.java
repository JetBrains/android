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

import static com.android.tools.idea.gradle.dsl.model.android.PackagingOptionsModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.DexDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.JniLibsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.ResourcesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class PackagingOptionsDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<PackagingOptionsDslElement> PACKAGING_OPTIONS =
    new PropertiesElementDescription<>("packagingOptions", PackagingOptionsDslElement.class, PackagingOptionsDslElement::new);
  public static final PropertiesElementDescription<PackagingOptionsDslElement> PACKAGING =
    new PropertiesElementDescription<>("packaging", PackagingOptionsDslElement.class, PackagingOptionsDslElement::new);

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"doNotStrip", property, DO_NOT_STRIP, VAR},
    {"doNotStrip", exactly(1), DO_NOT_STRIP, AUGMENT_LIST},
    {"excludes", property, EXCLUDES, VAR},
    {"exclude", exactly(1), EXCLUDES, AUGMENT_LIST},
    {"merges", property, MERGES, VAR},
    {"merge", exactly(1), MERGES, AUGMENT_LIST},
    {"pickFirsts", property, PICK_FIRSTS, VAR},
    {"pickFirst", exactly(1), PICK_FIRSTS, AUGMENT_LIST}
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"doNotStrip", property, DO_NOT_STRIP, VAR},
    {"doNotStrip", exactly(1), DO_NOT_STRIP, AUGMENT_LIST},
    {"excludes", property, EXCLUDES, VAR},
    {"exclude", exactly(1), EXCLUDES, AUGMENT_LIST},
    {"merges", property, MERGES, VAR},
    {"merge", exactly(1), MERGES, AUGMENT_LIST},
    {"pickFirsts", property, PICK_FIRSTS, VAR},
    {"pickFirst", exactly(1), PICK_FIRSTS, AUGMENT_LIST}
  }).collect(toModelMap());

  public static final ImmutableMap<String,PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"dex", DexDslElement.DEX},
    {"jniLibs", JniLibsDslElement.JNI_LIBS},
    {"resources", ResourcesDslElement.RESOURCES},
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap);
  }

  @Override
  protected @NotNull ImmutableMap<String, PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  public PackagingOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
