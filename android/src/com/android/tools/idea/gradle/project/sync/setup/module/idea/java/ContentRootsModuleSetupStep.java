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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetupStep;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntriesSetup.removeExistingContentEntries;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;

public class ContentRootsModuleSetupStep extends JavaModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull JavaModuleModel javaModuleModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    ModifiableRootModel moduleModel = ideModelsProvider.getModifiableRootModel(module);
    JavaContentEntriesSetup setup = new JavaContentEntriesSetup(javaModuleModel, moduleModel);
    List<ContentEntry> contentEntries = findContentEntries(moduleModel, javaModuleModel);
    setup.execute(contentEntries);
  }

  @NotNull
  private static List<ContentEntry> findContentEntries(@NotNull ModifiableRootModel moduleModel, @NotNull JavaModuleModel javaModuleModel) {
    removeExistingContentEntries(moduleModel);

    List<ContentEntry> contentEntries = new ArrayList<>();
    for (JavaModuleContentRoot contentRoot : javaModuleModel.getContentRoots()) {
      File rootDirPath = contentRoot.getRootDirPath();
      ContentEntry contentEntry = moduleModel.addContentEntry(pathToIdeaUrl(rootDirPath));
      contentEntries.add(contentEntry);
    }
    return contentEntries;
  }

  @Override
  public boolean invokeOnSkippedSync() {
    return true;
  }
}
