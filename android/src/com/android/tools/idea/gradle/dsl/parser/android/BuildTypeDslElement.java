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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class BuildTypeDslElement extends AbstractFlavorTypeDslElement {

  @NotNull
  private static final ImmutableMap<String, String> ktsToModelNameMap = Stream.concat(
    AbstractFlavorTypeDslElement.ktsToModelNameMap.entrySet().stream().map(data -> new String[]{data.getKey(), data.getValue()}),
    Stream.of(new String[][]{
      {"isDebuggable", BuildTypeModelImpl.DEBUGGABLE},
      {"isEmbedMicroApp", BuildTypeModelImpl.EMBED_MICRO_APP},
      {"isJniDebuggable", BuildTypeModelImpl.JNI_DEBUGGABLE},
      {"isMinifyEnabled", BuildTypeModelImpl.MINIFY_ENABLED},
      {"isPseudoLocalesEnabled", BuildTypeModelImpl.PSEUDO_LOCALES_ENABLED},
      {"isRenderscriptDebuggable", BuildTypeModelImpl.RENDERSCRIPT_DEBUGGABLE},
      {"renderscriptOptimLevel", BuildTypeModelImpl.RENDERSCRIPT_OPTIM_LEVEL},
      {"isShrinkResources", BuildTypeModelImpl.SHRINK_RESOURCES},
      {"isTestCoverageEnabled", BuildTypeModelImpl.TEST_COVERAGE_ENABLED},
      {"isZipAlignEnabled", BuildTypeModelImpl.ZIP_ALIGN_ENABLED}
    }))
    .collect(toImmutableMap(data -> data[0], data -> data[1]));

  @NotNull
  private static final ImmutableMap<String, String> groovyToModelNameMap = Stream.concat(
    AbstractFlavorTypeDslElement.groovyToModelNameMap.entrySet().stream().map(data -> new String[]{data.getKey(), data.getValue()}),
    Stream.of(new String[][]{
      {"debuggable", BuildTypeModelImpl.DEBUGGABLE},
      {"embedMicroApp", BuildTypeModelImpl.EMBED_MICRO_APP},
      {"jniDebuggable", BuildTypeModelImpl.JNI_DEBUGGABLE},
      {"minifyEnabled", BuildTypeModelImpl.MINIFY_ENABLED},
      {"pseudoLocalesEnabled", BuildTypeModelImpl.PSEUDO_LOCALES_ENABLED},
      {"renderscriptDebuggable", BuildTypeModelImpl.RENDERSCRIPT_DEBUGGABLE},
      {"renderscriptOptimLevel", BuildTypeModelImpl.RENDERSCRIPT_OPTIM_LEVEL},
      {"shrinkResources", BuildTypeModelImpl.SHRINK_RESOURCES},
      {"testCoverageEnabled", BuildTypeModelImpl.TEST_COVERAGE_ENABLED},
      {"zipAlignEnabled", BuildTypeModelImpl.ZIP_ALIGN_ENABLED}
    }))
    .collect(toImmutableMap(data -> data[0], data -> data[1]));

  @Override
  @NotNull
  public ImmutableMap<String, String> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
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

  public BuildTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    super.addParsedElement(element);
    maybeRenameElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    super.setParsedElement(element);
    maybeRenameElement(element);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    // "release" and "debug" Build Type blocks can be deleted if empty
    return myName.name().equals("release") || myName.name().equals("debug");
  }
}
