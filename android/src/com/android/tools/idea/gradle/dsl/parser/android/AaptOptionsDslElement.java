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

import static com.android.tools.idea.gradle.dsl.model.android.AaptOptionsModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AaptOptionsDslElement extends GradleDslBlockElement {
  @NonNls public static final String AAPT_OPTIONS_BLOCK_NAME = "aaptOptions";

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String, SemanticsDescription>> ktsToModelNameMap = Stream.of(new Object[][]{
    {"additionalParameters", property, ADDITIONAL_PARAMETERS, VAR},
    {"additionalParameters", atLeast(0), ADDITIONAL_PARAMETERS, OTHER},
    {"cruncherEnabled", property, CRUNCHER_ENABLED, VAR},
    {"cruncherProcesses", property, CRUNCHER_PROCESSES, VAR},
    {"failOnMissingConfigEntry", property, FAIL_ON_MISSING_CONFIG_ENTRY, VAR},
    {"failOnMissingConfigEntry", exactly(1), FAIL_ON_MISSING_CONFIG_ENTRY, SET},
    {"ignoreAssetsPattern", property, IGNORE_ASSETS, VAR},
    {"ignoreAssets", property, IGNORE_ASSETS, VAR},
    {"noCompress", property, NO_COMPRESS, VAL},
    {"noCompress", atLeast(0), NO_COMPRESS, OTHER},
    {"setNoCompress", exactly(1), NO_COMPRESS, SET}, // actually there are more setNoCompress() methods than just the pure setter
    {"namespaced", property, NAMESPACED, VAR},
    {"namespaced", exactly(1), NAMESPACED, SET}
  }).collect(toImmutableMap(data -> new Pair<>((String) data[0], (Integer) data[1]),
                            data -> new Pair<>((String) data[2], (SemanticsDescription) data[3])));

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> groovyToModelNameMap = Stream.of(new Object[][]{
    {"additionalParameters", property, ADDITIONAL_PARAMETERS, VAR},
    {"additionalParameters", atLeast(0), ADDITIONAL_PARAMETERS, OTHER},
    {"cruncherEnabled", property, CRUNCHER_ENABLED, VAR},
    {"cruncherEnabled", exactly(1), CRUNCHER_ENABLED, SET},
    {"cruncherProcesses", property, CRUNCHER_PROCESSES, VAR},
    {"cruncherProcesses", exactly(1), CRUNCHER_PROCESSES, SET},
    {"failOnMissingConfigEntry", property, FAIL_ON_MISSING_CONFIG_ENTRY, VAR},
    {"failOnMissingConfigEntry", exactly(1), FAIL_ON_MISSING_CONFIG_ENTRY, SET},
    {"ignoreAssetsPattern", property, IGNORE_ASSETS, VAR},
    {"ignoreAssetsPattern", exactly(1), IGNORE_ASSETS, SET},
    {"ignoreAssets", property, IGNORE_ASSETS, VAR},
    {"ignoreAssets", exactly(1), IGNORE_ASSETS, SET},
    {"noCompress", property, NO_COMPRESS, VAR},
    {"noCompress", atLeast(0), NO_COMPRESS, OTHER},
    {"namespaced", property, NAMESPACED, VAR},
    {"namespaced", exactly(1), NAMESPACED, SET}
  }).collect(toImmutableMap(data -> new Pair<>((String) data[0], (Integer) data[1]),
                            data -> new Pair<>((String) data[2], (SemanticsDescription) data[3])));

  @Override
  @NotNull
  public ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
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

  public AaptOptionsDslElement(@NotNull GradleDslElement parent) {
    super(parent, GradleNameElement.create(AAPT_OPTIONS_BLOCK_NAME));
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (element instanceof GradleDslSimpleExpression) {
      if (element.getName().equals("additionalParameters")) {
        addAsParsedDslExpressionList(ADDITIONAL_PARAMETERS, (GradleDslSimpleExpression)element);
        return;
      }
      else if (element.getName().equals("noCompress")) {
        addAsParsedDslExpressionList(NO_COMPRESS, (GradleDslSimpleExpression)element);
        return;
      }
    }
    super.addParsedElement(element);
  }
}
