/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.model.android.CompileSdkBlockModelImpl.VERSION;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Top dsl element class to support following structure for new
 * compileSdk API
 * ```
 * android {
 *   compileSdk {
 *     version = release(33) {
 *       // this block and its properties are optional
 *       minorApiLevel = 1
 *       sdkExtension = 18
 *     }
 *   }
 * }
 * ```
 * or to use a preview version:
 * ```
 * android {
 *   compileSdk {
 *     version = preview("Tiramisu")
 *   }
 * }
 * ```
 */
public class CompileSdkBlockDslElement extends GradleDslBlockElement implements GradleDslNamedDomainContainer {
  public static final PropertiesElementDescription<CompileSdkBlockDslElement> COMPILE_SDK =
    new PropertiesElementDescription<>("compileSdk",
                                       CompileSdkBlockDslElement.class,
                                       CompileSdkBlockDslElement::new);

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"version", property, VERSION, VAR}
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"version", property, VERSION, VAR},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"version", property, VERSION, VAR},
  }).collect(toModelMap());

  protected CompileSdkBlockDslElement(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  @Override
  public boolean implicitlyExists(@NotNull String name) {
    return false;
  }
}
