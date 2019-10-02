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

import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

public final class ProductFlavorDslElement extends AbstractFlavorTypeDslElement {

  @NotNull
  public static final ImmutableMap<String, String> ktsToModelNameMap = Stream.of(new String[][]{
    // FIXME(xof): a few of these, despite having getFoo() and setFoo() methods, don't allow setting through the implicit setter foo = ...
    //  (setDimension, setTestFunctionalTest, setTestHandleProfiling)
    //  why not?  And is it too cute to pun using the setter method, or is that in fact the Right Thing?
    //
    // FIXME(b/142111082): it is too cute, in fact.  This trick works for parsing and for creating new properties (with cooperation from
    //  ModelImpl.asMethod() builders) but doesn't work for resolution.
    {"applicationId", ProductFlavorModelImpl.APPLICATION_ID},
    {"setDimension", ProductFlavorModelImpl.DIMENSION},
    {"maxSdkVersion", ProductFlavorModelImpl.MAX_SDK_VERSION},
    {"minSdkVersion", ProductFlavorModelImpl.MIN_SDK_VERSION},
    {"missingDimensionStrategy", ProductFlavorModelImpl.MISSING_DIMENSION_STRATEGY}, // FIXME(xof): missingDimensionStrategies?
    {"renderscriptTargetApi", ProductFlavorModelImpl.RENDER_SCRIPT_TARGET_API},
    {"renderscriptSupportModeEnabled", ProductFlavorModelImpl.RENDER_SCRIPT_SUPPORT_MODE_ENABLED},
    {"renderscriptSupportModeBlasEnabled", ProductFlavorModelImpl.RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED},
    {"renderscriptNdkModeEnabled", ProductFlavorModelImpl.RENDER_SCRIPT_NDK_MODE_ENABLED},
    {"resConfigs", ProductFlavorModelImpl.RES_CONFIGS},
    {"targetSdkVersion", ProductFlavorModelImpl.TARGET_SDK_VERSION},
    {"testApplicationId", ProductFlavorModelImpl.TEST_APPLICATION_ID},
    {"setTestFunctionalTest", ProductFlavorModelImpl.TEST_FUNCTIONAL_TEST},
    {"setTestHandleProfiling", ProductFlavorModelImpl.TEST_HANDLE_PROFILING},
    {"testInstrumentationRunner", ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER},
    {"testInstrumentationRunnerArguments", ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER_ARGUMENTS},
    {"versionCode", ProductFlavorModelImpl.VERSION_CODE},
    {"versionName", ProductFlavorModelImpl.VERSION_NAME},
    {"wearAppUnbundled", ProductFlavorModelImpl.WEAR_APP_UNBUNDLED}
  })
    .collect(toImmutableMap(data -> data[0], data -> data[1]));

  @NotNull
  public static final ImmutableMap<String, String> groovyToModelNameMap = Stream.of(new String[][]{
    {"applicationId", ProductFlavorModelImpl.APPLICATION_ID},
    {"dimension", ProductFlavorModelImpl.DIMENSION},
    {"maxSdkVersion", ProductFlavorModelImpl.MAX_SDK_VERSION},
    {"minSdkVersion", ProductFlavorModelImpl.MIN_SDK_VERSION},
    {"missingDimensionStrategy", ProductFlavorModelImpl.MISSING_DIMENSION_STRATEGY}, // FIXME(xof): missingDimensionStrategies?
    {"renderscriptTargetApi", ProductFlavorModelImpl.RENDER_SCRIPT_TARGET_API},
    {"renderscriptSupportModeEnabled", ProductFlavorModelImpl.RENDER_SCRIPT_SUPPORT_MODE_ENABLED},
    {"renderscriptSupportModeBlasEnabled", ProductFlavorModelImpl.RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED},
    {"renderscriptNdkModeEnabled", ProductFlavorModelImpl.RENDER_SCRIPT_NDK_MODE_ENABLED},
    {"resConfigs", ProductFlavorModelImpl.RES_CONFIGS},
    {"targetSdkVersion", ProductFlavorModelImpl.TARGET_SDK_VERSION},
    {"testApplicationId", ProductFlavorModelImpl.TEST_APPLICATION_ID},
    {"testFunctionalTest", ProductFlavorModelImpl.TEST_FUNCTIONAL_TEST},
    {"testHandleProfiling", ProductFlavorModelImpl.TEST_HANDLE_PROFILING},
    {"testInstrumentationRunner", ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER},
    {"testInstrumentationRunnerArguments", ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER_ARGUMENTS},
    {"versionCode", ProductFlavorModelImpl.VERSION_CODE},
    {"versionName", ProductFlavorModelImpl.VERSION_NAME},
    {"wearAppUnbundled", ProductFlavorModelImpl.WEAR_APP_UNBUNDLED}
  }).collect(toImmutableMap(data -> data[0], data -> data[1]));

  @Override
  public ImmutableMap<String, String> getExternalToModelMap(GradleDslNameConverter converter) {
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

  public ProductFlavorDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  // FIXME(xof): this is identical to BuildTypeDslElement.maybeRenameElement() with the comments deleted.  Make the BuildTypeDslElement
  //  version available through an interface somewhere and delete this version
  private void maybeRenameElement(@NotNull GradleDslElement element) {
    String name = element.getName();
    Map<String,String> nameMapper = getExternalToModelMap(element.getDslFile().getParser());
    if (nameMapper.containsKey(name)) {
      String newName = nameMapper.get(name);
      element.getNameElement().canonize(newName);  // NOTYPO
    }
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();

    // these two methods have the same names in both currently-supported languages (Kotlin and Groovy)
    if (property.equals("resConfigs") || property.equals("resConfig")) {
      addToParsedExpressionList(ProductFlavorModelImpl.RES_CONFIGS, element);
      return;
    }

    // testInstrumentationRunnerArguments has the same name in Groovy and Kotlin
    if (property.equals("testInstrumentationRunnerArguments")) {
      // This deals with references to maps.
      GradleDslElement oldElement = element;
      if (element instanceof GradleDslLiteral && ((GradleDslLiteral)element).isReference()) {
        element = followElement((GradleDslLiteral) element);
      }
      if (!(element instanceof GradleDslExpressionMap)) {
        return;
      }

      GradleDslExpressionMap testInstrumentationRunnerArgumentsElement =
        getPropertyElement(ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        oldElement.getNameElement().rename(ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER_ARGUMENTS);
        testInstrumentationRunnerArgumentsElement =
          new GradleDslExpressionMap(this, element.getPsiElement(), oldElement.getNameElement(), true);
        setParsedElement(testInstrumentationRunnerArgumentsElement);
      }

      testInstrumentationRunnerArgumentsElement.setPsiElement(element.getPsiElement());
      GradleDslExpressionMap elementsToAdd = (GradleDslExpressionMap)element;
      for (Map.Entry<String, GradleDslElement> entry : elementsToAdd.getPropertyElements().entrySet()) {
        testInstrumentationRunnerArgumentsElement.setParsedElement(entry.getValue());
      }
      return;
    }

    // testInstrumentationRunnerArgument has the same name in Groovy and Kotlin
    if (property.equals("testInstrumentationRunnerArgument")) {
      if (!(element instanceof GradleDslExpressionList)) {
        return;
      }
      GradleDslExpressionList gradleDslExpressionList = (GradleDslExpressionList)element;
      List<GradleDslSimpleExpression> elements = gradleDslExpressionList.getSimpleExpressions();
      if (elements.size() != 2) {
        return;
      }

      String key = elements.get(0).getValue(String.class);
      if (key == null) {
        return;
      }
      GradleDslSimpleExpression value = elements.get(1);
      // Set the name element of the value to be the previous element.
      value.getNameElement().commitNameChange(elements.get(0).getPsiElement(), this.getDslFile().getWriter(), this);

      GradleDslExpressionMap testInstrumentationRunnerArgumentsElement =
        getPropertyElement(ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement =
          new GradleDslExpressionMap(this, GradleNameElement.create(ProductFlavorModelImpl.TEST_INSTRUMENTATION_RUNNER_ARGUMENTS));
      }
      testInstrumentationRunnerArgumentsElement.setParsedElement(value);
      return;
    }

    maybeRenameElement(element);
    super.addParsedElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    // FIXME(xof): investigate whether any of the addParsedElement() cleverness needs to be implemented in setParsedElement
    maybeRenameElement(element);
    super.setParsedElement(element);
  }
}
