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
package com.android.tools.idea.gradle.project.facet.ndk;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.intellij.ProjectTopics;
import com.intellij.facet.*;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NdkFacet extends Facet<NdkFacetConfiguration> {
  @NotNull private static final FacetTypeId<NdkFacet> TYPE_ID = new FacetTypeId<>("native-android-gradle");

  private NdkModuleModel myNdkModuleModel;

  @Nullable
  public static NdkFacet getInstance(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetByType(TYPE_ID);
  }

  public NdkFacet(@NotNull Module module,
                  @NotNull String name,
                  @NotNull NdkFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static NdkFacetType getFacetType() {
    FacetType facetType = FacetTypeRegistry.getInstance().findFacetType(getFacetId());
    assert facetType instanceof NdkFacetType;
    return (NdkFacetType)facetType;
  }

  @NotNull
  public static FacetTypeId<NdkFacet> getFacetTypeId() {
    return TYPE_ID;
  }

  @NotNull
  public static String getFacetId() {
    return "native-android-gradle";
  }

  @NotNull
  public static String getFacetName() {
    return "Native-Android-Gradle";
  }

  @Override
  public void initFacet() {
    MessageBusConnection connection = getModule().getMessageBus().connect(this);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!isDisposed()) {
            PsiDocumentManager.getInstance(getModule().getProject()).commitAllDocuments();
            updateConfiguration();
          }
        }, ModalityState.NON_MODAL);
      }
    });
    updateConfiguration();
  }

  private void updateConfiguration() {
    NdkFacetConfiguration config = getConfiguration();
    try {
      FacetUtil.saveFacetConfiguration(config);
    }
    catch (WriteExternalException e) {
      Logger.getInstance(NdkFacet.class).error("Unable to save contents of 'Native-Android-Gradle' facet", e);
    }
  }

  @Nullable
  public NdkModuleModel getNdkModuleModel() {
    return myNdkModuleModel;
  }

  public void setNdkModuleModel(@NotNull NdkModuleModel ndkModuleModel) {
    myNdkModuleModel = ndkModuleModel;

    getConfiguration().SELECTED_BUILD_VARIANT = myNdkModuleModel.getSelectedVariant().getName();
  }
}
