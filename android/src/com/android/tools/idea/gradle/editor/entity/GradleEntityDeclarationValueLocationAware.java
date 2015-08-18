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

import org.jetbrains.annotations.NotNull;

public interface GradleEntityDeclarationValueLocationAware {

  /**
   * Exposes target entity declaration's value location.
   * <p/>
   * Example:
   * <pre>
   *   ext.COMPILE_SDK_VERSION = 21
   *   ...
   *   android {
   *     compileSdkVersion COMPILE_SDK_VERSION
   *   }
   * </pre>
   * Consider compile sdk declaration - it uses a variable declared elsewhere, however, this method returns location for
   * the <code>'COMPILE_SDK_VERSION'</code> form the <code>'compileSdkVersion COMPILE_SDK_VERSION'</code> statement.
   * <p/>
   * I.e. the general idea is to provide information which allows to programmatically change particular entity's value at the
   * declaration place.
   *
   * @return    target entity declaration's value location
   */
  @NotNull
  GradleEditorSourceBinding getDeclarationValueLocation();
}
