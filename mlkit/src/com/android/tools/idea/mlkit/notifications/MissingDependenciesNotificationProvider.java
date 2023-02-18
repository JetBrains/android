/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.notifications;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.mlkit.MlUtils;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileEditor;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that some required dependencies are missing for ML Model Binding feature.
 */
public class MissingDependenciesNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("ml.missing.deps.notification.panel");
  private static final Key<String> HIDDEN_KEY = Key.create("ml.missing.deps.notification.panel.hidden");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    if (fileEditor.getUserData(HIDDEN_KEY) != null
        || !(fileEditor instanceof TfliteModelFileEditor)) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null || !MlUtils.isMlModelBindingBuildFeatureEnabled(module)) {
      return null;
    }

    if (MlUtils.isModelFileInMlModelsFolder(module, file)
        && !MlUtils.getMissingRequiredDependencies(module).isEmpty()) {
      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
      panel.setText("ML Model Binding dependencies not found.");
      panel.createActionLabel("Add Now", () -> {
        List<GradleCoordinate> depsToAdd = MlUtils.getMissingRequiredDependencies(module);
        // TODO(b/149224613): switch to use DependencyManagementUtil#addDependencies.
        AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);
        if (DependencyManagementUtil.userWantsToAdd(module.getProject(), depsToAdd, "")) {
          for (GradleCoordinate dep : depsToAdd) {
            moduleSystem.registerDependency(dep);
          }
          ProjectSystemUtil.getSyncManager(module.getProject()).syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED);
        }
      });
      panel.createActionLabel("Hide notification", () -> {
        fileEditor.putUserData(HIDDEN_KEY, "true");
        EditorNotifications.getInstance(project).updateNotifications(file);
      });
      return panel;
    }

    return null;
  }
}
