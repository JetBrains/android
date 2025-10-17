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

import static com.android.tools.idea.gradle.dsl.api.android.UseLibraryModel.USE_LIBRARY;

import com.android.tools.idea.gradle.dsl.api.android.UseLibrariesModel;
import com.android.tools.idea.gradle.dsl.api.android.UseLibraryModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class UseLibrariesModelImpl implements UseLibrariesModel {

  private final @NotNull GradlePropertiesDslElement myDslElement;

  public UseLibrariesModelImpl(@NotNull GradlePropertiesDslElement element) { myDslElement = element; }

  @Override
  public @NotNull UseLibraryModel create(@NotNull String libraryName) {
    return UseLibraryModelImpl.createNew(myDslElement, libraryName);
  }

  @Override
  public @NotNull UseLibraryModel create(@NotNull String libraryName, boolean required) {
    return UseLibraryModelImpl.createNew(myDslElement, libraryName, required);
  }

  @Override
  public @NotNull List<UseLibraryModel> find(@NotNull String libraryName) {
    List<UseLibraryModel> result = new ArrayList<>();
    for (GradleDslElement element : myDslElement.getChildren()) {
      if (isUseLibraryElement(element)) {
        UseLibraryModel model = new UseLibraryModelImpl(element);
        if (libraryName.equals(model.name())) {
          result.add(model);
        }
      }
    }
    return result;
  }

  private boolean isUseLibraryElement(@NotNull GradleDslElement element) {
    if (element instanceof GradleDslMethodCall methodCall) {
      if (!USE_LIBRARY.equals(methodCall.getMethodName())) return false;
      int size = methodCall.getArguments().size();
      if (size == 0 || size > 2) return false;
      return true;
    }
    if (element instanceof GradleDslLiteral literal) {
      if (!USE_LIBRARY.equals(literal.getName())) return false;
      return true;
    }
    return false;
  }
}
