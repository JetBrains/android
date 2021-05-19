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

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public interface TagToClassMapper {

  /**
   * Returns a map from tag names to {@link PsiClass} instances for all subclasses of {@code className} that can be accessed from the
   * current module and can be used as tags in XML files.
   *
   * @param className fully qualified name of the superclass
   */
  @NotNull
  Map<String, PsiClass> getClassMap(String className);

  /**
   * Returns cache state for a particular class map that can be accessed from the current module.
   *
   * @param className fully qualified name of the superclass
   */
  @NotNull
  ClassMapFreshness getClassMapFreshness(String className);

  enum ClassMapFreshness {
    REBUILD_ENTIRE_CLASS_MAP,
    REBUILD_PARTIAL_CLASS_MAP,
    VALID_CLASS_MAP
  }

  @TestOnly
  void resetAllClassMaps();

  static TagToClassMapper getInstance(@NotNull Module module) {
    return module.getService(TagToClassMapper.class);
  }
}
