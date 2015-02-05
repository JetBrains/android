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
package com.android.tools.idea.gradle.editor.entity;

import org.jetbrains.annotations.Nullable;

public interface GradleEntityDefinitionValueLocationAware {

  /**
   * Exposes target entity definition's value location.
   * <p/>
   * Example:
   * <pre>
   *   ext.COMPILE_SDK_VERSION = 21
   *   ...
   *   android {
   *     compileSdkVersion COMPILE_SDK_VERSION
   *   }
   * </pre>
   * Consider compile sdk declaration - it uses a variable declared elsewhere and this method returns location for
   * the <code>'21'</code> text from the <code>'ext.COMPILE_SDK_VERSION = 21'</code> statement.
   * <p/>
   * I.e. the general idea is to provide information which allows to modify exact entity value even if it's referenced via variable.
   *
   * @return    target entity definition's value location (if possible);
   *            <code>null</code> as an indication that there is no single definition value place
   */
  @Nullable
  GradleEditorSourceBinding getDefinitionValueLocation();
}
