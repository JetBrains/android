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

import com.android.tools.idea.gradle.dsl.api.android.UseLibrariesModel;
import com.android.tools.idea.gradle.dsl.api.android.UseLibraryModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
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
}
