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
package com.android.tools.idea.projectsystem;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A service for storing and finding light R classes. */
public interface LightResourceClassService {
  /**
   * Returns all R classes with the given name that are inside the scope.
   */
  @NotNull
  Collection<? extends PsiClass> getLightRClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Returns all R classes that should be visible from the given module.
   */
  @NotNull
  Collection<? extends PsiClass> getLightRClassesAccessibleFromModule(@NotNull Module module);

  /**
   * Returns R classes defined by this module.
   */
  @NotNull
  Collection<? extends PsiClass> getLightRClassesDefinedByModule(@NotNull Module module);

  /**
   * Returns all R classes that may contain resources from the given module.
   *
   * <p>This is used for finding resource usages, by looking for fields with the matching resource type and name.
   */
  @NotNull
  Collection<? extends PsiClass> getLightRClassesContainingModuleResources(@NotNull Module module);

  /**
   * Returns the light package with the given name.
   */
  @Nullable
  PsiPackage findRClassPackage(@NotNull String qualifiedName);

  /**
   * Returns all light R classes.
   */
  @NotNull
  Collection<? extends PsiClass> getAllLightRClasses();
}
