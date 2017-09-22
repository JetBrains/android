/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ExtraGradleSyncModelsManager {
  @NotNull private final List<ExtraGradleSyncJavaModels> myJavaModels;
  @NotNull private final Set<Class<?>> myJavaModelTypes;

  @NotNull
  public static ExtraGradleSyncModelsManager getInstance() {
    return ServiceManager.getService(ExtraGradleSyncModelsManager.class);
  }

  public ExtraGradleSyncModelsManager() {
    this(Arrays.asList(ExtraGradleSyncJavaModels.getExtensions()));
  }

  @VisibleForTesting
  ExtraGradleSyncModelsManager(@NotNull List<ExtraGradleSyncJavaModels> javaModels) {
    myJavaModels = javaModels;
    myJavaModelTypes = new HashSet<>();
    for (ExtraGradleSyncJavaModels models : myJavaModels) {
      myJavaModelTypes.addAll(models.getModelTypes());
    }
  }

  @NotNull
  public Set<Class<?>> getAndroidModelTypes() {
    // TODO implement extension point for Android model
    return Collections.emptySet();
  }

  @NotNull
  public Set<Class<?>> getJavaModelTypes() {
    return myJavaModelTypes;
  }

  public void applyModelsToModule(@NotNull GradleModuleModels moduleModels,
                                  @NotNull Module module,
                                  @NotNull IdeModifiableModelsProvider modelsProvider) {
    for (ExtraGradleSyncModels models : myJavaModels) {
      models.applyModelsToModule(moduleModels, module, modelsProvider);
    }
  }
}
