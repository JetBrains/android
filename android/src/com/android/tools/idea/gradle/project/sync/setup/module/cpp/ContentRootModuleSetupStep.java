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
package com.android.tools.idea.gradle.project.sync.setup.module.cpp;

import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.module.CppModuleSetupStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;

public class ContentRootModuleSetupStep extends CppModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull NativeAndroidGradleModel androidModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    ModifiableRootModel moduleModel = ideModelsProvider.getModifiableRootModel(module);
    CppContentEntriesSetup setup = new CppContentEntriesSetup(androidModel, moduleModel);
    List<ContentEntry> contentEntries = findContentEntries(moduleModel, androidModel);
    setup.execute(contentEntries);
  }

  @NotNull
  private static List<ContentEntry> findContentEntries(@NotNull ModifiableRootModel moduleModel,
                                                       @NotNull NativeAndroidGradleModel androidModel) {
    return Lists.newArrayList(moduleModel.addContentEntry(pathToIdeaUrl(androidModel.getRootDirPath())));
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Source folder(s) setup";
  }

  @Override
  public boolean invokeOnBuildVariantChange() {
    return true;
  }
}
