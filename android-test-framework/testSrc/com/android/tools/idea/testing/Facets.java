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
package com.android.tools.idea.testing;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.intellij.facet.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public final class Facets {
  private Facets() {
  }

  @NotNull
  public static AndroidFacet createAndAddAndroidFacet(@NotNull Module module) {
    return createAndAddFacet(module, AndroidFacet.getFacetType(), AndroidFacet.NAME);
  }

  @NotNull
  public static ApkFacet createAndAddApkFacet(@NotNull Module module) {
    return createAndAddFacet(module, ApkFacet.getFacetType(), ApkFacet.getFacetName());
  }

  @NotNull
  public static JavaFacet createAndAddJavaFacet(@NotNull Module module) {
    return createAndAddFacet(module, JavaFacet.getFacetType(), JavaFacet.getFacetName());
  }

  @NotNull
  public static GradleFacet createAndAddGradleFacet(@NotNull Module module) {
    return createAndAddFacet(module, GradleFacet.getFacetType(), GradleFacet.getFacetName());
  }

  @NotNull
  public static NdkFacet createAndAddNdkFacet(@NotNull Module module) {
    return createAndAddFacet(module, NdkFacet.getFacetType(), NdkFacet.getFacetName());
  }

  @NotNull
  private static <F extends Facet, C extends FacetConfiguration> F createAndAddFacet(@NotNull Module module,
                                                                                     @NotNull FacetType<F, C> type,
                                                                                     @NotNull String name) {
    FacetManager facetManager = FacetManager.getInstance(module);
    F facet = facetManager.createFacet(type, name, null);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    facetModel.addFacet(facet);
    ApplicationManager.getApplication().runWriteAction(facetModel::commit);
    return facet;
  }
}
