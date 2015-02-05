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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntityGroup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * We assume that android gradle plugin dsl always evolves, so, we want to be able to easily adapt to new (incompatible) changes.
 * <p/>
 * That's why we encapsulate logic which builds actual {@link GradleEditorEntity entities} from generic low-level data derived
 * from AST/PSI ({@link GradleEditorModelParseContext}).
 * <p/>
 * E.g. we might have an implementation which works with gradle android plugin version, say, up to 1.0 and another implementation
 * which works with all plugin versions greater than 1.0.
 */
public interface GradleEditorModelParser {

  /**
   * @return  minimum supported gradle plugin version (inclusive)
   */
  @NotNull
  GradleCoordinate getMinSupportedAndroidGradlePluginVersion();

  /**
   * @return    maximum supported gradle plugin version (exclusive)
   */
  @NotNull
  GradleCoordinate getMaxSupportedAndroidGradlePluginVersion();

  /**
   * Builds actual {@link GradleEditorEntity entities} from the given low-level data extracted from the AST/PSI.
   *
   * @param context  holder for a low-level data extracted from the AST/PSI
   * @return         {@link GradleEditorEntity entities} to use
   */
  @NotNull
  List<GradleEditorEntityGroup> buildEntities(@NotNull GradleEditorModelParseContext context);
}
