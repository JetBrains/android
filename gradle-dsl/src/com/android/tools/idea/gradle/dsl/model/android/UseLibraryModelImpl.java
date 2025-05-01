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

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.removeElement;

import com.android.tools.idea.gradle.dsl.api.android.UseLibraryModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.intellij.psi.PsiElement;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UseLibraryModelImpl implements UseLibraryModel {

  private final @NotNull GradleDslElement myDslElement;

  UseLibraryModelImpl(@NotNull GradleDslElement element) { myDslElement = element; }

  @Override
  public @NotNull String name() {
    if (myDslElement instanceof GradleDslMethodCall methodCall) {
      return methodCall.getArguments().stream().findFirst().map(arg -> ((GradleDslLiteral)arg).getValue(String.class)).orElseThrow();
    }
    if (myDslElement instanceof GradleDslLiteral literal) {
      return Objects.requireNonNull(literal.getValue(String.class));
    }
    throw new NoSuchElementException("unexpected GradleDslElement in UseLibraryModel" + myDslElement);
  }

  @Override
  public boolean required() {
    if (myDslElement instanceof GradleDslMethodCall methodCall) {
      return methodCall.getArguments().stream().skip(1).findFirst().map(arg -> ((GradleDslLiteral)arg).getValue(Boolean.class)).orElse(true);
    }
    if (myDslElement instanceof GradleDslLiteral) return true;
    throw new NoSuchElementException("unexpected GradleDslElement in UseLibraryModel" + myDslElement);
  }

  @Override
  public void delete() {
    removeElement(myDslElement);
  }

  @Override
  public @Nullable PsiElement getPsiElement() {
    return myDslElement.getPsiElement();
  }

  static UseLibraryModel createNew(@NotNull GradlePropertiesDslElement parent, @NotNull String libraryName) {
    return createNew(parent, libraryName, null);
  }

  static UseLibraryModel createNew(@NotNull GradlePropertiesDslElement parent, @NotNull String libraryName, @Nullable Boolean required) {
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parent, GradleNameElement.empty(), USE_LIBRARY);
    GradleDslLiteral nameArgument = new GradleDslLiteral(methodCall, GradleNameElement.empty());
    nameArgument.setValue(libraryName);
    methodCall.addNewArgument(nameArgument);
    if (required != null) {
      GradleDslLiteral requiredArgument = new GradleDslLiteral(methodCall, GradleNameElement.empty());
      requiredArgument.setValue(required);
      methodCall.addNewArgument(requiredArgument);
    }
    parent.setNewElement(methodCall);
    return new UseLibraryModelImpl(methodCall);
  }
}
