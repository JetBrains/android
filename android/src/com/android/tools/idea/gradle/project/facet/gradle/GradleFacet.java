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
import com.intellij.facet.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ProjectTopics.PROJECT_ROOTS;
import static com.intellij.facet.impl.FacetUtil.saveFacetConfiguration;
import static org.jetbrains.android.model.AndroidModelSerializationConstants.ANDROID_GRADLE_FACET_ID;
import static org.jetbrains.android.model.AndroidModelSerializationConstants.ANDROID_GRADLE_FACET_NAME;

/**
 * Identifies a module as a "Gradle project".
 */
public class GradleFacet extends Facet<GradleFacetConfiguration> {
  @NotNull private static final FacetTypeId<GradleFacet> TYPE_ID = new FacetTypeId<>("android-gradle");

  @Nullable private GradleModuleModel myGradleModuleModel;

  public static boolean isAppliedTo(@NotNull Module module) {
    return getInstance(module) != null;
  }

  @Nullable
  public static GradleFacet getInstance(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetByType(getFacetTypeId());
  }

  public GradleFacet(@NotNull Module module,
                     @NotNull String name,
                     @NotNull GradleFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static GradleFacetType getFacetType() {
    FacetType facetType = FacetTypeRegistry.getInstance().findFacetType(getFacetId());
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

  @Override
  public void initFacet() {
    MessageBusConnection connection = getModule().getMessageBus().connect(this);
    connection.subscribe(PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!isDisposed()) {
            PsiDocumentManager.getInstance(getModule().getProject()).commitAllDocuments();
            updateConfiguration();
          }
        });
      }
    });
    updateConfiguration();
  }

  private void updateConfiguration() {
    GradleFacetConfiguration config = getConfiguration();
    try {
      saveFacetConfiguration(config);
    }
    catch (WriteExternalException e) {
      Logger.getInstance(GradleFacet.class).error("Unable to save contents of 'Android-Gradle' facet", e);
    }
  }

  @Nullable
  public GradleModuleModel getGradleModuleModel() {
    return myGradleModuleModel;
  }

  public void setGradleModuleModel(@NotNull GradleModuleModel gradleModuleModel) {
    myGradleModuleModel = gradleModuleModel;
    getConfiguration().GRADLE_PROJECT_PATH = myGradleModuleModel.getGradlePath();
  }
}
