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

import static com.android.tools.idea.gradle.dsl.model.android.CompileOptionsModelImpl.ENCODING;
import static com.android.tools.idea.gradle.dsl.model.android.CompileOptionsModelImpl.INCREMENTAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class CompileOptionsDslElement extends BaseCompileOptionsDslElement {
  public static final PropertiesElementDescription<CompileOptionsDslElement> COMPILE_OPTIONS =
    new PropertiesElementDescription<>("compileOptions",
                                       CompileOptionsDslElement.class,
                                       CompileOptionsDslElement::new);

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"encoding", property, ENCODING, VAR},
    {"incremental", property, INCREMENTAL, VAR}
  }).collect(toModelMap(BaseCompileOptionsDslElement.ktsToModelNameMap));

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"encoding", property, ENCODING, VAR},
    {"encoding", exactly(1), ENCODING, SET},
    {"incremental", property, INCREMENTAL, VAR},
    {"incremental", exactly(1), INCREMENTAL, SET},
  }).collect(toModelMap(BaseCompileOptionsDslElement.groovyToModelNameMap));

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"encoding", property, ENCODING, VAR},
    {"incremental", property, INCREMENTAL, VAR}
  }).collect(toModelMap(BaseCompileOptionsDslElement.declarativeToModelNameMap));

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public CompileOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
