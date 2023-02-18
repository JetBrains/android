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
import com.android.tools.idea.mlkit.MlUtils;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileEditor;
import com.android.tools.idea.npw.template.components.ModuleTemplateComboProvider;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.io.IOException;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that a model file is not in the correct ml folder in order to use ML Model Binding feature.
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
    if (module == null || MlUtils.isModelFileInMlModelsFolder(module, file)) {
      return null;
    }

    List<NamedModuleTemplate> moduleTemplateList = ContainerUtil.filter(
      ProjectSystemUtil.getModuleSystem(module).getModuleTemplates(file),
      template -> !template.getPaths().getMlModelsDirectories().isEmpty());
    if (moduleTemplateList.isEmpty()) {
      return null;
    }

    EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
    panel.setText("This TensorFlow Lite model is not in a configured ml-model directory, so ML Model Binding is disabled. To use " +
                  "ML Model Binding consider moving the file.");
    panel.createActionLabel("Move File", () -> {
      MoveModelFileDialog moveModelFileDialog = new MoveModelFileDialog(moduleTemplateList);
      if (moveModelFileDialog.showAndGet()) {
        WriteAction.run(() -> {
          VirtualFile mlVirtualDir = null;
          try {
            mlVirtualDir = VfsUtil.createDirectoryIfMissing(moveModelFileDialog.getSelectedMlDirectoryPath());
            if (mlVirtualDir != null) {
              PsiDirectory mlPsiDir = PsiDirectoryFactory.getInstance(project).createDirectory(mlVirtualDir);
              if (!CopyFilesOrDirectoriesHandler.checkFileExist(
                mlPsiDir, null, PsiManager.getInstance(project).findFile(file), file.getName(), "Move")) {
                file.move(this, mlVirtualDir);
                EditorNotifications.getInstance(project).updateNotifications(file);
                LoggingUtils.logEvent(EventType.MODEL_IMPORT_FROM_MOVE_FILE_BUTTON, file);
              }
            }
          }
          catch (IOException e) {
            Logger.getInstance(InNonMlFolderNotificationProvider.class)
              .error(String.format("Error moving %s to %s.", file, mlVirtualDir), e);
          }
        });
      }
    });
    panel.createActionLabel("Hide notification", () -> {
      fileEditor.putUserData(HIDDEN_KEY, "true");
      EditorNotifications.getInstance(project).updateNotifications(file);
    });
    return panel;
  }

  private static class MoveModelFileDialog extends DialogWrapper {
    private final List<NamedModuleTemplate> myNamedModuleTemplateList;
    private ComboBox<NamedModuleTemplate> myComboBox;

    private MoveModelFileDialog(@NotNull List<NamedModuleTemplate> namedModuleTemplateList) {
      super(true);
      myNamedModuleTemplateList = namedModuleTemplateList;
      init();
      setTitle("Move TensorFlow Lite Model File");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel dialogPanel = new JPanel();
      dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.X_AXIS));
      dialogPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
      dialogPanel.setBorder(JBUI.Borders.empty(10));
      dialogPanel.add(new JBLabel("Move the model file to the ml directory in "));

      myComboBox = new ModuleTemplateComboProvider(myNamedModuleTemplateList).createComponent();
      dialogPanel.add(myComboBox);

      return dialogPanel;
    }

    @NotNull
    private String getSelectedMlDirectoryPath() {
      return ((NamedModuleTemplate)myComboBox.getSelectedItem()).getPaths().getMlModelsDirectories().get(0).getPath();
    }
  }
}
