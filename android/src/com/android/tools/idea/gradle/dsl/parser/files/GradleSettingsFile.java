/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.model.GradleSettingsModelImpl.INCLUDE;

public class GradleSettingsFile extends GradleDslFile {
  public GradleSettingsFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    super(file, project, moduleName);
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (INCLUDE.equals(property)) {
      addToParsedExpressionList(property, element);
      return;
    }
    super.addParsedElement(property, element);
  }
}