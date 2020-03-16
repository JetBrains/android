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
import com.android.tools.idea.mlkit.MlkitUtils;
import com.android.tools.idea.mlkit.TfliteModelFileEditor;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that build feature flag mlModelBinding is off.
 */
public class BuildFeatureOffNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("ml.build.feature.off.notification.panel");
  private static final Key<String> HIDDEN_KEY = Key.create("ml.build.feature.off.notification.panel.hidden");

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
    if (module != null && !MlkitUtils.isMlModelBindingBuildFeatureEnabled(module) && MlkitUtils.isModelFileInMlModelsFolder(module, file)) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText(
        "Tensorflow Lite model binding build feature is disabled. To configure your app to use model binding, " +
        "enable the feature in your build.gradle file.");
      panel.createActionLabel("Learn more", () -> BrowserUtil.browse("https://developer.android.com/studio/write/mlmodelbinding"));
      panel.createActionLabel("Hide notification", () -> {
        fileEditor.putUserData(HIDDEN_KEY, "true");
        EditorNotifications.getInstance(project).updateNotifications(file);
      });
      return panel;
    }

    return null;
  }
}
