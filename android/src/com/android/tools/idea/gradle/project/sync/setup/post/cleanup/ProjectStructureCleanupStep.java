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
package com.android.tools.idea.gradle.project.sync.setup.post.cleanup;

import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanupStep;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.android.tools.idea.gradle.util.GradleUtil.getNativeAndroidProject;

public class ProjectStructureCleanupStep extends ProjectCleanupStep {
  @NotNull private final AndroidSdks myAndroidSdks;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public ProjectStructureCleanupStep(@NotNull AndroidSdks androidSdks) {
    myAndroidSdks = androidSdks;
  }

  @Override
  public void cleanUpProject(@NotNull Project project,
                             @NotNull IdeModifiableModelsProvider ideModifiableModelsProvider,
                             @Nullable ProgressIndicator indicator) {
    Set<Sdk> androidSdks = new HashSet<>();

    for (Module module : ideModifiableModelsProvider.getModules()) {
      ModifiableRootModel rootModel = ideModifiableModelsProvider.getModifiableRootModel(module);

      Sdk sdk = rootModel.getSdk();
      if (sdk != null) {
        if (myAndroidSdks.isAndroidSdk(sdk)) {
          androidSdks.add(sdk);
        }
        continue;
      }

      NativeAndroidProject nativeAndroidProject = getNativeAndroidProject(module);
      if (nativeAndroidProject != null) {
        // Native modules does not need any jdk entry.
        continue;
      }

      Sdk jdk = IdeSdks.getInstance().getJdk();
      rootModel.setSdk(jdk);
    }

    for (Sdk sdk : androidSdks) {
      ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
        @Override
        public void execute() {
          myAndroidSdks.refreshLibrariesIn(sdk);
        }
      });
    }
  }
}
