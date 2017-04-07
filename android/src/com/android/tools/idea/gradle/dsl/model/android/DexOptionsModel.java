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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DexOptionsModel extends GradleDslBlockModel {
  @NonNls private static final String ADDITIONAL_PARAMETERS = "additionalParameters";
  @NonNls private static final String JAVA_MAX_HEAP_SIZE = "javaMaxHeapSize";
  @NonNls private static final String JUMBO_MODE = "jumboMode";
  @NonNls private static final String KEEP_RUNTIME_ANNOTATED_CLASSES = "keepRuntimeAnnotatedClasses";
  @NonNls private static final String MAX_PROCESS_COUNT = "maxProcessCount";
  @NonNls private static final String OPTIMIZE = "optimize";
  @NonNls private static final String PRE_DEX_LIBRARIES = "preDexLibraries";
  @NonNls private static final String THREAD_COUNT = "threadCount";

  public DexOptionsModel(@NotNull DexOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Nullable
  public List<GradleNotNullValue<String>> additionalParameters() {
    return myDslElement.getListProperty(ADDITIONAL_PARAMETERS, String.class);
  }

  @NotNull
  public DexOptionsModel addAdditionalParameter(@NotNull String additionalParameter) {
    myDslElement.addToNewLiteralList(ADDITIONAL_PARAMETERS, additionalParameter);
    return this;
  }

  @NotNull
  public DexOptionsModel removeAdditionalParameter(@NotNull String additionalParameter) {
    myDslElement.removeFromExpressionList(ADDITIONAL_PARAMETERS, additionalParameter);
    return this;
  }

  @NotNull
  public DexOptionsModel removeAllAdditionalParameters() {
    myDslElement.removeProperty(ADDITIONAL_PARAMETERS);
    return this;
  }

  @NotNull
  public DexOptionsModel replaceAdditionalParameter(@NotNull String oldAdditionalParameter, @NotNull String newAdditionalParameter) {
    myDslElement.replaceInExpressionList(ADDITIONAL_PARAMETERS, oldAdditionalParameter, newAdditionalParameter);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> javaMaxHeapSize() {
    return myDslElement.getLiteralProperty(JAVA_MAX_HEAP_SIZE, String.class);
  }

  @NotNull
  public DexOptionsModel setJavaMaxHeapSize(@NotNull String javaMaxHeapSize) {
    myDslElement.setNewLiteral(JAVA_MAX_HEAP_SIZE, javaMaxHeapSize);
    return this;
  }

  @NotNull
  public DexOptionsModel removeJavaMaxHeapSize() {
    myDslElement.removeProperty(JAVA_MAX_HEAP_SIZE);
    return this;
  }

  @NotNull
  public GradleNullableValue<Boolean> jumboMode() {
    return myDslElement.getLiteralProperty(JUMBO_MODE, Boolean.class);
  }

  @NotNull
  public DexOptionsModel setJumboMode(boolean jumboMode) {
    myDslElement.setNewLiteral(JUMBO_MODE, jumboMode);
    return this;
  }

  @NotNull
  public DexOptionsModel removeJumboMode() {
    myDslElement.removeProperty(JUMBO_MODE);
    return this;
  }

  @NotNull
  public GradleNullableValue<Boolean> keepRuntimeAnnotatedClasses() {
    return myDslElement.getLiteralProperty(KEEP_RUNTIME_ANNOTATED_CLASSES, Boolean.class);
  }

  @NotNull
  public DexOptionsModel setKeepRuntimeAnnotatedClasses(boolean keepRuntimeAnnotatedClasses) {
    myDslElement.setNewLiteral(KEEP_RUNTIME_ANNOTATED_CLASSES, keepRuntimeAnnotatedClasses);
    return this;
  }

  @NotNull
  public DexOptionsModel removeKeepRuntimeAnnotatedClasses() {
    myDslElement.removeProperty(KEEP_RUNTIME_ANNOTATED_CLASSES);
    return this;
  }

  @NotNull
  public GradleNullableValue<Integer> maxProcessCount() {
    return myDslElement.getLiteralProperty(MAX_PROCESS_COUNT, Integer.class);
  }

  @NotNull
  public DexOptionsModel setMaxProcessCount(int maxProcessCount) {
    myDslElement.setNewLiteral(MAX_PROCESS_COUNT, maxProcessCount);
    return this;
  }

  @NotNull
  public DexOptionsModel removeMaxProcessCount() {
    myDslElement.removeProperty(MAX_PROCESS_COUNT);
    return this;
  }

  @NotNull
  public GradleNullableValue<Boolean> optimize() {
    return myDslElement.getLiteralProperty(OPTIMIZE, Boolean.class);
  }

  @NotNull
  public DexOptionsModel setOptimize(boolean optimize) {
    myDslElement.setNewLiteral(OPTIMIZE, optimize);
    return this;
  }

  @NotNull
  public DexOptionsModel removeOptimize() {
    myDslElement.removeProperty(OPTIMIZE);
    return this;
  }

  @NotNull
  public GradleNullableValue<Boolean> preDexLibraries() {
    return myDslElement.getLiteralProperty(PRE_DEX_LIBRARIES, Boolean.class);
  }

  @NotNull
  public DexOptionsModel setPreDexLibraries(boolean preDexLibraries) {
    myDslElement.setNewLiteral(PRE_DEX_LIBRARIES, preDexLibraries);
    return this;
  }

  @NotNull
  public DexOptionsModel removePreDexLibraries() {
    myDslElement.removeProperty(PRE_DEX_LIBRARIES);
    return this;
  }

  @NotNull
  public GradleNullableValue<Integer> threadCount() {
    return myDslElement.getLiteralProperty(THREAD_COUNT, Integer.class);
  }

  @NotNull
  public DexOptionsModel setThreadCount(int threadCount) {
    myDslElement.setNewLiteral(THREAD_COUNT, threadCount);
    return this;
  }

  @NotNull
  public DexOptionsModel removeThreadCount() {
    myDslElement.removeProperty(THREAD_COUNT);
    return this;
  }
}
