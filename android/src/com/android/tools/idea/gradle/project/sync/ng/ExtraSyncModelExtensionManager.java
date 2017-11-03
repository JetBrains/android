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
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ExtraSyncModelExtensionManager {
  // TODO: Handle ExtraAndroidSyncModelExtension when android extensions are available.
  @NotNull private final List<ExtraJavaSyncModelExtension> myExtraJavaSyncModelExtensions;
  @NotNull private final Set<Class<?>> myExtraJavaModels;

  public ExtraSyncModelExtensionManager() {
    this(Arrays.asList(ExtraJavaSyncModelExtension.getExtensions()));
  }

  @VisibleForTesting
  ExtraSyncModelExtensionManager(@NotNull List<ExtraJavaSyncModelExtension> extraJavaSyncModelExtensions) {
    myExtraJavaSyncModelExtensions = extraJavaSyncModelExtensions;
    myExtraJavaModels = myExtraJavaSyncModelExtensions.stream()
      .map(ExtraSyncModelExtension::getExtraProjectModelClasses)
      .flatMap(Set::stream)
      .collect(Collectors.toSet());
  }

  @NotNull
  public Set<Class<?>> getExtraAndroidModels() {
    return Collections.emptySet();
  }

  @NotNull
  public Set<Class<?>> getExtraJavaModels() {
    return myExtraJavaModels;
  }

  public void setupExtraJavaModels(@NotNull SyncAction.ModuleModels moduleModels,
                                   @NotNull Project project,
                                   @NotNull Module module,
                                   @NotNull IdeModifiableModelsProvider modelsProvider) {
    for (ExtraSyncModelExtension moduleSyncAndSetupExtension : myExtraJavaSyncModelExtensions) {
      moduleSyncAndSetupExtension.setupExtraModels(moduleModels, project, module, modelsProvider);
    }
  }
}
