/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.facet;

import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.intellij.ProjectTopics;
import com.intellij.facet.*;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NativeAndroidGradleFacet extends Facet<NativeAndroidGradleFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance(NativeAndroidGradleFacet.class);

  @NotNull public static final FacetTypeId<NativeAndroidGradleFacet> TYPE_ID =
    new FacetTypeId<>("native-android-gradle");

  @NonNls public static final String ID = "native-android-gradle";
  @NonNls public static final String NAME = "Native-Android-Gradle";

  private NativeAndroidGradleModel myNativeAndroidGradleModel;

  @Nullable
  public static NativeAndroidGradleFacet getInstance(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetByType(TYPE_ID);
  }

  @SuppressWarnings("ConstantConditions")
  public NativeAndroidGradleFacet(@NotNull Module module,
                                  @NotNull String name,
                                  @NotNull NativeAndroidGradleFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static NativeAndroidGradleFacetType getFacetType() {
    FacetType facetType = FacetTypeRegistry.getInstance().findFacetType(ID);
    assert facetType instanceof NativeAndroidGradleFacetType;
    return (NativeAndroidGradleFacetType)facetType;
  }

  @Override
  public void initFacet() {
    MessageBusConnection connection = getModule().getMessageBus().connect(this);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
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
    NativeAndroidGradleFacetConfiguration config = getConfiguration();
    try {
      FacetUtil.saveFacetConfiguration(config);
    }
    catch (WriteExternalException e) {
      LOG.error("Unable to save contents of 'Native-Android-Gradle' facet", e);
    }
  }

  @Nullable
  public NativeAndroidGradleModel getNativeAndroidGradleModel() {
    return myNativeAndroidGradleModel;
  }

  public void setNativeAndroidGradleModel(@NotNull NativeAndroidGradleModel nativeAndroidGradleModel) {
    myNativeAndroidGradleModel = nativeAndroidGradleModel;

    getConfiguration().SELECTED_BUILD_VARIANT = myNativeAndroidGradleModel.getSelectedVariant().getName();
  }
}
