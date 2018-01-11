/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface TagToClassMapper {
  /**
   * Returns a map from tag names to {@link PsiClass} instances for all subclasses of the given {@code className} that can be accessed
   * from the current module.
   */
  @NotNull
  Map<String, PsiClass> getClassMap(@NotNull String className);

  static TagToClassMapper getInstance(@NotNull Module module) {
    return ModuleServiceManager.getService(module, TagToClassMapper.class);
  }
}
