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
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that some required dependencies have lower version than required.
 */
public class DependenciesTooLowNotificationProvider implements EditorNotificationProvider {
  private static final Key<String> HIDDEN_KEY = Key.create("ml.deps.too.low.notification.panel.hidden");

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    return fileEditor -> {
      if (fileEditor.getUserData(HIDDEN_KEY) != null || !(fileEditor instanceof TfliteModelFileEditor)) {
        return null;
      }

      Module module = ModuleUtilCore.findModuleForFile(file, project);
      if (module == null || !MlUtils.isMlModelBindingBuildFeatureEnabled(module) || !MlUtils.isModelFileInMlModelsFolder(module, file)) {
        return null;
      }

      List<Pair<GradleCoordinate, GradleCoordinate>> depPairList = MlUtils.getDependenciesLowerThanRequiredVersion(module);
      if (!depPairList.isEmpty()) {
        EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
        panel.setText("ML Model Binding requires updated dependencies");
        panel.createActionLabel("View dependencies", () -> {
          String existingDepString = depPairList.stream()
            .map(it -> it.getFirst())
            .map(it -> String.format(Locale.US, "    %s:%s:%s\n", it.getGroupId(), it.getArtifactId(), it.getRevision()))
            .collect(Collectors.joining(""));
          String requiredDepString = depPairList.stream()
            .map(it -> it.getSecond())
            .map(it -> String.format(Locale.US, "    %s:%s:%s\n", it.getGroupId(), it.getArtifactId(), it.getRevision()))
            .collect(Collectors.joining(""));
          Messages.showWarningDialog(
            String.format(Locale.US, "Existing:\n%s\nRequired:\n%s", existingDepString, requiredDepString),
            "Dependencies Needing Updates");
        });
        panel.createActionLabel("Hide notification", () -> {
          fileEditor.putUserData(HIDDEN_KEY, "true");
          EditorNotifications.getInstance(project).updateNotifications(file);
        });
        return panel;
      }

      return null;
    };
  }
}
