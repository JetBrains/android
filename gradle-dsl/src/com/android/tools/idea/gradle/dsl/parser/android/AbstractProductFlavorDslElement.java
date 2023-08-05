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

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.*;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription.CREATE_WITH_VALUE;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.*;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

/**
 * Common base class for {@link ProductFlavorDslElement} and {@link DefaultConfigDslElement}
 */
public abstract class AbstractProductFlavorDslElement extends AbstractFlavorTypeDslElement {
  public static final ImmutableMap<String, PropertiesElementDescription> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"externalNativeBuild", ExternalNativeBuildOptionsDslElement.EXTERNAL_NATIVE_BUILD_OPTIONS},
    {"ndk", NdkOptionsDslElement.NDK_OPTIONS},
    {"vectorDrawables", VectorDrawablesOptionsDslElement.VECTOR_DRAWABLES_OPTIONS}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  @Override
  @NotNull
  protected ImmutableMap<String,PropertiesElementDescription> getChildPropertiesElementsDescriptionMap() {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"applicationId", property, APPLICATION_ID, VAR},
    {"setApplicationId", exactly(1), APPLICATION_ID, SET},
    {"isDefault", property, DEFAULT, VAR},
    {"dimension", property, DIMENSION, VAR},
    {"setDimension", exactly(1), DIMENSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"maxSdk", property, MAX_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"maxSdkVersion", property, MAX_SDK_VERSION, VAR, VersionConstraint.agpBefore("8.0.0")},
    {"maxSdkVersion", exactly(1), MAX_SDK_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"minSdk", property, MIN_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"minSdkPreview", property, MIN_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"minSdkVersion", exactly(1), MIN_SDK_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"missingDimensionStrategy", atLeast(1), MISSING_DIMENSION_STRATEGY, OTHER}, // ADD
    {"renderscriptTargetApi", property, RENDER_SCRIPT_TARGET_API, VAR},
    {"renderscriptSupportModeEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_ENABLED, VAR},
    {"renderscriptSupportModeBlasEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED, VAR},
    {"renderscriptNdkModeEnabled", property, RENDER_SCRIPT_NDK_MODE_ENABLED, VAR},
    {"resConfigs", atLeast(0), RES_CONFIGS, AUGMENT_LIST, VersionConstraint.agpBefore("8.0.0")},
    {"resConfig", exactly(1), RES_CONFIGS, AUGMENT_LIST, VersionConstraint.agpBefore("8.0.0")},
    {"resourceConfigurations", property, RES_CONFIGS, VAL, VersionConstraint.agpFrom("4.1.0")},
    {"targetSdk", property, TARGET_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"targetSdkPreview", property, TARGET_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"targetSdkVersion", exactly(1), TARGET_SDK_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"testApplicationId", property, TEST_APPLICATION_ID, VAR},
    {"setTestApplicationId", exactly(1), TEST_APPLICATION_ID, SET},
    {"testFunctionalTest", property, TEST_FUNCTIONAL_TEST, VAR},
    {"setTestFunctionalTest", exactly(1), TEST_FUNCTIONAL_TEST, SET, VersionConstraint.agpBefore("8.0.0")},
    {"testHandleProfiling", property, TEST_HANDLE_PROFILING, VAR},
    {"setTestHandleProfiling", exactly(1), TEST_HANDLE_PROFILING, SET, VersionConstraint.agpBefore("8.0.0")},
    {"testInstrumentationRunner", property, TEST_INSTRUMENTATION_RUNNER, VAR},
    {"testInstrumentationRunner", exactly(1), TEST_INSTRUMENTATION_RUNNER, SET},
    {"testInstrumentationRunnerArguments", property, TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS, VersionConstraint.agpBefore("4.1.0")},
    {"testInstrumentationRunnerArguments", property, TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, VAL, VersionConstraint.agpFrom("4.1.0")},
    {"testInstrumentationRunnerArguments", exactly(1), TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, AUGMENT_MAP, VersionConstraint.agpBefore("8.0.0")},
    {"setTestInstrumentationRunnerArguments", exactly(1), TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, SET, VersionConstraint.agpBefore("8.0.0")},
    {"versionCode", property, VERSION_CODE, VAR},
    {"setVersionCode", exactly(1), VERSION_CODE, SET},
    {"versionName", property, VERSION_NAME, VAR},
    {"setVersionName", exactly(1), VERSION_NAME, SET},
    {"wearAppUnbundled", property, WEAR_APP_UNBUNDLED, VAR}
  }).collect(toModelMap(AbstractFlavorTypeDslElement.ktsToModelNameMap));

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"applicationId", property, APPLICATION_ID, VAR},
    {"applicationId", exactly(1), APPLICATION_ID, SET},
    {"isDefault", property, DEFAULT, VAR},
    {"isDefault", exactly(1), DEFAULT, SET},
    {"dimension", property, DIMENSION, VAR},
    {"dimension", exactly(1), DIMENSION, SET},
    {"setDimension", exactly(1), DIMENSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"maxSdk", property, MAX_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"maxSdk", exactly(1), MAX_SDK_VERSION, SET, VersionConstraint.agpFrom("4.1.0")},
    {"maxSdkVersion", property, MAX_SDK_VERSION, VAR, VersionConstraint.agpBefore("8.0.0")},
    {"maxSdkVersion", exactly(1), MAX_SDK_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"minSdk", property, MIN_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"minSdk", exactly(1), MIN_SDK_VERSION, SET, VersionConstraint.agpFrom("4.1.0")},
    {"minSdkPreview", property, MIN_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"minSdkPreview", exactly(1), MIN_SDK_VERSION, SET, VersionConstraint.agpFrom("4.1.0")},
    {"minSdkVersion", property, MIN_SDK_VERSION, VAR, VersionConstraint.agpBefore("8.0.0")},
    {"minSdkVersion", exactly(1), MIN_SDK_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"missingDimensionStrategy", atLeast(1), MISSING_DIMENSION_STRATEGY, OTHER},
    {"renderscriptTargetApi", property, RENDER_SCRIPT_TARGET_API, VAR},
    {"renderscriptTargetApi", exactly(1), RENDER_SCRIPT_TARGET_API, SET},
    {"renderscriptSupportModeEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_ENABLED, VAR},
    {"renderscriptSupportModeEnabled", exactly(1), RENDER_SCRIPT_SUPPORT_MODE_ENABLED, SET},
    {"renderscriptSupportModeBlasEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED, VAR},
    {"renderscriptSupportModeBlasEnabled", exactly(1), RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED, SET},
    {"renderscriptNdkModeEnabled", property, RENDER_SCRIPT_NDK_MODE_ENABLED, VAR},
    {"renderscriptNdkModeEnabled", exactly(1), RENDER_SCRIPT_NDK_MODE_ENABLED, SET},
    {"resConfigs", atLeast(0), RES_CONFIGS, AUGMENT_LIST, VersionConstraint.agpBefore("8.0.0")},
    {"resConfig", exactly(1), RES_CONFIGS, AUGMENT_LIST, VersionConstraint.agpBefore("8.0.0")},
    {"resourceConfigurations", property, RES_CONFIGS, VAL, VersionConstraint.agpFrom("4.1.0")},
    {"targetSdk", property, TARGET_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"targetSdk", exactly(1), TARGET_SDK_VERSION, SET, VersionConstraint.agpFrom("4.1.0")},
    {"targetSdkPreview", property, TARGET_SDK_VERSION, VAR, VersionConstraint.agpFrom("4.1.0")},
    {"targetSdkPreview", exactly(1), TARGET_SDK_VERSION, SET, VersionConstraint.agpFrom("4.1.0")},
    {"targetSdkVersion", property, TARGET_SDK_VERSION, VAR, VersionConstraint.agpBefore("8.0.0")},
    {"targetSdkVersion", exactly(1), TARGET_SDK_VERSION, SET, VersionConstraint.agpBefore("8.0.0")},
    {"testApplicationId", property, TEST_APPLICATION_ID, VAR},
    {"testApplicationId", exactly(1), TEST_APPLICATION_ID, SET},
    {"testFunctionalTest", property, TEST_FUNCTIONAL_TEST, VAR},
    {"testFunctionalTest", exactly(1), TEST_FUNCTIONAL_TEST, SET},
    {"setTestFunctionalTest", exactly(1), TEST_FUNCTIONAL_TEST, SET, VersionConstraint.agpBefore("8.0.0")},
    {"testHandleProfiling", property, TEST_HANDLE_PROFILING, VAR},
    {"testHandleProfiling", exactly(1), TEST_HANDLE_PROFILING, SET},
    {"setTestHandleProfiling", exactly(1), TEST_HANDLE_PROFILING, SET, VersionConstraint.agpBefore("8.0.0")},
    {"testInstrumentationRunner", property, TEST_INSTRUMENTATION_RUNNER, VAR},
    {"testInstrumentationRunner", exactly(1), TEST_INSTRUMENTATION_RUNNER, SET},
    {"testInstrumentationRunnerArguments", property, TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, VAR},
    {"testInstrumentationRunnerArguments", exactly(1), TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, AUGMENT_MAP},
    {"setTestInstrumentationRunnerArguments", exactly(1), TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, SET, VersionConstraint.agpBefore("8.0.0")},
    {"versionCode", property, VERSION_CODE, VAR},
    {"versionCode", exactly(1), VERSION_CODE, SET},
    {"versionName", property, VERSION_NAME, VAR},
    {"versionName", exactly(1), VERSION_NAME, SET},
    {"wearAppUnbundled", property, WEAR_APP_UNBUNDLED, VAR},
    {"wearAppUnbundled", exactly(1), WEAR_APP_UNBUNDLED, SET}
  }).collect(toModelMap(AbstractFlavorTypeDslElement.groovyToModelNameMap));

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"applicationId", property, APPLICATION_ID, VAR},
    {"isDefault", property, DEFAULT, VAR},
    {"dimension", property, DIMENSION, VAR},
    {"maxSdk", property, MAX_SDK_VERSION, VAR},
    {"minSdk", property, MIN_SDK_VERSION, VAR},
    {"minSdkPreview", property, MIN_SDK_VERSION, VAR},
    {"missingDimensionStrategy", atLeast(1), MISSING_DIMENSION_STRATEGY, AUGMENT_LIST},
    {"renderscriptTargetApi", property, RENDER_SCRIPT_TARGET_API, VAR},
    {"renderscriptSupportModeEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_ENABLED, VAR},
    {"renderscriptSupportModeBlasEnabled", property, RENDER_SCRIPT_SUPPORT_MODE_BLAS_ENABLED, VAR},
    {"renderscriptNdkModeEnabled", property, RENDER_SCRIPT_NDK_MODE_ENABLED, VAR},
    {"resourceConfigurations", property, RES_CONFIGS, VAL},
    {"targetSdk", property, TARGET_SDK_VERSION, VAR},
    {"targetSdkPreview", property, TARGET_SDK_VERSION, VAR},
    {"testApplicationId", property, TEST_APPLICATION_ID, VAR},
    {"testFunctionalTest", property, TEST_FUNCTIONAL_TEST, VAR},
    {"testHandleProfiling", property, TEST_HANDLE_PROFILING, VAR},
    {"testInstrumentationRunner", property, TEST_INSTRUMENTATION_RUNNER, VAR},
    {"testInstrumentationRunnerArguments", property, TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, VAR},
    {"versionCode", property, VERSION_CODE, VAR},
    {"versionName", property, VERSION_NAME, VAR},
    {"wearAppUnbundled", property, WEAR_APP_UNBUNDLED, VAR}
  }).collect(toModelMap(AbstractFlavorTypeDslElement.declarativeToModelNameMap));

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap,declarativeToModelNameMap);
  }

  AbstractProductFlavorDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
    GradleDslExpressionMap testInstrumentationRunnerArguments =
      new GradleDslExpressionMap(this, GradleNameElement.fake(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS.name));
    ModelEffectDescription effect = new ModelEffectDescription(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, CREATE_WITH_VALUE);
    testInstrumentationRunnerArguments.setModelEffect(effect);
    testInstrumentationRunnerArguments.setElementType(REGULAR);
    addDefaultProperty(testInstrumentationRunnerArguments);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();

    // this method has the same name in Kotlin and Groovy
    if (property.equals("missingDimensionStrategy") && element instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
      GradleDslExpressionList argumentList = methodCall.getArgumentsElement();
      ModelEffectDescription effect = new ModelEffectDescription(new ModelPropertyDescription(MISSING_DIMENSION_STRATEGY), OTHER);
      argumentList.setModelEffect(effect);
      super.addParsedElement(argumentList);
      return;
    }

    // testInstrumentationRunnerArgument has the same name in Groovy and Kotlin
    if (property.equals("testInstrumentationRunnerArgument")) {
      if (element instanceof GradleDslMethodCall) {
        element = ((GradleDslMethodCall)element).getArgumentsElement();
      }
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
        getPropertyElement(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, GradleDslExpressionMap.class);
      if (testInstrumentationRunnerArgumentsElement == null) {
        testInstrumentationRunnerArgumentsElement =
          new GradleDslExpressionMap(this, GradleNameElement.create(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS.name));
        setParsedElement(testInstrumentationRunnerArgumentsElement);
      }
      testInstrumentationRunnerArgumentsElement.setParsedElement(value);
      // This is not theoretically sound, but...
      ModelEffectDescription effect =
        new ModelEffectDescription(TEST_INSTRUMENTATION_RUNNER_ARGUMENTS, CREATE_WITH_VALUE, VersionConstraint.agpBefore("8.0.0"));
      testInstrumentationRunnerArgumentsElement.setModelEffect(effect);
      if (testInstrumentationRunnerArgumentsElement.getPsiElement() == null) {
        testInstrumentationRunnerArgumentsElement.setPsiElement(element.getPsiElement());
      }
      return;
    }

    super.addParsedElement(element);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    // defaultConfig is special in that is can be deleted if it is empty.
    return this instanceof DefaultConfigDslElement;
  }
}
