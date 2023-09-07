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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.JacocoModelImpl.VERSION;
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
import org.jetbrains.annotations.Nullable;

public class JacocoDslElement extends GradleDslBlockElement {
  /** the Dsl element is named "jacoco" even though the API interface is JacocoOptions */
  public static final PropertiesElementDescription<JacocoDslElement> JACOCO =
    new PropertiesElementDescription<>("jacoco",
                                       JacocoDslElement.class,
                                       JacocoDslElement::new,
                                       JacocoDslElementSchema::new);

  public static final ExternalToModelMap ktsToModelMap = Stream.of(new Object[][]{
    {"version", property, VERSION, VAR},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelMap = Stream.of(new Object[][]{
    {"version", property, VERSION, VAR},
    {"version", exactly(1), VERSION, SET},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelMap = Stream.of(new Object[][]{
    {"version", property, VERSION, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelMap, ktsToModelMap, declarativeToModelMap);
  }

  public JacocoDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class JacocoDslElementSchema extends GradlePropertiesDslElementSchema {
    @Override
    @NotNull
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelMap, ktsToModelMap, declarativeToModelMap);
    }

    @Nullable
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.JacocoOptions";
    }
  }
}
