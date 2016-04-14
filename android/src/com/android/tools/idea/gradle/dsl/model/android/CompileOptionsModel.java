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

import com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModel;
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompileOptionsModel extends BaseCompileOptionsModel {
  @NonNls public static String ENCODING_ATTRIBUTE_NAME = "encoding";

  public CompileOptionsModel(@NotNull BaseCompileOptionsDslElement dslElement, boolean useAssignment) {
    super(dslElement, useAssignment);
  }

  @Nullable
  public String encoding() {
    return myDslElement.getProperty(ENCODING_ATTRIBUTE_NAME, String.class);
  }

  @NotNull
  public CompileOptionsModel setEncoding(@NotNull String encoding) {
    myDslElement.setNewLiteral(ENCODING_ATTRIBUTE_NAME, encoding);
    return this;
  }

  @NotNull
  public CompileOptionsModel removeEncoding() {
    myDslElement.removeProperty(ENCODING_ATTRIBUTE_NAME);
    return this;
  }
}
