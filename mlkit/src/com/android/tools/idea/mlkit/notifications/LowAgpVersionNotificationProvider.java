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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileEditor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that Android Gradle Plugin version is a bit low so feature may be not fully supported.
 */
public class LowAgpVersionNotificationProvider implements EditorNotificationProvider {
  private static final Key<String> HIDDEN_KEY = Key.create("ml.low.ago.notification.panel.hidden");
  private static final String MIN_AGP_VERSION = "4.1.0-alpha10";

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    return fileEditor -> createNotificationPanel(file, fileEditor, project);
  }

  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    if (fileEditor.getUserData(HIDDEN_KEY) != null || !(fileEditor instanceof TfliteModelFileEditor)) {
      return null;
    }

    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromModel(project);
    GradleVersion agpVersion = androidPluginInfo != null ? androidPluginInfo.getPluginVersion() : null;
    if (agpVersion == null || agpVersion.compareTo(MIN_AGP_VERSION) >= 0) {
      return null;
    }

    EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
    panel.setText("ML Model Binding is not fully supported in the current Android Gradle Plugin, so please update to the latest version.");
    panel.createActionLabel("Hide notification", () -> {
      fileEditor.putUserData(HIDDEN_KEY, "true");
      EditorNotifications.getInstance(project).updateNotifications(file);
    });
    return panel;
  }
}
