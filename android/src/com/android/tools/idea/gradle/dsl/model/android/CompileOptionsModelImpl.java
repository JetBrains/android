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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CompileOptionsModelImpl extends BaseCompileOptionsModelImpl implements
                                                                         CompileOptionsModel {
  @NonNls private static final String ENCODING = "encoding";
  @NonNls private static final String INCREMENTAL = "incremental";

  public CompileOptionsModelImpl(@NotNull BaseCompileOptionsDslElement dslElement, boolean useAssignment) {
    super(dslElement, useAssignment);
  }

  @Override
  @NotNull
  public GradleNullableValue<String> encoding() {
    return myDslElement.getLiteralProperty(ENCODING, String.class);
  }

  @Override
  @NotNull
  public CompileOptionsModel setEncoding(@NotNull String encoding) {
    myDslElement.setNewLiteral(ENCODING, encoding);
    return this;
  }

  @Override
  @NotNull
  public CompileOptionsModel removeEncoding() {
    myDslElement.removeProperty(ENCODING);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> incremental() {
    return myDslElement.getLiteralProperty(INCREMENTAL, Boolean.class);
  }

  @Override
  @NotNull
  public CompileOptionsModel setIncremental(boolean incremental) {
    myDslElement.setNewLiteral(INCREMENTAL, incremental);
    return this;
  }

  @Override
  @NotNull
  public CompileOptionsModel removeIncremental() {
    myDslElement.removeProperty(INCREMENTAL);
    return this;
  }
}
