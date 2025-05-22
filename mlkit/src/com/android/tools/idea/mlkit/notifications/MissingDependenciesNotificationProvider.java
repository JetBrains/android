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

import static com.android.tools.idea.mlkit.viewer.TfliteModelFileType.TFLITE_EXTENSION;

import com.android.ide.common.gradle.Dependency;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.mlkit.MlUtils;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileEditor;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that some required dependencies are missing for ML Model Binding feature.
 */
public class MissingDependenciesNotificationProvider implements EditorNotificationProvider {

  private static final Key<String> HIDDEN_KEY = Key.create("ml.missing.deps.notification.panel.hidden");

  @Nullable
  @Override
  public Function<FileEditor, EditorNotificationPanel> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    if (!TFLITE_EXTENSION.equals(file.getExtension())) return null;
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null || !MlUtils.isMlModelBindingBuildFeatureEnabled(module)) {
      return null;
    }
    if (!MlUtils.isModelFileInMlModelsFolder(module, file)
        || MlUtils.getMissingRequiredDependencies(module).isEmpty()) {
      return null;
    }
    return (fileEditor) -> {
      if (fileEditor.getUserData(HIDDEN_KEY) != null
          || !(fileEditor instanceof TfliteModelFileEditor)) {
        return null;
      }
      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
      panel.setText("ML Model Binding dependencies not found.");
      panel.createActionLabel("Add Now", () -> {
        List<GradleCoordinate> depsToAdd = MlUtils.getMissingRequiredDependencies(module);
        String message = createAddDependencyMessage(depsToAdd);
        if (Messages.OK == Messages.showOkCancelDialog(project, message, "Add Ml Model Binding Dependencies", Messages.getErrorIcon())) {
          GradleDependencyManager manager = GradleDependencyManager.getInstance(project);
          manager.addDependencies(module, depsToAdd.stream().map(it -> Dependency.parse(it.toString())).toList());
          ProjectSystemUtil.getSyncManager(module.getProject()).requestSyncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED);
        }
      });
      panel.createActionLabel("Hide notification", () -> {
        fileEditor.putUserData(HIDDEN_KEY, "true");
        EditorNotifications.getInstance(project).updateNotifications(file);
      });
      return panel;
    };
  }

  private static @NotNull String createAddDependencyMessage(@NotNull List<GradleCoordinate> coordinates) {
    String libraryNames = StringUtil.join(coordinates, ", ");
    String these = StringUtil.pluralize("this", coordinates.size());
    String libraries = StringUtil.pluralize("library", coordinates.size());
    StringBuilder sb = new StringBuilder();
    sb.append("This operation requires the ").append(libraries).append(" ").append(libraryNames).append(".\n\n");
    sb.append("Would you like to add ").append(these).append(" now?");
    return sb.toString();
  }
}
