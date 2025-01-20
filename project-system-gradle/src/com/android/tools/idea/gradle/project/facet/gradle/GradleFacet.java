/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.facet.gradle;

import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.projectsystem.gradle.LinkedAndroidModuleGroupUtilsKt;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies a module as a "Gradle project".
 */
public class GradleFacet extends Facet<GradleFacetConfiguration> {
  public static final String ANDROID_GRADLE_FACET_ID = "android-gradle";
  public static final String ANDROID_GRADLE_FACET_NAME = "Android-Gradle";
  @NotNull private static final FacetTypeId<GradleFacet> TYPE_ID = new FacetTypeId<>("android-gradle");

  @Nullable private GradleModuleModel myGradleModuleModel;

  public static boolean isAppliedTo(@NotNull Module module) {
    return getInstance(module) != null;
  }

  @Nullable
  public static GradleFacet getInstance(@NotNull Module module) {
    if (module.isDisposed()) {
      Logger.getInstance(GradleFacet.class).warn(ANDROID_GRADLE_FACET_NAME + " facet is requested on a disposed module " + module);
      return null;
    }
    Module holderModule = LinkedAndroidModuleGroupUtilsKt.getHolderModule(module);
    if (holderModule.isDisposed()) {
      Logger.getInstance(GradleFacet.class).warn(ANDROID_GRADLE_FACET_NAME + " facet is requested on " + module + " but holder module is disposed " + holderModule);
      return null;
    }
    return FacetManager.getInstance(holderModule).getFacetByType(getFacetTypeId());
  }

  public GradleFacet(@NotNull Module module,
                     @NotNull String name,
                     @NotNull GradleFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static GradleFacetType getFacetType() {
    final var facetType = FacetTypeRegistry.getInstance().findFacetType(getFacetId());
    assert facetType instanceof GradleFacetType;
    return (GradleFacetType)facetType;
  }

  @NotNull
  public static FacetTypeId<GradleFacet> getFacetTypeId() {
    return TYPE_ID;
  }

  @NotNull
  public static String getFacetId() {
    return ANDROID_GRADLE_FACET_ID;
  }

  @NotNull
  public static String getFacetName() {
    return ANDROID_GRADLE_FACET_NAME;
  }

  @Nullable
  public GradleModuleModel getGradleModuleModel() {
    return myGradleModuleModel;
  }

  public void setGradleModuleModel(@NotNull GradleModuleModel gradleModuleModel) {
    myGradleModuleModel = gradleModuleModel;
    getConfiguration().LAST_KNOWN_AGP_VERSION = myGradleModuleModel.getAgpVersion();
  }
}
