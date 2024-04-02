/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.model.android.DependenciesInfoModelImpl.INCLUDE_IN_APK;
import static com.android.tools.idea.gradle.dsl.model.android.DependenciesInfoModelImpl.INCLUDE_IN_BUNDLE;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
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
import org.jetbrains.annotations.Nullable;

public class DependenciesInfoDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<DependenciesInfoDslElement> DEPENDENCIES_INFO =
    new PropertiesElementDescription<>("dependenciesInfo",
                                       DependenciesInfoDslElement.class,
                                       DependenciesInfoDslElement::new,
                                       DependenciesInfoDslElementSchema::new);

  public static final ExternalToModelMap ktsToModelMap = Stream.of(new Object[][]{
    {"includeInApk", property, INCLUDE_IN_APK, VAR},
    {"includeInBundle", property, INCLUDE_IN_BUNDLE, VAR}
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelMap = Stream.of(new Object[][] {
    {"includeInApk", property, INCLUDE_IN_APK, VAR},
    {"includeInApk", exactly(1), INCLUDE_IN_APK, SET},
    {"includeInBundle", property, INCLUDE_IN_BUNDLE, VAR},
    {"includeInBundle", exactly(1), INCLUDE_IN_BUNDLE, SET}
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelMap = Stream.of(new Object[][]{
    {"includeInApk", property, INCLUDE_IN_APK, VAR},
    {"includeInBundle", property, INCLUDE_IN_BUNDLE, VAR}
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelMap, ktsToModelMap, declarativeToModelMap);
  }

  DependenciesInfoDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class DependenciesInfoDslElementSchema extends GradlePropertiesDslElementSchema {
    @Override
    @NotNull
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelMap, ktsToModelMap, declarativeToModelMap);
    }

    @Nullable
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.DependenciesInfo";
    }
  }
}
