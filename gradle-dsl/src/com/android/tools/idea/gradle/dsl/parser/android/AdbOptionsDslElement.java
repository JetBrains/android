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

import static com.android.tools.idea.gradle.dsl.model.android.AdbOptionsModelImpl.INSTALL_OPTIONS;
import static com.android.tools.idea.gradle.dsl.model.android.AdbOptionsModelImpl.TIME_OUT_IN_MS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
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

public class AdbOptionsDslElement extends GradleDslBlockElement {
  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"installOptions", property, INSTALL_OPTIONS, VAR},
    {"installOptions", atLeast(0), INSTALL_OPTIONS, ADD_AS_LIST},
    {"setInstallOptions", exactly(1), INSTALL_OPTIONS, SET},
    {"timeOutInMs", property, TIME_OUT_IN_MS, VAR},
    {"timeOutInMs", exactly(1), TIME_OUT_IN_MS, SET}
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"installOptions", property, INSTALL_OPTIONS, VAR},
    {"installOptions", atLeast(0), INSTALL_OPTIONS, ADD_AS_LIST},
    {"timeOutInMs", property, TIME_OUT_IN_MS, VAR},
    {"timeOutInMs", exactly(1), TIME_OUT_IN_MS, SET}
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"installOptions", property, INSTALL_OPTIONS, VAR},
    {"timeOutInMs", property, TIME_OUT_IN_MS, VAR},
  }).collect(toModelMap());
  public static final PropertiesElementDescription<AdbOptionsDslElement> ADB_OPTIONS =
    new PropertiesElementDescription<>("adbOptions",
                                       AdbOptionsDslElement.class,
                                       AdbOptionsDslElement::new);

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public AdbOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
