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
package com.android.tools.idea.gradle.editor.parser;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntityGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Defines contract for plugging into plugging into enhanced gradle editor model.
 */
public interface GradleEditorModelCustomizer {

  ExtensionPointName<GradleEditorModelCustomizer> EP_NAME = ExtensionPointName.create("com.android.gradle.gradleEditorModelCustomizer");

  /**
   * Allows to customize gradle editor entities to be shown at the UI-level later
   *
   * @param groups   current groups to be shown, should not be modified
   * @param context  context filled with the low-level information about target gradle file configuration
   * @return         groups to be shown further, i.e. given list if it's not modified or a new list with reduced/expanded contents
   */
  @NotNull
  List<GradleEditorEntityGroup> postProcess(@NotNull List<GradleEditorEntityGroup> groups, @NotNull GradleEditorModelParseContext context);
}
