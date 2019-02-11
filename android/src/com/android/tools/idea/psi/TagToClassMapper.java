/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.psi;

import com.android.support.AndroidxName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.psi.PsiClass;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TagToClassMapper {
  /**
   * Returns a map from tag names to {@link PsiClass} instances for all subclasses of either {@code frameworkClass} or {@code androidXClass}
   * that can be accessed from the current module.
   *
   * @param frameworkClass fully qualified name of the framework superclass
   * @param androidXClass fully qualified name of the AndroidX superclass, if it's known and it doesn't inherit from {@code frameworkClass}
   *                      itself
   */
  @NotNull
  Map<String, PsiClass> getClassMap(@NotNull String frameworkClass, @Nullable AndroidxName androidXClass);

  static TagToClassMapper getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, TagToClassMapper.class);
  }
}
