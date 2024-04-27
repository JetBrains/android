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

import static com.android.tools.idea.gradle.dsl.model.android.KotlinOptionsModelImpl.FREE_COMPILER_ARGS;
import static com.android.tools.idea.gradle.dsl.model.android.KotlinOptionsModelImpl.JVM_TARGET;
import static com.android.tools.idea.gradle.dsl.model.android.KotlinOptionsModelImpl.USE_IR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
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

public class KotlinOptionsDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<KotlinOptionsDslElement> KOTLIN_OPTIONS =
    new PropertiesElementDescription<>("kotlinOptions",
                                       KotlinOptionsDslElement.class,
                                       KotlinOptionsDslElement::new,
                                       KotlinOptionsDslElementSchema::new);

  public static final ExternalToModelMap modelNameMap = Stream.of(new Object[][]{
    {"freeCompilerArgs", property, FREE_COMPILER_ARGS, VAR},
    {"jvmTarget", property, JVM_TARGET, VAR},
    {"useIR", property, USE_IR, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, modelNameMap, modelNameMap, modelNameMap);
  }

  public KotlinOptionsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class KotlinOptionsDslElementSchema extends GradlePropertiesDslElementSchema {
    @Override
    @NotNull
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, modelNameMap, modelNameMap, modelNameMap);
    }

    @Nullable
    @Override
    public String getAgpDocClass() {
      return "org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions";
    }
  }
}
