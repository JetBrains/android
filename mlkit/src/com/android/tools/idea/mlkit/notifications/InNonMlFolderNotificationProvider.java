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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.mlkit.LoggingUtils;
import com.android.tools.idea.mlkit.MlkitUtils;
import com.android.tools.idea.mlkit.TfliteModelFileEditor;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that a model file is not in the correct ml folder in order to use ML model binding feature.
 */
public class InNonMlFolderNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("ml.incorrect.folder.notification.panel");
  private static final Key<String> HIDDEN_KEY = Key.create("ml.incorrect.folder.notification.panel.hidden");

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
    if (!StudioFlags.ML_MODEL_BINDING.get()
        || fileEditor.getUserData(HIDDEN_KEY) != null
        || !(fileEditor instanceof TfliteModelFileEditor)) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null && !MlkitUtils.isModelFileInMlModelsFolder(module, file)) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText("This TensorFlow Lite model is not in a configured ml-model directory, model binding is disabled.");
      panel.createActionLabel("Move file", () -> ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          AndroidFacet androidFacet = AndroidFacet.getInstance(module);
          if (androidFacet == null) {
            Logger.getInstance(InNonMlFolderNotificationProvider.class).error("Can't find the Android Facet.");
            return;
          }

          SourceProviders sourceProviders = SourceProviders.getInstance(androidFacet);
          Optional<VirtualFile> targetMlDir = sourceProviders.getCurrentAndSomeFrequentlyUsedInactiveSourceProviders().stream()
            .flatMap(sourceProvider -> sourceProvider.getMlModelsDirectories().stream())
            .filter(mlDir -> VfsUtilCore.isAncestor(mlDir.getParent(), file, true))
            .findFirst();
          if (targetMlDir.isPresent()) {
            try {
              file.move(this, targetMlDir.get());
              LoggingUtils.logEvent(EventType.MODEL_IMPORT_FROM_MOVE_FILE_BUTTON, file);
            }
            catch (IOException e) {
              Logger.getInstance(InNonMlFolderNotificationProvider.class)
                .error(String.format("Error moving %s to %s.", file, targetMlDir.get()), e);
            }
          }
          else {
            // TODO(b/153573353): handle the case where the ml folders are custom configured.
            Logger.getInstance(InNonMlFolderNotificationProvider.class).error("Can't determine the target ml folder.");
          }
        }
      }));
      panel.createActionLabel("Hide notification", () -> {
        fileEditor.putUserData(HIDDEN_KEY, "true");
        EditorNotifications.getInstance(project).updateNotifications(file);
      });
      return panel;
    }

    return null;
  }
}
