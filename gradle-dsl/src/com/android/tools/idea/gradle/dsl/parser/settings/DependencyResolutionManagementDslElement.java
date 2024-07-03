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
package com.android.tools.idea.gradle.dsl.parser.settings;

import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class DependencyResolutionManagementDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<DependencyResolutionManagementDslElement> DEPENDENCY_RESOLUTION_MANAGEMENT =
    new PropertiesElementDescription<>("dependencyResolutionManagement", DependencyResolutionManagementDslElement.class,
                                       DependencyResolutionManagementDslElement::new);

  public static final String DEFAULT_LIBRARIES_EXTENSION_NAME = "mDefaultLibrariesExtensionName";

  public static final ImmutableMap<String, PropertiesElementDescription<?>> CHILD_PROPERTIES_ELEMENT_MAP = Stream.of(new Object[][]{
    {"repositories", RepositoriesDslElement.REPOSITORIES},
    {"versionCatalogs", VersionCatalogsDslElement.VERSION_CATALOGS},
  }).collect(toImmutableMap(data -> (String)data[0], data -> (PropertiesElementDescription<?>)data[1]));

  private static final ExternalToModelMap externalToModelMap = Stream.of(new Object[][]{
    {"defaultLibrariesExtensionName", property, DEFAULT_LIBRARIES_EXTENSION_NAME, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return externalToModelMap;
  }

  @Override
  public @NotNull ImmutableMap<String, PropertiesElementDescription<?>> getChildPropertiesElementsDescriptionMap(
    GradleDslNameConverter.Kind kind
  ) {
    return CHILD_PROPERTIES_ELEMENT_MAP;
  }

  public DependencyResolutionManagementDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
