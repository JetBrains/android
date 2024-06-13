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
package com.android.tools.idea.gradle.dsl.parser.android.productFlavors;

import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl.ABI_FILTERS;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl.C_FLAGS;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl.JOBS;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl.LD_LIBS;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl.MODULE_NAME;
import static com.android.tools.idea.gradle.dsl.model.android.productFlavors.NdkOptionsModelImpl.STL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_LIST;
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

public class NdkOptionsDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<NdkOptionsDslElement> NDK_OPTIONS =
    new PropertiesElementDescription<>("ndk",
                                       NdkOptionsDslElement.class,
                                       NdkOptionsDslElement::new,
                                       NdkOptionsDslElementSchema::new);

  private static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"abiFilters", property, ABI_FILTERS, VAL},
    {"cFlags", property, C_FLAGS, VAR},
    {"jobs", property, JOBS, VAR},
    {"ldLibs", property, LD_LIBS, VAL},
    {"moduleName", property, MODULE_NAME, VAR},
    {"stl", property, STL, VAR},
  }).collect(toModelMap());

  private static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"abiFilters", property, ABI_FILTERS, VAR},
    {"abiFilters", atLeast(0), ABI_FILTERS, AUGMENT_LIST},
    {"abiFilter", exactly(1), ABI_FILTERS, AUGMENT_LIST},
    {"cFlags", property, C_FLAGS, VAR},
    {"cFlags", exactly(1), C_FLAGS, SET},
    {"jobs", property, JOBS, VAR},
    {"jobs", exactly(1), JOBS, SET},
    {"ldLibs", property, LD_LIBS, VAR},
    {"ldLibs", atLeast(0), LD_LIBS, AUGMENT_LIST},
    {"moduleName", property, MODULE_NAME, VAR},
    {"moduleName", exactly(1), MODULE_NAME, SET},
    {"stl", property, STL, VAR},
    {"stl", exactly(1), STL, SET},
  }).collect(toModelMap());

  private static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"abiFilters", property, ABI_FILTERS, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public NdkOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class NdkOptionsDslElementSchema extends GradlePropertiesDslElementSchema {
    @NotNull
    @Override
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
    }

    @NotNull
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.Ndk";
    }
  }
}
