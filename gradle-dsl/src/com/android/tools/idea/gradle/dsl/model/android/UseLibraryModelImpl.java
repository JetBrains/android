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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.UseLibraryModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UseLibraryModelImpl implements UseLibraryModel {

  private final @NotNull GradleDslMethodCall myDslElement;

  private UseLibraryModelImpl(@NotNull GradleDslMethodCall element) { myDslElement = element; }

  @Override
  public @NotNull String name() {
    return myDslElement.getArguments().stream().findFirst().map(arg -> ((GradleDslLiteral)arg).getValue(String.class)).orElseThrow();
  }

  @Override
  public @Nullable PsiElement getPsiElement() {
    return myDslElement.getPsiElement();
  }

  static UseLibraryModel createNew(@NotNull GradlePropertiesDslElement parent, @NotNull String libraryName) {
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parent, GradleNameElement.empty(), USE_LIBRARY);
    GradleDslLiteral nameArgument = new GradleDslLiteral(methodCall, GradleNameElement.empty());
    nameArgument.setValue(libraryName);
    methodCall.addNewArgument(nameArgument);
    parent.setNewElement(methodCall);
    return new UseLibraryModelImpl(methodCall);
  }
}
