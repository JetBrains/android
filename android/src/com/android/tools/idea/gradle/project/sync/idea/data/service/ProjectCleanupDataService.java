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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.PROJECT_CLEANUP_MODEL;

public class ProjectCleanupDataService extends AbstractProjectDataService<ProjectCleanupModel, Void> {
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final ProjectCleanup myProjectCleanup;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public ProjectCleanupDataService(@NotNull IdeInfo ideInfo) {
    this(ideInfo, new ProjectCleanup());
  }

  @VisibleForTesting
  ProjectCleanupDataService(@NotNull IdeInfo ideInfo, @NotNull ProjectCleanup projectCleanup) {
    myIdeInfo = ideInfo;
    myProjectCleanup = projectCleanup;
  }

  @Override
  @NotNull
  public Key<ProjectCleanupModel> getTargetDataKey() {
    return PROJECT_CLEANUP_MODEL;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ProjectCleanupModel>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    // IntelliJ supports several gradle projects linked to one IDEA project it will be separate processes for these gradle projects importing
    // also IntelliJ does not prevent to mix gradle projects with non-gradle ones.
    // See https://youtrack.jetbrains.com/issue/IDEA-137433
    if (toImport.isEmpty()) {
      return;
    }

    myProjectCleanup.cleanUpProject(project, modelsProvider, null);
  }
}
