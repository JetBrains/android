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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.JavaModuleSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;

public class JavaModuleModelDataService extends ModuleModelDataService<JavaModuleModel> {
  @NotNull private final JavaModuleSetup myModuleSetup;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public JavaModuleModelDataService() {
    this(new JavaModuleSetup());
  }

  @VisibleForTesting
  JavaModuleModelDataService(@NotNull JavaModuleSetup moduleSetup) {
    myModuleSetup = moduleSetup;
  }

  @Override
  @NotNull
  public Key<JavaModuleModel> getTargetDataKey() {
    return JAVA_MODULE_MODEL;
  }

  @Override
  protected void importData(@NotNull Collection<DataNode<JavaModuleModel>> toImport,
                            @NotNull Project project,
                            @NotNull IdeModifiableModelsProvider modelsProvider,
                            @NotNull Map<String, JavaModuleModel> modelsByName) {
    for (Module module : modelsProvider.getModules()) {
      JavaModuleModel javaModuleModel = modelsByName.get(module.getName());
      if (javaModuleModel != null) {
        myModuleSetup.setUpModule(module, modelsProvider, javaModuleModel, null, null);
      }
    }
  }
}
