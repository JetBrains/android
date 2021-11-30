/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeBuildTypeContainer;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeProductFlavorContainer;
import com.android.tools.idea.gradle.model.IdeSourceProvider;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * DEPRECATED: AndroidModuleModel is being deprecated and will eventually be deleted. Callers are being migrated to AndroidModel and
 * AndroidProject/ModuleSystem APIs. Those callers needing Gradle specific features should, for now, depend on gradle-project-system.
 */
public interface AndroidModuleModel extends AndroidModel, ModuleModel {
  @NotNull IdeDependencies getSelectedMainCompileLevel2Dependencies();

  @Nullable IdeDependencies getSelectedAndroidTestCompileDependencies();

  @NotNull GradleVersion getAgpVersion();

  @NotNull List<IdeSourceProvider> getActiveSourceProviders();

  @NotNull List<IdeSourceProvider> getUnitTestSourceProviders();

  @NotNull List<IdeSourceProvider> getAndroidTestSourceProviders();

  @NotNull List<IdeSourceProvider> getTestFixturesSourceProviders();

  @Nullable IdeBuildTypeContainer findBuildType(@NotNull String name);

  @Nullable IdeProductFlavorContainer findProductFlavor(@NotNull String name);

  @NotNull File getRootDirPath();

  @NotNull IdeAndroidProject getAndroidProject();

  @NotNull IdeVariant getSelectedVariant();

  String getSelectedVariantName();

  @NotNull ImmutableList<IdeVariant> getVariants();

  @Nullable LanguageLevel getJavaLanguageLevel();

  @Nullable IdeAndroidArtifact getArtifactForAndroidTest();

  @Nullable IdeAndroidArtifact getArtifactForTestFixtures();

  @Nullable
  static AndroidModuleModel get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  static AndroidModuleModel get(@NotNull AndroidFacet androidFacet) {
    AndroidModel androidModel = AndroidModel.get(androidFacet);
    return androidModel instanceof AndroidModuleModel ? (AndroidModuleModel)androidModel : null;
  }
}
