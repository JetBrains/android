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

import com.android.tools.idea.gradle.dsl.api.android.DexOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DexOptionsModelImpl extends GradleDslBlockModel implements DexOptionsModel {
  @NonNls private static final String ADDITIONAL_PARAMETERS = "additionalParameters";
  @NonNls private static final String JAVA_MAX_HEAP_SIZE = "javaMaxHeapSize";
  @NonNls private static final String JUMBO_MODE = "jumboMode";
  @NonNls private static final String KEEP_RUNTIME_ANNOTATED_CLASSES = "keepRuntimeAnnotatedClasses";
  @NonNls private static final String MAX_PROCESS_COUNT = "maxProcessCount";
  @NonNls private static final String OPTIMIZE = "optimize";
  @NonNls private static final String PRE_DEX_LIBRARIES = "preDexLibraries";
  @NonNls private static final String THREAD_COUNT = "threadCount";

  public DexOptionsModelImpl(@NotNull DexOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel additionalParameters() {
    return getModelForProperty(ADDITIONAL_PARAMETERS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel javaMaxHeapSize() {
    return getModelForProperty(JAVA_MAX_HEAP_SIZE, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel jumboMode() {
    return getModelForProperty(JUMBO_MODE, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel keepRuntimeAnnotatedClasses() {
    return getModelForProperty(KEEP_RUNTIME_ANNOTATED_CLASSES, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel maxProcessCount() {
    return getModelForProperty(MAX_PROCESS_COUNT, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel optimize() {
    return getModelForProperty(OPTIMIZE, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel preDexLibraries() {
    return getModelForProperty(PRE_DEX_LIBRARIES, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel threadCount() {
    return getModelForProperty(THREAD_COUNT, true);
  }
}
