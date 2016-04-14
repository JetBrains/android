/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.dependency;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.Facets;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * An IDEA module's dependency on another IDEA module.
 */
public class ModuleDependency extends Dependency {
  @NotNull private final String myGradlePath;

  @Nullable private LibraryDependency myBackupDependency;

  /**
   * Creates a new {@link ModuleDependency}.
   *
   * @param gradlePath the Gradle path of the project that maps to the IDEA module to depend on.
   * @param scope      the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  @VisibleForTesting
  public ModuleDependency(@NotNull String gradlePath, @NotNull DependencyScope scope) {
    super(scope);
    myGradlePath = gradlePath;
  }

  @Nullable
  public Module getModule(@NotNull IdeModifiableModelsProvider modelsProvider) {
    for (Module module : modelsProvider.getModules()) {
      AndroidGradleFacet gradleFacet = Facets.findFacet(module, modelsProvider, AndroidGradleFacet.TYPE_ID);
      if (gradleFacet != null && hasEqualPath(gradleFacet)) {
        return module;
      }
    }
    return null;
  }

  @Nullable
  public Module getModule(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null && hasEqualPath(gradleFacet)) {
        return module;
      }
    }
    return null;
  }

  private boolean hasEqualPath(@NotNull AndroidGradleFacet facet) {
    String gradlePath = facet.getConfiguration().GRADLE_PROJECT_PATH;
    return isNotEmpty(gradlePath) && gradlePath.equals(getGradlePath());
  }

  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  /**
   * @return the backup library that can be used as dependency in case it is not possible to use the module dependency (e.g. the module is
   * outside the project and we don't have the path of the module folder.)
   */
  @Nullable
  public LibraryDependency getBackupDependency() {
    return myBackupDependency;
  }

  @VisibleForTesting
  public void setBackupDependency(@Nullable LibraryDependency backupDependency) {
    myBackupDependency = backupDependency;
    updateBackupDependencyScope();
  }

  /**
   * Sets the scope of this dependency. It also updates the scope of this dependency's backup dependency if it is not {@code null}.
   *
   * @param scope the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  @Override
  void setScope(@NotNull DependencyScope scope) throws IllegalArgumentException {
    super.setScope(scope);
    updateBackupDependencyScope();
  }

  private void updateBackupDependencyScope() {
    if (myBackupDependency != null) {
      myBackupDependency.setScope(getScope());
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "gradlePath=" + myGradlePath +
           ", scope=" + getScope() +
           ", backUpDependency=" + myBackupDependency +
           "]";
  }
}
